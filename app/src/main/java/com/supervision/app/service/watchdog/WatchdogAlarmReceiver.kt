package com.supervision.app.service.watchdog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import com.supervision.app.service.monitoring.UsageMonitoringService
import com.supervision.app.service.vpn.SupervisionVpnService
import timber.log.Timber

/**
 * WatchdogAlarmReceiver — High-frequency, battery-conscious system watchdog receiver.
 * Fires periodically via AlarmManager.setAndAllowWhileIdle to guarantee that Supervision's
 * UsageMonitoringService and SupervisionVpnService remain active even under aggressive OEM task killing.
 */
class WatchdogAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WATCHDOG_PING = "com.supervision.app.WATCHDOG_PING"
        const val WATCHDOG_INTERVAL_MS = 60_000L // 60 seconds interval

        fun scheduleNextWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, WatchdogAlarmReceiver::class.java).apply {
                    action = ACTION_WATCHDOG_PING
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    999,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
                Timber.d("WatchdogAlarmReceiver scheduled next ping in 60s")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule next watchdog ping")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Timber.d("WatchdogAlarmReceiver received ping ($intent?.action)")

        // 1. Reschedule next watchdog heartbeat immediately
        scheduleNextWatchdog(context)

        // 2. Acquire brief partial wakelock so background services start cleanly
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "supervision:watchdog_resurrect")
        wakeLock?.acquire(3000L)

        try {
            // 3. Ensure UsageMonitoringService is active
            val monitoringIntent = Intent(context, UsageMonitoringService::class.java).apply {
                action = UsageMonitoringService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(monitoringIntent)
            } else {
                context.startService(monitoringIntent)
            }

            // 4. Ensure SupervisionVpnService is active if VPN permission is granted
            if (VpnService.prepare(context) == null) {
                val vpnIntent = Intent(context, SupervisionVpnService::class.java).apply {
                    action = SupervisionVpnService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent)
                } else {
                    context.startService(vpnIntent)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reviving services in WatchdogAlarmReceiver")
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }
}

