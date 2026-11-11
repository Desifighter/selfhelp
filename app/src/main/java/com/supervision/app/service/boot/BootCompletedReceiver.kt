package com.supervision.app.service.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.supervision.app.data.repository.ProtectionPlanRepository
import com.supervision.app.service.monitoring.UsageMonitoringService
import com.supervision.app.service.vpn.SupervisionVpnService
import com.supervision.app.service.watchdog.WatchdogAlarmReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var protectionPlanRepository: ProtectionPlanRepository

    override fun onReceive(context: Context, intent: Intent?) {
        val incomingAction = intent?.action
        if (incomingAction != Intent.ACTION_BOOT_COMPLETED &&
            incomingAction != "android.intent.action.QUICKBOOT_POWERON" &&
            incomingAction != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Timber.i("Boot or package update completed ($incomingAction) — checking protection state")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activePlan = protectionPlanRepository.getActivePlan()
                if (activePlan != null && !activePlan.isExpired) {
                    Timber.i("Active protection plan found: ${activePlan.name}")

                    val vpnIntent = Intent(context, SupervisionVpnService::class.java).apply {
                        setAction(SupervisionVpnService.ACTION_START)
                    }
                    context.startForegroundService(vpnIntent)

                    val monitoringIntent = Intent(context, UsageMonitoringService::class.java).apply {
                        setAction(UsageMonitoringService.ACTION_START)
                    }
                    context.startForegroundService(monitoringIntent)

                    WatchdogAlarmReceiver.scheduleNextWatchdog(context)

                    Timber.i("Protection services restarted after boot")
                } else {
                    Timber.i("No active protection plan — services not started")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error restoring protection after boot")
            } finally {
                pendingResult.finish()
            }
        }
    }
}

