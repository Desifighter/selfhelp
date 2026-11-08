package com.supervision.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SupervisionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupTimber()
        createNotificationChannels()
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
