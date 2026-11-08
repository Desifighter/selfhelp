package com.supervision.app.domain.model

data class ProtectionStatus(
    val isVpnActive: Boolean = false,
    val isMonitoringActive: Boolean = false,
    val isPlanActive: Boolean = false,
    val protectionMode: ProtectionMode = ProtectionMode.STANDARD,
    val vpnHealth: ServiceHealth = ServiceHealth.UNKNOWN,
    val monitoringHealth: ServiceHealth = ServiceHealth.UNKNOWN,
    val blockedDomainCount: Int = 0,
    val discoveredDomainCount: Int = 0,
    val restrictedAppCount: Int = 0,
    val activePlanName: String? = null,
    val tamperEvents: Int = 0
) {
    val overallStatus: OverallProtectionState
        get() = when {
            !isPlanActive -> OverallProtectionState.INACTIVE
            isVpnActive && isMonitoringActive -> OverallProtectionState.ACTIVE
            isVpnActive || isMonitoringActive -> OverallProtectionState.PARTIAL
            else -> OverallProtectionState.INACTIVE
        }
}

enum class OverallProtectionState {
    ACTIVE,
    PARTIAL,
    INACTIVE
}

enum class ServiceHealth {
    HEALTHY,
    DEGRADED,
    FAILED,
    UNKNOWN
}
