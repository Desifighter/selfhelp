package com.supervision.app.presentation.websites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supervision.app.data.repository.DomainClassificationRepository
import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.ClassificationSource
import com.supervision.app.domain.model.DomainClassification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebsiteRestrictionsUiState(
    val blockedDomains: List<DomainClassification> = emptyList(),
    val autoDiscoveredDomains: List<DomainClassification> = emptyList(),
    val totalBlockedCount: Int = 0,
    val autoBlockedCount: Int = 0
)

@HiltViewModel
class WebsiteRestrictionsViewModel @Inject constructor(
    private val repository: DomainClassificationRepository
) : ViewModel() {

    val uiState: StateFlow<WebsiteRestrictionsUiState> = combine(
        repository.observeBlockedDomains(),
        repository.observeAutoDiscoveredDomains(),
        repository.observeBlockedCount(),
        repository.observeAutoBlockedCount()
    ) { blocked, autoDiscovered, totalCount, autoCount ->
        WebsiteRestrictionsUiState(
            blockedDomains = blocked,
            autoDiscoveredDomains = autoDiscovered,
            totalBlockedCount = totalCount,
            autoBlockedCount = autoCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WebsiteRestrictionsUiState()
    )

    fun addCustomBlockedDomain(domain: String) {
        viewModelScope.launch {
            repository.blockDomain(
                domain = domain,
                category = ClassificationCategory.PORNOGRAPHY,
                source = ClassificationSource.USER_MANUAL
            )
        }
    }

    fun allowlistDomain(domain: String) {
        viewModelScope.launch {
            repository.allowDomain(domain)
        }
    }
}

