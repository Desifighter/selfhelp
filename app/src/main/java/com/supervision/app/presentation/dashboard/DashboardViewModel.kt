package com.supervision.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.BlockedRequestRepository
import com.supervision.app.data.repository.DomainClassificationRepository
import com.supervision.app.data.repository.ProtectionPlanRepository
import com.supervision.app.data.repository.UsageRepository
import com.supervision.app.domain.model.AppRestriction
import com.supervision.app.domain.model.OverallProtectionState
import com.supervision.app.domain.model.ProtectionPlan
import com.supervision.app.domain.model.ProtectionStatus
import com.supervision.app.domain.model.RestrictionType
import com.supervision.app.domain.model.ServiceHealth
import com.supervision.app.domain.model.UsageRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val protectionStatus: ProtectionStatus = ProtectionStatus(),
    val activePlan: ProtectionPlan? = null,
    val instagramRemainingMinutes: Int = 30,
    val totalBlockedCount: Int = 0,
    val autoDiscoveredCount: Int = 0,
    val restrictedAppsCount: Int = 0,
    val todayUsageList: List<UsageRecord> = emptyList(),
    val totalUsageMinutesToday: Int = 0,
    val recentBlockedRequests: List<String> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val protectionPlanRepository: ProtectionPlanRepository,
    private val domainClassificationRepository: DomainClassificationRepository,
    private val appRestrictionRepository: AppRestrictionRepository,
    private val usageRepository: UsageRepository,
    private val blockedRequestRepository: BlockedRequestRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        protectionPlanRepository.observeActivePlan(),
        domainClassificationRepository.observeBlockedCount(),
        domainClassificationRepository.observeAutoBlockedCount(),
        appRestrictionRepository.observeRestrictedCount(),
        usageRepository.observeTodayUsage(),
        blockedRequestRepository.observeUnresolvedTamperCount()
    ) { activePlan, blockedCount, autoCount, restrictedCount, todayUsage, tamperCount ->
        val instagramUsage = todayUsage.find { it.packageName == "com.instagram.android" }
        val instagramRemaining = (30 - (instagramUsage?.totalForegroundMinutes ?: 0)).coerceAtLeast(0)
        val totalMinutes = todayUsage.sumOf { it.totalForegroundMinutes }

        val status = ProtectionStatus(
            isVpnActive = activePlan != null && !activePlan.isExpired,
            isMonitoringActive = activePlan != null && !activePlan.isExpired,
            isPlanActive = activePlan != null && !activePlan.isExpired,
            protectionMode = activePlan?.protectionMode ?: com.supervision.app.domain.model.ProtectionMode.STANDARD,
            vpnHealth = if (activePlan != null) ServiceHealth.HEALTHY else ServiceHealth.UNKNOWN,
            monitoringHealth = if (activePlan != null) ServiceHealth.HEALTHY else ServiceHealth.UNKNOWN,
            blockedDomainCount = blockedCount,
            discoveredDomainCount = autoCount,
            restrictedAppCount = restrictedCount,
            activePlanName = activePlan?.name,
            tamperEvents = tamperCount
        )

        DashboardUiState(
            protectionStatus = status,
            activePlan = activePlan,
            instagramRemainingMinutes = instagramRemaining,
            totalBlockedCount = blockedCount,
            autoDiscoveredCount = autoCount,
            restrictedAppsCount = restrictedCount,
            todayUsageList = todayUsage,
            totalUsageMinutesToday = totalMinutes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun setupInstagramDefaultRestriction() {
        viewModelScope.launch {
            val instagram = AppRestriction(
                packageName = "com.instagram.android",
                appName = "Instagram",
                restrictionType = RestrictionType.TIME_LIMITED,
                dailyLimitMinutes = 30
            )
            appRestrictionRepository.setRestriction(instagram)
        }
    }
}
