package com.supervision.app.service.watchdog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import com.supervision.app.service.monitoring.UsageMonitoringService
import com.supervision.app.service.vpn.SupervisionVpnService
import timber.log.Timber

/**
 * WatchdogAlarmReceiver — Battery-conscious system watchdog receiver.
 * Uses ELAPSED_REALTIME periodic alarms and screen-on triggers to ensure
 * UsageMonitoringService and SupervisionVpnService are kept alive without battery drain.
 */
class WatchdogAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WATCHDOG_PING = "com.supervision.app.WATCHDOG_PING"
        const val WATCHDOG_INTERVAL_MS = 15 * 60_000L // 15 minutes battery-friendly interval

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

                val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pendingIntent)
                }
                Timber.d("WatchdogAlarmReceiver scheduled next ping in 15m")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule next watchdog ping")
            }
        }

        fun checkAndEnsureServices(context: Context) {
            try {
                // 1. Ensure UsageMonitoringService is active
                val monitoringIntent = Intent(context, UsageMonitoringService::class.java).apply {
                    action = UsageMonitoringService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(monitoringIntent)
                } else {
                    context.startService(monitoringIntent)
                }

                // 2. Ensure SupervisionVpnService is active if VPN permission is granted
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
                Timber.e(e, "Error ensuring services in checkAndEnsureServices")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Timber.d("WatchdogAlarmReceiver received ping ($intent?.action)")

        // 1. Reschedule next watchdog heartbeat
        scheduleNextWatchdog(context)

        // 2. Ensure services are running
        checkAndEnsureServices(context)
    }
}
