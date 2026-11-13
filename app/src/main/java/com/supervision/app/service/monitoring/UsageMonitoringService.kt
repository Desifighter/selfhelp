package com.supervision.app.service.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.supervision.app.R
import com.supervision.app.SupervisionApp
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.UsageRepository
import com.supervision.app.domain.model.RestrictionType
import com.supervision.app.presentation.MainActivity
import com.supervision.app.service.watchdog.WatchdogAlarmReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class UsageMonitoringService : Service() {

    @Inject
    lateinit var usageRepository: UsageRepository

    @Inject
    lateinit var appRestrictionRepository: AppRestrictionRepository

    @Inject
    lateinit var appBlocker: AppBlocker

    @Inject
    lateinit var blockingOverlayManager: BlockingOverlayManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRunning = false
    private var isExplicitlyStopped = false
    private val warnedPackages = mutableSetOf<String>()

    // Performance caches to eliminate CPU spikes, GC pressure, and battery drain
    private var lastForegroundApp: String? = null
    private var lastDailyUsageSyncTime = 0L
    private var lastEventQueryTime = 0L
    private val activeResumedPackages = ConcurrentHashMap<String, Long>()
    private val appNameCache = ConcurrentHashMap<String, String>()
    private val dailyUsageMinutesCache = ConcurrentHashMap<String, Int>()

    companion object {
        const val ACTION_START = "com.supervision.app.MONITORING_START"
        const val ACTION_STOP = "com.supervision.app.MONITORING_STOP"
        private const val NOTIFICATION_ID = 2
        private const val CHECK_INTERVAL_MS = 1000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("UsageMonitoringService onTaskRemoved — ensuring persistent background execution")
        scheduleResurrectionAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isExplicitlyStopped = true
                stopMonitoring()
                return START_NOT_STICKY
            }
            else -> {
                isExplicitlyStopped = false
                startMonitoring()
                WatchdogAlarmReceiver.scheduleNextWatchdog(applicationContext)
                return START_STICKY
            }
        }
    }

    private fun startMonitoring() {
        if (isRunning) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        isRunning = true
        Timber.i("Usage monitoring started")

        serviceScope.launch {
            monitorLoop()
        }
    }

    private suspend fun monitorLoop() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            Timber.e("UsageStatsManager not available")
            return
        }

        try {
            appRestrictionRepository.initializeEssentialApps()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize default apps in monitoring loop")
        }

        // Initial sync of usage metrics
        syncDailyUsageStats(usageStatsManager, LocalDate.now(), System.currentTimeMillis())

        while (isRunning) {
            try {
                processUsageAndEnforce(usageStatsManager)
            } catch (e: Exception) {
                Timber.e(e, "Error in monitoring loop")
            }
            delay(CHECK_INTERVAL_MS)
        }
    }

    private suspend fun processUsageAndEnforce(usageStatsManager: UsageStatsManager) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        // 1. Periodically persist daily usage metrics to Room DB (every 30s, eliminating per-second SQLite writes)
        if (now - lastDailyUsageSyncTime >= 30_000L) {
            syncDailyUsageStats(usageStatsManager, today, now)
            lastDailyUsageSyncTime = now
        }

        // 2. Comprehensive active/visible packages detection (supporting Fullscreen, Freeform, Multi-Window & PIP)
        val visiblePackages = mutableSetOf<String>()

        // 2a. Query UsageEvents since last check (or last 60 seconds on startup)
        val queryStart = if (lastEventQueryTime > 0) (lastEventQueryTime - 1000L) else (now - 60_000L)
        val recentEvents = usageStatsManager.queryEvents(queryStart, now)
        val event = UsageEvents.Event()
        var maxEventTime = lastEventQueryTime

        while (recentEvents.hasNextEvent()) {
            recentEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (event.timeStamp > maxEventTime) {
                maxEventTime = event.timeStamp
            }
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    activeResumedPackages[pkg] = event.timeStamp
                    lastForegroundApp = pkg
                }
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    activeResumedPackages.remove(pkg)
                }
            }
        }
        if (maxEventTime > lastEventQueryTime) {
            lastEventQueryTime = maxEventTime
        }

        // Add all packages currently in resumed state that are not in dismiss cooldown
        for ((pkg, _) in activeResumedPackages) {
            if (pkg != packageName && !blockingOverlayManager.isDismissCooldownActive(pkg)) {
                visiblePackages.add(pkg)
            }
        }

        // 2b. Query UsageStats for recent activity (catches Freeform / Floating windows)
        try {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 30_000L, now)
            for (stat in stats) {
                val pkg = stat.packageName ?: continue
                if (pkg != packageName && !blockingOverlayManager.isDismissCooldownActive(pkg)) {
                    val isRecentlyVisible = (now - stat.lastTimeVisible) < 8_000L
                    val isRecentlyUsed = (now - stat.lastTimeUsed) < 8_000L
                    if (isRecentlyVisible || isRecentlyUsed) {
                        visiblePackages.add(pkg)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying usage stats for visible packages")
        }

        // Fallback: If no visible packages detected from events or stats, use last known foreground app if not in cooldown
        if (visiblePackages.isEmpty() && lastForegroundApp != null) {
            if (!blockingOverlayManager.isDismissCooldownActive(lastForegroundApp!!)) {
                visiblePackages.add(lastForegroundApp!!)
            }
        }

        // 3. Evaluate and enforce restrictions on all currently visible packages
        var hasActiveBlockedPackage = false

        for (pkg in visiblePackages) {
            if (pkg == packageName) continue

            val restriction = appRestrictionRepository.getRestriction(pkg) ?: continue
            when (restriction.restrictionType) {
                RestrictionType.COMPLETELY_BLOCKED -> {
                    if (!restriction.isEssential) {
                        Timber.i("Restricted package active/visible (Fullscreen or Freeform): $pkg")
                        hasActiveBlockedPackage = true
                        appBlocker.blockApp(
                            context = this@UsageMonitoringService,
                            packageName = pkg,
                            appName = restriction.appName,
                            reason = "Completely blocked under active plan"
                        )
                    }
                }
                RestrictionType.TIME_LIMITED -> {
                    val limit = restriction.dailyLimitMinutes ?: Int.MAX_VALUE
                    val usedMinutes = dailyUsageMinutesCache[pkg] ?: 0
                    val remaining = limit - usedMinutes

                    if (remaining <= 0) {
                        Timber.i("Time limit exceeded for active/visible package: $pkg ($usedMinutes/$limit min)")
                        hasActiveBlockedPackage = true
                        appBlocker.blockApp(
                            context = this@UsageMonitoringService,
                            packageName = pkg,
                            appName = restriction.appName,
                            reason = "Daily limit of $limit min exceeded"
                        )
                    } else if (remaining <= 5 && !warnedPackages.contains(pkg)) {
                        warnedPackages.add(pkg)
                        showTimeWarningNotification(restriction.appName, remaining)
                    }
                }
                RestrictionType.ALWAYS_ALLOWED -> {
                    // Essential or user-whitelisted (e.g. Teams, Outlook, IRCTC)
                }
            }
        }

        // 4. Auto-dismiss overlay if no blocked package is visible on screen
        if (!hasActiveBlockedPackage && blockingOverlayManager.isShowing()) {
            blockingOverlayManager.dismissOverlay()
        }

        // 5. Proactively terminate background processes of completely blocked apps
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val blockedApps = appRestrictionRepository.getBlockedApps()
            for (blocked in blockedApps) {
                if (!blocked.isEssential && !visiblePackages.contains(blocked.packageName)) {
                    am?.killBackgroundProcesses(blocked.packageName)
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun syncDailyUsageStats(usageStatsManager: UsageStatsManager, today: LocalDate, now: Long) {
        try {
            val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val events = usageStatsManager.queryEvents(startOfDay, now)
            val event = UsageEvents.Event()

            val foregroundDurations = mutableMapOf<String, Long>()
            val lastResumeTimes = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        lastResumeTimes[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val resumeTime = lastResumeTimes.remove(pkg)
                        if (resumeTime != null && event.timeStamp > resumeTime) {
                            val duration = event.timeStamp - resumeTime
                            foregroundDurations[pkg] = (foregroundDurations[pkg] ?: 0L) + duration
                        }
                    }
                }
            }

            // Include duration for app actively running in foreground right now
            for ((pkg, resumeTime) in lastResumeTimes) {
                if (now > resumeTime) {
                    val duration = now - resumeTime
                    foregroundDurations[pkg] = (foregroundDurations[pkg] ?: 0L) + duration
                }
            }

            // Update in-memory caches and persist to repository
            val pm = packageManager
            for ((pkg, durationMs) in foregroundDurations) {
                val minutes = (durationMs / 60_000L).toInt()
                dailyUsageMinutesCache[pkg] = minutes

                val appName = appNameCache.getOrPut(pkg) {
                    try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                }
                usageRepository.recordUsage(
                    packageName = pkg,
                    appName = appName,
                    minutes = minutes,
                    date = today
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing daily usage stats")
        }
    }

    private fun scheduleResurrectionAlarm() {
        WatchdogAlarmReceiver.scheduleNextWatchdog(applicationContext)
    }

    private fun stopMonitoring() {
        isRunning = false
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.i("Usage monitoring stopped")
    }

    private fun createNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SupervisionApp.CHANNEL_MONITORING)
            .setContentTitle("Supervision Monitoring")
            .setContentText("Monitoring app usage")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
        }

    private fun showTimeWarningNotification(appName: String, remainingMinutes: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = NotificationCompat.Builder(this, SupervisionApp.CHANNEL_ALERTS)
            .setContentTitle("Daily Limit Warning")
            .setContentText("$appName: $remainingMinutes minutes remaining today")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(appName.hashCode(), notification)
    }

    override fun onDestroy() {
        if (!isExplicitlyStopped) {
            Timber.w("UsageMonitoringService destroyed unexpectedly! Scheduling resurrection...")
            scheduleResurrectionAlarm()
        }
        blockingOverlayManager.dismissOverlay()
        stopMonitoring()
        super.onDestroy()
    }
}
