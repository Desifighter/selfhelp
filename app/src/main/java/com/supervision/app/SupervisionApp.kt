package com.supervision.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.supervision.app.service.watchdog.ServiceWatchdogWorker
import com.supervision.app.service.watchdog.WatchdogAlarmReceiver
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class SupervisionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupTimber()
        createNotificationChannels()
        setupWatchdog()
        registerScreenStateReceiver()
    }

    private fun setupWatchdog() {
        try {
            // 1. Periodic AlarmManager watchdog
            WatchdogAlarmReceiver.scheduleNextWatchdog(this)

            // 2. Fallback WorkManager periodic worker
            val watchdogRequest = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "SupervisionServiceWatchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                watchdogRequest
            )
            Timber.i("Scheduled SupervisionServiceWatchdog periodic worker & alarm watchdog")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule SupervisionServiceWatchdog")
        }
    }

    private fun registerScreenStateReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (context != null) {
                        WatchdogAlarmReceiver.checkAndEnsureServices(context)
                    }
                }
            }, filter)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register screen state receiver")
        }
    }

    private fun setupTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val vpnChannel = NotificationChannel(
            CHANNEL_VPN,
            "VPN Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when website filtering is active"
            setShowBadge(false)
        }

        val monitoringChannel = NotificationChannel(
            CHANNEL_MONITORING,
            "Usage Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when app usage monitoring is active"
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Protection Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important alerts about protection status and time limits"
        }

        manager.createNotificationChannels(listOf(vpnChannel, monitoringChannel, alertsChannel))
    }

    companion object {
        const val CHANNEL_VPN = "supervision_vpn"
        const val CHANNEL_MONITORING = "supervision_monitoring"
        const val CHANNEL_ALERTS = "supervision_alerts"
    }
}
