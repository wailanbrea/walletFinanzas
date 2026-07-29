package com.bsolutions.wallet.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.data.repository.SyncOutcome
import com.bsolutions.wallet.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val isSyncing: Boolean = false,
    val lastResult: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository
) : ViewModel() {

    val pendingCount: StateFlow<Int> = syncRepository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    fun syncNow() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.value = SyncUiState(isSyncing = true)
            _uiState.value = when (val outcome = syncRepository.sync()) {
                is SyncOutcome.Success -> {
                    val base = "Sincronizado: ${outcome.pushed} subidos, ${outcome.pulled} recibidos."
                    if (outcome.discarded > 0) {
                        // El servidor los rechazó una y otra vez: decirlo, porque son
                        // cambios del usuario que se quedaron solo en este teléfono.
                        SyncUiState(
                            lastResult = "$base ${outcome.discarded} no se pudieron subir y se " +
                                "descartaron; revisa esos cambios.",
                            isError = true
                        )
                    } else {
                        SyncUiState(lastResult = base)
                    }
                }
                is SyncOutcome.NoSession ->
                    SyncUiState(lastResult = "Inicia sesión para sincronizar con la nube.", isError = true)
                is SyncOutcome.Error ->
                    SyncUiState(lastResult = outcome.message, isError = true)
            }
        }
    }
}
