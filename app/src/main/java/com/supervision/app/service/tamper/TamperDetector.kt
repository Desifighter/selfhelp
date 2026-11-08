package com.supervision.app.service.tamper

import android.app.ActivityManager
import android.content.Context
import android.net.VpnService
import android.os.PowerManager
import com.supervision.app.data.repository.BlockedRequestRepository
import com.supervision.app.service.monitoring.UsageMonitoringService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TamperDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedRequestRepository: BlockedRequestRepository
) {
    private val _tamperState = MutableStateFlow(TamperState())
    val tamperState: StateFlow<TamperState> = _tamperState

    data class TamperState(
        val vpnDisconnected: Boolean = false,
        val monitoringDisabled: Boolean = false,
        val batteryOptimized: Boolean = false,
        val permissionsRevoked: Boolean = false,
        val systemTimeManipulated: Boolean = false
    ) {
        val isTampered: Boolean
            get() = vpnDisconnected || monitoringDisabled || permissionsRevoked
    }

    suspend fun performCheck() {
        val vpnOk = isVpnActive()
        val monitoringOk = isServiceRunning(UsageMonitoringService::class.java.name)
        val batteryOk = !isBatteryOptimized()

        val state = TamperState(
            vpnDisconnected = !vpnOk,
            monitoringDisabled = !monitoringOk,
            batteryOptimized = !batteryOk
        )

        _tamperState.value = state

        if (state.isTampered) {
            Timber.w("Tamper detected: vpn=$vpnOk, monitoring=$monitoringOk, battery=$batteryOk")
            blockedRequestRepository.recordTamperEvent(
                type = "TAMPER_DETECTED",
                description = buildTamperDescription(state),
                severity = if (!vpnOk) "HIGH" else "MEDIUM"
            )
        }
    }

    private fun buildTamperDescription(state: TamperState): String {
        val issues = mutableListOf<String>()
        if (state.vpnDisconnected) issues.add("VPN disconnected")
        if (state.monitoringDisabled) issues.add("Monitoring disabled")
        if (state.batteryOptimized) issues.add("Battery optimization enabled")
        if (state.permissionsRevoked) issues.add("Permissions revoked")
        return issues.joinToString(", ")
    }

    private fun isVpnActive(): Boolean {
        return try {
            VpnService.prepare(context) == null
        } catch (_: Exception) {
            false
        }
    }

    private fun isServiceRunning(className: String): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return activityManager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == className }
    }

    private fun isBatteryOptimized(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
