package com.supervision.app.presentation.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.domain.model.AppRestriction
import com.supervision.app.domain.model.RestrictionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppRestrictionsViewModel @Inject constructor(
    private val repository: AppRestrictionRepository
) : ViewModel() {

    val appRestrictions: StateFlow<List<AppRestriction>> = repository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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
            repository.setRestriction(app.copy(restrictionType = newType))
        }
    }

    fun updateDailyLimit(app: AppRestriction, minutes: Int) {
        viewModelScope.launch {
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

