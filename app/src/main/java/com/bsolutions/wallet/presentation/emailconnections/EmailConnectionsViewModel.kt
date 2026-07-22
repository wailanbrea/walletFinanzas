package com.bsolutions.wallet.presentation.emailconnections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.data.repository.EmailCandidate
import com.bsolutions.wallet.data.repository.EmailConnection
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import com.bsolutions.wallet.data.repository.EmailProvider
import com.bsolutions.wallet.data.repository.EmailSessionExpiredException
import com.bsolutions.wallet.data.repository.EmailSyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EmailConnectionsPhase { LOADING, CONTENT, EMPTY, ERROR }

data class EmailConnectionsUiState(
    val phase: EmailConnectionsPhase = EmailConnectionsPhase.LOADING,
    val connections: List<EmailConnection> = emptyList(),
    val candidates: List<EmailCandidate> = emptyList(),
    val categories: List<Category> = emptyList(),
    val actionProvider: EmailProvider? = null,
    val reviewCandidateId: String? = null,
    val syncResult: EmailSyncResult? = null,
    val authorizationUrl: String? = null,
    val message: String? = null
) {
    val candidatesByProvider: Map<EmailProvider, List<EmailCandidate>>
        get() = candidates.groupBy { it.provider }
}

@HiltViewModel
class EmailConnectionsViewModel @Inject constructor(
    private val repository: EmailConnectionsRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmailConnectionsUiState())
    val uiState: StateFlow<EmailConnectionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getCategories().collect { categories ->
                _uiState.value = _uiState.value.copy(categories = categories.sortedBy { it.name })
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = EmailConnectionsPhase.LOADING,
                actionProvider = null,
                message = null
            )
            loadConnections()
        }
    }

    fun onAuthorizationReturn() = refresh()

    fun connect(provider: EmailProvider) {
        if (_uiState.value.actionProvider != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionProvider = provider, message = null)
            try {
                val url = repository.getAuthorizationUrl(provider)
                _uiState.value = _uiState.value.copy(
                    actionProvider = null,
                    authorizationUrl = url
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    actionProvider = null,
                    message = if (exception is EmailSessionExpiredException) {
                        "Tu sesión venció. Inicia sesión nuevamente."
                    } else {
                        "No se pudo iniciar la conexión. Inténtalo de nuevo."
                    }
                )
            }
        }
    }

    fun consumeAuthorizationUrl() {
        _uiState.value = _uiState.value.copy(authorizationUrl = null)
    }

    fun sync(provider: EmailProvider) {
        if (_uiState.value.actionProvider != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionProvider = provider, message = null, syncResult = null)
            try {
                val result = repository.sync(provider)
                loadConnections(result)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    actionProvider = null,
                    message = if (exception is EmailSessionExpiredException) {
                        "Tu sesión venció. Inicia sesión nuevamente."
                    } else {
                        "No se pudieron sincronizar los correos. Inténtalo de nuevo."
                    }
                )
            }
        }
    }

    fun disconnect(provider: EmailProvider) {
        if (_uiState.value.actionProvider != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionProvider = provider, message = null)
            try {
                repository.disconnect(provider)
                loadConnections()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    actionProvider = null,
                    message = if (exception is EmailSessionExpiredException) {
                        "Tu sesión venció. Inicia sesión nuevamente."
                    } else {
                        "No se pudo desconectar la cuenta. Inténtalo de nuevo."
                    }
                )
            }
        }
    }

    fun classify(candidateId: String, category: String) {
        if (category.isBlank()) return
        review(candidateId, "categorize", category.trim())
    }

    fun dismiss(candidateId: String) {
        review(candidateId, "dismiss", null)
    }

    private fun review(candidateId: String, action: String, category: String?) {
        if (_uiState.value.reviewCandidateId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(reviewCandidateId = candidateId, message = null)
            try {
                repository.reviewCandidate(candidateId, action, category)
                _uiState.value = _uiState.value.copy(
                    candidates = _uiState.value.candidates.filterNot { it.id == candidateId },
                    reviewCandidateId = null,
                    message = if (action == "dismiss") {
                        "El correo fue descartado y la corrección se usará en el futuro."
                    } else {
                        "Movimiento clasificado. La categoría se usará en futuros correos similares."
                    }
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    reviewCandidateId = null,
                    message = if (exception is EmailSessionExpiredException) {
                        "Tu sesión venció. Inicia sesión nuevamente."
                    } else {
                        "No se pudo guardar la clasificación. Inténtalo de nuevo."
                    }
                )
            }
        }
    }

    private suspend fun loadConnections(syncResult: EmailSyncResult? = _uiState.value.syncResult) {
        try {
            val connections = repository.getConnections()
            val candidates = repository.getCandidates()
            _uiState.value = _uiState.value.copy(
                phase = if (connections.isEmpty()) EmailConnectionsPhase.EMPTY else EmailConnectionsPhase.CONTENT,
                connections = connections,
                candidates = candidates,
                actionProvider = null,
                syncResult = syncResult,
                message = null
            )
        } catch (exception: Exception) {
            _uiState.value = _uiState.value.copy(
                phase = EmailConnectionsPhase.ERROR,
                connections = emptyList(),
                candidates = emptyList(),
                actionProvider = null,
                message = if (exception is EmailSessionExpiredException) {
                    "Tu sesión venció. Inicia sesión nuevamente."
                } else {
                    "No se pudieron cargar las conexiones de correo."
                }
            )
        }
    }
}
