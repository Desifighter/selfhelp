package com.supervision.app.presentation.onboarding

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.DomainClassificationRepository
import com.supervision.app.data.repository.ProtectionPlanRepository
import com.supervision.app.domain.model.ProtectionMode
import com.supervision.app.domain.model.ProtectionPlan
import com.supervision.app.domain.model.UnknownDomainPolicy
import com.supervision.app.service.classification.BlocklistProvider
import com.supervision.app.service.monitoring.UsageMonitoringService
import com.supervision.app.service.vpn.SupervisionVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class OnboardingUiState(
    val currentStep: Int = 0,
    val totalSteps: Int = 5,
    val isVpnPermissionGranted: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val selectedProtectionMode: ProtectionMode = ProtectionMode.STANDARD,
    val selectedUnknownPolicy: UnknownDomainPolicy = UnknownDomainPolicy.BALANCED,
    val selectedPlanDurationDays: Int = 30,
    val isSetupComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val protectionPlanRepository: ProtectionPlanRepository,
    private val appRestrictionRepository: AppRestrictionRepository,
    private val blocklistProvider: BlocklistProvider,
    private val domainClassificationRepository: DomainClassificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
    }

    fun nextStep() {
        val current = _uiState.value.currentStep
        if (current < _uiState.value.totalSteps - 1) {
            _uiState.value = _uiState.value.copy(currentStep = current + 1)
        }
    }

    fun previousStep() {
        val current = _uiState.value.currentStep
        if (current > 0) {
            _uiState.value = _uiState.value.copy(currentStep = current - 1)
        }
    }

    fun setProtectionMode(mode: ProtectionMode) {
        _uiState.value = _uiState.value.copy(selectedProtectionMode = mode)
    }

    fun setUnknownDomainPolicy(policy: UnknownDomainPolicy) {
        _uiState.value = _uiState.value.copy(selectedUnknownPolicy = policy)
    }

    fun setPlanDurationDays(days: Int) {
        _uiState.value = _uiState.value.copy(selectedPlanDurationDays = days)
    }

    fun checkPermissions() {
        val vpnGranted = VpnService.prepare(context) == null
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        val usageGranted = mode == android.app.AppOpsManager.MODE_ALLOWED
        val overlayGranted = android.provider.Settings.canDrawOverlays(context)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        _uiState.value = _uiState.value.copy(
            isVpnPermissionGranted = vpnGranted,
            isUsageStatsGranted = usageGranted,
            isOverlayPermissionGranted = overlayGranted,
            isBatteryOptimizationIgnored = batteryIgnored
        )
    }

    fun activateProtection(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                blocklistProvider.initialize()
                appRestrictionRepository.initializeEssentialApps()

                val now = Instant.now()
                val durationDays = _uiState.value.selectedPlanDurationDays.toLong()
                val endDate = now.plus(durationDays, ChronoUnit.DAYS)

                val plan = ProtectionPlan(
                    name = "${_uiState.value.selectedPlanDurationDays}-Day Protection Plan",
                    isActive = true,
                    protectionMode = _uiState.value.selectedProtectionMode,
                    unknownDomainPolicy = _uiState.value.selectedUnknownPolicy,
                    startDate = now,
                    endDate = endDate
                )

                val planId = protectionPlanRepository.createPlan(plan)
                protectionPlanRepository.activatePlan(planId)

                val vpnIntent = Intent(context, SupervisionVpnService::class.java).apply {
                    action = SupervisionVpnService.ACTION_START
                }
                context.startForegroundService(vpnIntent)

                val monitoringIntent = Intent(context, UsageMonitoringService::class.java).apply {
                    action = UsageMonitoringService.ACTION_START
                }
                context.startForegroundService(monitoringIntent)

                _uiState.value = _uiState.value.copy(isSetupComplete = true)
                Timber.i("Protection activated successfully with planId=$planId")
                onComplete()
            } catch (e: Exception) {
                Timber.e(e, "Failed to activate protection during onboarding")
            }
        }
    }
}

