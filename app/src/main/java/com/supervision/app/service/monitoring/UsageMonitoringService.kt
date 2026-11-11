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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRunning = false
    private var isExplicitlyStopped = false
    private val warnedPackages = mutableSetOf<String>()

    // Performance caches to eliminate CPU spikes, GC pressure, and battery drain
    private var lastForegroundApp: String? = null
    private var lastDailyUsageSyncTime = 0L
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

        // 2. Real-time active foreground app detection (queries only recent 15s window for instant, O(1) response)
        val recentEvents = usageStatsManager.queryEvents(now - 15_000L, now)
        val event = UsageEvents.Event()
        var latestActivePkg: String? = null
        var latestResumeTime = 0L

        while (recentEvents.hasNextEvent()) {
            recentEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (event.timeStamp >= latestResumeTime) {
                        latestResumeTime = event.timeStamp
                        latestActivePkg = pkg
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (pkg == latestActivePkg && event.timeStamp >= latestResumeTime) {
                        latestActivePkg = null
                    }
                }
            }
        }

        if (latestActivePkg != null) {
            lastForegroundApp = latestActivePkg
        }

        // Fallback: If no recent event in 15s window, query UsageStats for the most recently active app
        val activeApp = lastForegroundApp ?: run {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now)
            stats.filter { it.packageName != packageName }
                .maxByOrNull { it.lastTimeUsed }
                ?.packageName
        }

        // 3. Enforce restriction on currently foreground application
        if (activeApp != null && activeApp != packageName) {
            val restriction = appRestrictionRepository.getRestriction(activeApp)
            if (restriction != null) {
                when (restriction.restrictionType) {
                    RestrictionType.COMPLETELY_BLOCKED -> {
                        if (!restriction.isEssential) {
                            Timber.i("Blocked app detected: $activeApp")
                            appBlocker.blockApp(this@UsageMonitoringService, activeApp)
                        }
                    }
                    RestrictionType.TIME_LIMITED -> {
                        val limit = restriction.dailyLimitMinutes ?: Int.MAX_VALUE
                        val usedMinutes = dailyUsageMinutesCache[activeApp] ?: 0
                        val remaining = limit - usedMinutes

                        if (remaining <= 0) {
                            Timber.i("Time limit reached for: $activeApp ($usedMinutes/$limit min)")
                            appBlocker.blockApp(this@UsageMonitoringService, activeApp)
                        } else if (remaining <= 5 && !warnedPackages.contains(activeApp)) {
                            warnedPackages.add(activeApp)
                            showTimeWarningNotification(restriction.appName, remaining)
                        }
                    }
                    RestrictionType.ALWAYS_ALLOWED -> {}
                }
            }
        }

        // 4. Scan running processes to catch Freeform floating windows and multi-window mode
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        am?.runningAppProcesses?.forEach { proc ->
            if (proc.pkgList != null && proc.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                for (pkg in proc.pkgList) {
                    if (pkg != packageName) {
                        val restriction = appRestrictionRepository.getRestriction(pkg)
                        if (restriction != null) {
                            when (restriction.restrictionType) {
                                RestrictionType.COMPLETELY_BLOCKED -> {
                                    if (!restriction.isEssential) {
                                        Timber.i("Visible process (Freeform window) detected for blocked app: $pkg")
                                        appBlocker.blockApp(this@UsageMonitoringService, pkg)
                                    }
                                }
                                RestrictionType.TIME_LIMITED -> {
                                    val limit = restriction.dailyLimitMinutes ?: Int.MAX_VALUE
                                    val usedMinutes = dailyUsageMinutesCache[pkg] ?: 0
                                    if (usedMinutes >= limit) {
                                        Timber.i("Visible process (Freeform window) limit reached for: $pkg ($usedMinutes/$limit min)")
                                        appBlocker.blockApp(this@UsageMonitoringService, pkg)
                                    }
                                }
                                RestrictionType.ALWAYS_ALLOWED -> {}
                            }
                        }
                    }
                }
            }
        }

        // 5. Proactively terminate background processes of completely blocked apps (e.g. Instagram, Telegram)
        am?.runningAppProcesses?.forEach { proc ->
            if (proc.pkgList != null && proc.importance > android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                for (pkg in proc.pkgList) {
                    if (pkg != packageName) {
                        val restriction = appRestrictionRepository.getRestriction(pkg)
                        if (restriction?.restrictionType == RestrictionType.COMPLETELY_BLOCKED && !restriction.isEssential) {
                            try {
                                am.killBackgroundProcesses(pkg)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
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
        try {
            val restartIntent = Intent(applicationContext, UsageMonitoringService::class.java).apply {
                action = ACTION_START
            }
            val pendingIntent = PendingIntent.getForegroundService(
                applicationContext,
                101,
                restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000L,
                    pendingIntent
                )
            } else {
                alarmManager?.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000L,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule service resurrection alarm")
        }
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
        stopMonitoring()
        super.onDestroy()
    }
}
