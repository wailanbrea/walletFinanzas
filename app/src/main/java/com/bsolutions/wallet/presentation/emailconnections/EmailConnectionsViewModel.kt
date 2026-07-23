package com.bsolutions.wallet.presentation.emailconnections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
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
import java.time.Instant
import javax.inject.Inject

enum class EmailConnectionsPhase { LOADING, CONTENT, EMPTY, ERROR }

data class EmailConnectionsUiState(
    val phase: EmailConnectionsPhase = EmailConnectionsPhase.LOADING,
    val connections: List<EmailConnection> = emptyList(),
    val candidates: List<EmailCandidate> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val bookedCandidates: Map<String, Transaction> = emptyMap(),
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
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmailConnectionsUiState())
    val uiState: StateFlow<EmailConnectionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getCategories().collect { categories ->
                _uiState.value = _uiState.value.copy(categories = categories.sortedBy { it.name })
            }
        }
        viewModelScope.launch {
            accountRepository.getAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts.sortedBy { it.name })
            }
        }
        viewModelScope.launch {
            transactionRepository.getTransactions().collect { transactions ->
                _uiState.value = _uiState.value.copy(
                    bookedCandidates = transactions
                        .filter { it.id.startsWith(EMAIL_TRANSACTION_PREFIX) }
                        .associateBy { it.id.removePrefix(EMAIL_TRANSACTION_PREFIX) }
                )
            }
        }
        refresh()
    }

    fun refresh() {
        if (_uiState.value.reviewCandidateId != null || _uiState.value.actionProvider != null) return
        _uiState.value = _uiState.value.copy(
            phase = EmailConnectionsPhase.LOADING,
            actionProvider = null,
            message = null
        )
        viewModelScope.launch {
            loadConnections()
        }
    }

    fun onAuthorizationReturn() = refresh()

    fun connect(provider: EmailProvider) {
        if (_uiState.value.actionProvider != null || _uiState.value.reviewCandidateId != null) return
        _uiState.value = _uiState.value.copy(actionProvider = provider, message = null)
        viewModelScope.launch {
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
        if (_uiState.value.actionProvider != null || _uiState.value.reviewCandidateId != null) return
        _uiState.value = _uiState.value.copy(actionProvider = provider, message = null, syncResult = null)
        viewModelScope.launch {
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
        if (_uiState.value.actionProvider != null || _uiState.value.reviewCandidateId != null) return
        _uiState.value = _uiState.value.copy(actionProvider = provider, message = null)
        viewModelScope.launch {
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

    fun classify(candidateId: String, accountId: String, categoryId: String) {
        val bookedTransaction = _uiState.value.bookedCandidates[candidateId]
        if (
            accountId.isBlank() || (categoryId.isBlank() && bookedTransaction == null) ||
            _uiState.value.reviewCandidateId != null || _uiState.value.actionProvider != null
        ) return
        _uiState.value = _uiState.value.copy(reviewCandidateId = candidateId, message = null)
        viewModelScope.launch {
            var movementReady = false
            try {
                val candidate = checkNotNull(_uiState.value.candidates.firstOrNull { it.id == candidateId })
                val transactionId = emailTransactionId(candidate.id)
                val existing = transactionRepository.getTransaction(transactionId)
                val transaction: Transaction
                val categoryName: String
                val accountName: String
                if (existing != null) {
                    transaction = existing
                    categoryName = categoryRepository.getCategory(existing.categoryId)?.name
                        ?: candidate.categorySuggestion
                        ?: "Otros"
                    accountName = accountRepository.getAccount(existing.accountId)?.name ?: "la cuenta original"
                } else {
                    val account = checkNotNull(accountRepository.getAccount(accountId))
                    val category = checkNotNull(_uiState.value.categories.firstOrNull { it.id == categoryId })
                    val amount = checkNotNull(candidateAmountForAccount(candidate, account))
                    val type = when (candidate.direction) {
                        "income" -> "INCOME"
                        "expense" -> "EXPENSE"
                        else -> error("Dirección de movimiento inválida")
                    }
                    transaction = Transaction(
                        id = transactionId,
                        accountId = account.id,
                        amount = amount,
                        type = type,
                        categoryId = category.id,
                        date = checkNotNull(candidateOccurredAtMillis(candidate.occurredAt)),
                        note = candidate.merchant ?: candidate.subject ?: "Movimiento detectado por correo",
                        currency = account.currency
                    )
                    categoryName = category.name
                    accountName = account.name
                    transactionRepository.addTransactionWithBalance(transaction)
                    check(transactionRepository.getTransaction(transaction.id) == transaction) {
                        "No se pudo verificar el movimiento guardado"
                    }
                }
                movementReady = true
                repository.reviewCandidate(candidateId, "categorize", categoryName)
                _uiState.value = _uiState.value.copy(
                    candidates = _uiState.value.candidates.filterNot { it.id == candidateId },
                    reviewCandidateId = null,
                    message = "Movimiento agregado a $accountName."
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    reviewCandidateId = null,
                    message = if (movementReady && exception is EmailSessionExpiredException) {
                        "El movimiento ya fue agregado. Inicia sesión nuevamente para confirmar el correo."
                    } else if (movementReady) {
                        "El movimiento ya fue agregado, pero no se pudo confirmar el correo. Reintenta para finalizar."
                    } else if (exception is EmailSessionExpiredException) {
                        "Tu sesión venció. Inicia sesión nuevamente."
                    } else {
                        "No se pudo agregar el movimiento. Inténtalo de nuevo."
                    }
                )
            }
        }
    }

    fun dismiss(candidateId: String) {
        if (_uiState.value.reviewCandidateId != null || _uiState.value.actionProvider != null) return
        _uiState.value = _uiState.value.copy(reviewCandidateId = candidateId, message = null)
        viewModelScope.launch {
            if (transactionRepository.getTransaction(emailTransactionId(candidateId)) != null) {
                _uiState.value = _uiState.value.copy(
                    reviewCandidateId = null,
                    message = "Este movimiento ya fue agregado. Clasifícalo para completar la confirmación."
                )
                return@launch
            }
            review(candidateId, "dismiss", null, lockAlreadyHeld = true)
        }
    }

    private fun review(candidateId: String, action: String, category: String?, lockAlreadyHeld: Boolean = false) {
        if (!lockAlreadyHeld && (_uiState.value.reviewCandidateId != null || _uiState.value.actionProvider != null)) return
        if (!lockAlreadyHeld) _uiState.value = _uiState.value.copy(reviewCandidateId = candidateId, message = null)
        viewModelScope.launch {
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

internal fun candidateAmountForAccount(candidate: EmailCandidate, account: Account): Long? {
    val amount = when {
        candidate.currency == account.currency -> candidate.amount
        candidate.convertedCurrency == account.currency && candidate.conversionStatus != "unavailable" ->
            candidate.convertedAmount ?: return null
        else -> return null
    }
    if (amount == 0L || amount == Long.MIN_VALUE) return null
    return kotlin.math.abs(amount)
}

internal fun candidateOccurredAtMillis(value: String): Long? =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

private const val EMAIL_TRANSACTION_PREFIX = "email_"
private fun emailTransactionId(candidateId: String) = EMAIL_TRANSACTION_PREFIX + candidateId
