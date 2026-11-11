package com.supervision.app.service.watchdog

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.supervision.app.service.monitoring.UsageMonitoringService
import com.supervision.app.service.vpn.SupervisionVpnService
import timber.log.Timber

/**
 * ServiceWatchdogWorker — Periodic health check worker that guarantees Supervision
 * background services remain active and resilient against aggressive OEM battery management.
 */
class ServiceWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("ServiceWatchdogWorker executing health check")
        try {
            val context = applicationContext

            // 1. Ensure VPN Service is running if permitted
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

            // 2. Ensure Usage Monitoring Service is running
            val monitoringIntent = Intent(context, UsageMonitoringService::class.java).apply {
                action = UsageMonitoringService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(monitoringIntent)
            } else {
                context.startService(monitoringIntent)
            }

            Timber.d("ServiceWatchdogWorker successfully verified background services")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "ServiceWatchdogWorker failed during health check")
            return Result.retry()
        }
    }
}

