package com.supervision.app.service.monitoring

import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.supervision.app.R
import com.supervision.app.SupervisionApp
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.UsageRepository
import com.supervision.app.domain.model.RestrictionType
import com.supervision.app.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
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
    private val warnedPackages = mutableSetOf<String>()

    companion object {
        const val ACTION_START = "com.supervision.app.MONITORING_START"
        const val ACTION_STOP = "com.supervision.app.MONITORING_STOP"
        private const val NOTIFICATION_ID = 2
        private const val CHECK_INTERVAL_MS = 1000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            else -> {
                startMonitoring()
                return START_STICKY
            }
        }
    }

    private fun startMonitoring() {
        if (isRunning) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()

        val foregroundDurations = mutableMapOf<String, Long>()
        val lastResumeTimes = mutableMapOf<String, Long>()
        var activeForegroundApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeTimes[pkg] = event.timeStamp
                    activeForegroundApp = pkg
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumeTime = lastResumeTimes.remove(pkg)
                    if (resumeTime != null && event.timeStamp > resumeTime) {
                        val duration = event.timeStamp - resumeTime
                        foregroundDurations[pkg] = (foregroundDurations[pkg] ?: 0L) + duration
                    }
                    if (activeForegroundApp == pkg) {
                        activeForegroundApp = null
                    }
                }
            }
        }

        // Include duration for app actively running in foreground right now
        for ((pkg, resumeTime) in lastResumeTimes) {
            if (now > resumeTime) {
                val duration = now - resumeTime
                foregroundDurations[pkg] = (foregroundDurations[pkg] ?: 0L) + duration
                activeForegroundApp = pkg
            }
        }

        // Persist exact same-day usage metrics
        val pm = packageManager
        for ((pkg, durationMs) in foregroundDurations) {
            val minutes = (durationMs / 60000L).toInt()
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
            usageRepository.recordUsage(
                packageName = pkg,
                appName = appName,
                minutes = minutes,
                date = today
            )
        }

        // Enforce restriction on currently foreground application
        if (activeForegroundApp != null && activeForegroundApp != packageName) {
            val restriction = appRestrictionRepository.getRestriction(activeForegroundApp)
            if (restriction != null) {
                val usedMinutes = ((foregroundDurations[activeForegroundApp] ?: 0L) / 60000L).toInt()
                when (restriction.restrictionType) {
                    RestrictionType.COMPLETELY_BLOCKED -> {
                        if (!restriction.isEssential) {
                            Timber.i("Blocked app detected: $activeForegroundApp")
                            appBlocker.blockApp(this@UsageMonitoringService, activeForegroundApp)
                        }
                    }
                    RestrictionType.TIME_LIMITED -> {
                        val limit = restriction.dailyLimitMinutes ?: Int.MAX_VALUE
                        val remaining = limit - usedMinutes

                        if (remaining <= 0) {
                            Timber.i("Time limit reached for: $activeForegroundApp ($usedMinutes/$limit min)")
                            appBlocker.blockApp(this@UsageMonitoringService, activeForegroundApp)
                        } else if (remaining <= 5 && !warnedPackages.contains(activeForegroundApp)) {
                            warnedPackages.add(activeForegroundApp)
                            showTimeWarningNotification(restriction.appName, remaining)
                        }
                    }
                    RestrictionType.ALWAYS_ALLOWED -> {}
                }
            }
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
        stopMonitoring()
        super.onDestroy()
    }
}

