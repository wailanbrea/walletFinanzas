package com.bsolutions.wallet.presentation.banknotifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.data.local.entity.NotificationSourceEntity
import com.bsolutions.wallet.data.local.entity.RawBankNoticeEntity
import com.bsolutions.wallet.data.repository.BankNotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BankNotificationsUiState(
    val sources: List<NotificationSourceEntity> = emptyList(),
    val notices: List<RawBankNoticeEntity> = emptyList()
)

@HiltViewModel
class BankNotificationsViewModel @Inject constructor(
    private val repository: BankNotificationRepository
) : ViewModel() {
    val uiState: StateFlow<BankNotificationsUiState> = combine(
        repository.sources,
        repository.notices()
    ) { sources, notices ->
        BankNotificationsUiState(sources = sources, notices = notices)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BankNotificationsUiState()
    )

    init {
        viewModelScope.launch {
            repository.purgeExpired()
            repository.discoverInstalledKnownApps()
            repository.reconcileCapturedNotices()
        }
    }

    fun setSourceEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { repository.setSourceEnabled(packageName, enabled) }
    }

    fun clearNotices() {
        viewModelScope.launch { repository.clearNotices() }
    }

    suspend fun buildAnonymizedExport(): String = repository.buildAnonymizedFixtureExport()
}
