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

    companion object {
        const val ACTION_START = "com.supervision.app.MONITORING_START"
        const val ACTION_STOP = "com.supervision.app.MONITORING_STOP"
        private const val NOTIFICATION_ID = 2
        private const val CHECK_INTERVAL_MS = 5000L
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

        startForeground(NOTIFICATION_ID, createNotification())
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

        while (isRunning) {
            try {
                checkCurrentForegroundApp(usageStatsManager)
                updateUsageStats(usageStatsManager)
            } catch (e: Exception) {
                Timber.e(e, "Error in monitoring loop")
            }
            delay(CHECK_INTERVAL_MS)
        }
    }

    private suspend fun checkCurrentForegroundApp(usageStatsManager: UsageStatsManager) {
        val currentTime = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(currentTime - 10000, currentTime)
        val event = UsageEvents.Event()
        var lastForegroundPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundPackage = event.packageName
            }
        }

        if (lastForegroundPackage != null && lastForegroundPackage != packageName) {
            val restriction = appRestrictionRepository.getRestriction(lastForegroundPackage)
            if (restriction != null) {
                when (restriction.restrictionType) {
                    RestrictionType.COMPLETELY_BLOCKED -> {
                        if (!restriction.isEssential) {
                            Timber.i("Blocked app detected: $lastForegroundPackage")
                            appBlocker.blockApp(this@UsageMonitoringService, lastForegroundPackage)
                        }
                    }
                    RestrictionType.TIME_LIMITED -> {
                        val usedMinutes = usageRepository.getUsageForToday(lastForegroundPackage)
                        val limit = restriction.dailyLimitMinutes ?: Int.MAX_VALUE
                        if (usedMinutes >= limit) {
                            Timber.i("Time limit reached for: $lastForegroundPackage ($usedMinutes/$limit min)")
                            appBlocker.blockApp(this@UsageMonitoringService, lastForegroundPackage)
                        }
                    }
                    RestrictionType.ALWAYS_ALLOWED -> {}
                }
            }
        }
    }

    private suspend fun updateUsageStats(usageStatsManager: UsageStatsManager) {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now
        )

        usageStats?.forEach { stats ->
            if (stats.totalTimeInForeground > 0) {
                val minutes = (stats.totalTimeInForeground / 60000).toInt()
                val pm = packageManager
                val appName = try {
                    pm.getApplicationLabel(
                        pm.getApplicationInfo(stats.packageName, 0)
                    ).toString()
                } catch (_: Exception) {
                    stats.packageName
                }
                usageRepository.recordUsage(
                    packageName = stats.packageName,
                    appName = appName,
                    minutes = minutes,
                    date = today
                )
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

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}
