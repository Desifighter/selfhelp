package com.supervision.app.presentation.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.ProtectionPlanRepository
import com.supervision.app.domain.model.AppRestriction
import com.supervision.app.domain.model.RestrictionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AppRestrictionsViewModel @Inject constructor(
    private val repository: AppRestrictionRepository,
    private val protectionPlanRepository: ProtectionPlanRepository
) : ViewModel() {

    val appRestrictions: StateFlow<List<AppRestriction>> = repository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isPlanActive: StateFlow<Boolean> = protectionPlanRepository.observeActivePlan()
        .map { plan -> plan != null && plan.isActive && !plan.isExpired }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    init {
        viewModelScope.launch {
            repository.initializeEssentialApps()
        }
    }

    fun updateRestrictionType(app: AppRestriction, newType: RestrictionType) {
        viewModelScope.launch {
            if (app.isEssential && newType != RestrictionType.ALWAYS_ALLOWED) {
                return@launch
            }

            // Anti-Tamper: While a protection plan is active, restricted apps cannot be unblocked
            val activePlan = protectionPlanRepository.getActivePlan()
            val isPlanActiveNow = activePlan != null && activePlan.isActive && !activePlan.isExpired
            val isCurrentlyRestricted = app.restrictionType != RestrictionType.ALWAYS_ALLOWED

            if (isPlanActiveNow && isCurrentlyRestricted && newType == RestrictionType.ALWAYS_ALLOWED) {
                Timber.w("Anti-tamper: Cannot unblock ${app.packageName} while protection plan is active")
                return@launch
            }

            repository.setRestriction(app.copy(restrictionType = newType))
        }
    }

    fun updateDailyLimit(app: AppRestriction, minutes: Int) {
        viewModelScope.launch {
            // Anti-tamper: Cannot increase daily limit during active commitment
            val activePlan = protectionPlanRepository.getActivePlan()
            val isPlanActiveNow = activePlan != null && activePlan.isActive && !activePlan.isExpired
            val currentLimit = app.dailyLimitMinutes ?: 30

            if (isPlanActiveNow && minutes > currentLimit) {
                Timber.w("Anti-tamper: Cannot increase limit for ${app.packageName} from $currentLimit to $minutes during active protection")
                return@launch
            }

            repository.setRestriction(app.copy(
                dailyLimitMinutes = minutes,
                restrictionType = RestrictionType.TIME_LIMITED
            ))
        }
    }

    fun addApp(packageName: String, appName: String, type: RestrictionType, limitMinutes: Int? = null) {
        viewModelScope.launch {
            repository.setRestriction(
                AppRestriction(
                    packageName = packageName,
                    appName = appName,
                    restrictionType = type,
                    dailyLimitMinutes = limitMinutes
                )
            )
        }
    }
}

