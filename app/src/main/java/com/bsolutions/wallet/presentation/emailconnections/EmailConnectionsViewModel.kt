package com.bsolutions.wallet.presentation.emailconnections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.PlannedPayment
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.repository.PlannedPaymentRepository
import com.bsolutions.wallet.domain.usecase.DEBT_OWED_TO_ME
import com.bsolutions.wallet.domain.usecase.DebtLedger
import com.bsolutions.wallet.domain.usecase.LOAN_CATEGORY_ID
import com.bsolutions.wallet.domain.repository.TransactionRepository
import com.bsolutions.wallet.data.repository.EmailCandidate
import com.bsolutions.wallet.data.repository.EmailConnection
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import com.bsolutions.wallet.data.repository.EmailProvider
import com.bsolutions.wallet.data.repository.EmailSessionExpiredException
import com.bsolutions.wallet.data.repository.EmailSyncResult
import com.bsolutions.wallet.data.repository.EmailSyncStillQueuedException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

enum class EmailConnectionsPhase { LOADING, CONTENT, EMPTY, ERROR }

data class EmailConnectionsUiState(
    val phase: EmailConnectionsPhase = EmailConnectionsPhase.LOADING,
    val connections: List<EmailConnection> = emptyList(),
    val candidates: List<EmailCandidate> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Deudas por cobrar abiertas: un cargo del correo se les puede sumar. */
    val openReceivables: List<Debt> = emptyList(),
    val bookedCandidates: Map<String, Transaction> = emptyMap(),
    val actionProvider: EmailProvider? = null,
    val reviewCandidateId: String? = null,
    val syncResult: EmailSyncResult? = null,
    val authorizationUrl: String? = null,
    val message: String? = null,
    /** Día elegido para mirar; null muestra todos, del más reciente al más antiguo. */
    val selectedDate: LocalDate? = null
) {
    val candidatesByProvider: Map<EmailProvider, List<EmailCandidate>>
        get() = candidates.groupBy { it.provider }

    /**
     * Los correos se leen por día, que es como se recuerda un gasto. Agrupados y en
     * orden descendente para que lo de hoy quede arriba.
     */
    val candidatesByDate: Map<LocalDate, List<EmailCandidate>>
        get() = visibleCandidates
            .groupBy { candidateLocalDate(it.occurredAt) ?: LocalDate.MIN }
            .toSortedMap(compareByDescending { it })
            .mapValues { (_, day) -> day.sortedByDescending { it.occurredAt } }

    /** Días con correos, para poder saltar entre ellos. */
    val availableDates: List<LocalDate>
        get() = candidates.mapNotNull { candidateLocalDate(it.occurredAt) }
            .distinct()
            .sortedDescending()

    /**
     * Otro candidato que parece el mismo cargo visto por el otro buzon.
     *
     * Se compara en la divisa base y con la misma ventana y tolerancia que usa el
     * servidor, para que la app no proponga emparejamientos que el backend rechazaria.
     * Devuelve el que conviene conservar: el que ya esta en pesos.
     */
    fun duplicateCandidateFor(candidate: EmailCandidate): EmailCandidate? {
        val mine = candidate.baseAmount() ?: return null
        val myDay = candidateLocalDate(candidate.occurredAt) ?: return null

        return visibleCandidates.firstOrNull { other ->
            if (other.id == candidate.id || other.provider == candidate.provider) return@firstOrNull false
            if (other.direction != candidate.direction) return@firstOrNull false
            val theirs = other.baseAmount() ?: return@firstOrNull false
            val theirDay = candidateLocalDate(other.occurredAt) ?: return@firstOrNull false
            if (kotlin.math.abs(myDay.toEpochDay() - theirDay.toEpochDay()) > DUPLICATE_WINDOW_DAYS) {
                return@firstOrNull false
            }
            val reference = maxOf(kotlin.math.abs(mine), kotlin.math.abs(theirs))
            if (reference == 0L) return@firstOrNull false
            val drift = kotlin.math.abs(kotlin.math.abs(mine) - kotlin.math.abs(theirs)).toDouble() / reference
            // Solo tiene sentido proponer marcar este si el otro es el que se conserva.
            drift <= DUPLICATE_TOLERANCE && other.currency == BASE_CURRENCY && candidate.currency != BASE_CURRENCY
        }
    }

    private val visibleCandidates: List<EmailCandidate>
        get() {
            // Un duplicado ya esta representado por el candidato que se conserva:
            // mostrarlo contaria el mismo gasto dos veces.
            val active = candidates.filterNot { it.status == "duplicate" }

            return selectedDate?.let { day ->
                active.filter { candidateLocalDate(it.occurredAt) == day }
            } ?: active
        }
}

private const val BASE_CURRENCY = "DOP"
/** Misma ventana y tolerancia que el servidor, para no proponer lo que rechazaria. */
private const val DUPLICATE_WINDOW_DAYS = 3L
private const val DUPLICATE_TOLERANCE = 0.03

/** Importe en la divisa base, o null si el cargo no se puede comparar. */
private fun EmailCandidate.baseAmount(): Long? = when {
    currency == BASE_CURRENCY -> amount
    convertedCurrency == BASE_CURRENCY -> convertedAmount
    else -> null
}

/** Día local del correo; null si la fecha viene ilegible. */
internal fun candidateLocalDate(occurredAt: String, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
    runCatching { Instant.parse(occurredAt).atZone(zoneId).toLocalDate() }.getOrNull()

@HiltViewModel
class EmailConnectionsViewModel @Inject constructor(
    private val repository: EmailConnectionsRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val debtRepository: DebtRepository,
    private val debtLedger: DebtLedger,
    private val plannedPaymentRepository: PlannedPaymentRepository
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
            debtRepository.getDebts().collect { debts ->
                _uiState.value = _uiState.value.copy(
                    openReceivables = debts.filter { it.direction == DEBT_OWED_TO_ME && !it.isClosed }
                )
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

    /** [date] null vuelve a mostrar todos los días. */
    fun selectDate(date: LocalDate?) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

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
                    message = when (exception) {
                        is EmailSessionExpiredException -> "Tu sesión venció. Inicia sesión nuevamente."
                        is EmailSyncStillQueuedException ->
                            "El servidor recibió la solicitud pero aún no la procesa. " +
                                "Vuelve a intentarlo en un momento."
                        else -> "No se pudieron sincronizar los correos. Inténtalo de nuevo."
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

    /**
     * Crea el movimiento del correo [candidateId].
     *
     * [overrideAmountMinor] reemplaza el importe calculado. Hace falta porque la
     * conversion a pesos es una estimacion con la tasa media publicada: el banco cobra
     * con su propio margen, asi que el monto real del estado de cuenta casi nunca
     * coincide al centavo y solo el usuario sabe cual fue.
     */
    fun classify(
        candidateId: String,
        accountId: String,
        categoryId: String,
        selectedDateMillis: Long? = null,
        overrideAmountMinor: Long? = null,
        /** Deuda a la que se le carga el monto, si el correo es por algo que prestaste. */
        debtId: String? = null,
        /**
         * "income" o "expense" cuando el usuario corrige lo detectado.
         *
         * Hace falta porque la deteccion se equivoca: un aviso de nomina que dice "pago"
         * se leia como gasto, y sin poder corregirlo aqui el sueldo entraba restando y
         * no habia forma de arreglarlo despues, porque editar no deja cambiar el tipo.
         */
        directionOverride: String? = null,
        /**
         * Frecuencia con la que se repite: crea ademas el recurrente en esa misma fecha.
         *
         * Un sueldo llega siempre, asi que dejarlo anotado desde el propio aviso evita
         * tener que ir a crearlo aparte repitiendo monto, cuenta y categoria.
         */
        recurringFrequency: String? = null
    ) {
        val bookedTransaction = _uiState.value.bookedCandidates[candidateId]
        // Con deuda no hace falta categoria: se usa la de prestamos.
        if (
            accountId.isBlank() ||
            (categoryId.isBlank() && bookedTransaction == null && debtId == null) ||
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
                    // Con deuda la categoria es la de prestamos: es lo que hace que la ida
                    // y la vuelta se neteen en vez de contarse como consumo. Se resuelve
                    // por id y no exigiendo el objeto, porque el movimiento debe poder
                    // crearse aunque esa categoria aun no este sembrada.
                    val debt = debtId?.let { debtRepository.getDebt(it) }
                    val chosenCategory = _uiState.value.categories.firstOrNull { it.id == categoryId }
                    val finalCategoryId = if (debt != null) LOAN_CATEGORY_ID else checkNotNull(chosenCategory).id
                    val amount = overrideAmountMinor?.takeIf { it > 0L }
                        ?: checkNotNull(candidateAmountForAccount(candidate, account))
                    val type = when (directionOverride ?: candidate.direction) {
                        "income" -> "INCOME"
                        "expense" -> "EXPENSE"
                        else -> error("Dirección de movimiento inválida")
                    }
                    transaction = Transaction(
                        id = transactionId,
                        accountId = account.id,
                        amount = amount,
                        type = type,
                        categoryId = finalCategoryId,
                        date = checkNotNull(
                            candidateTransactionDateMillis(
                                occurredAt = candidate.occurredAt,
                                selectedDateMillis = selectedDateMillis
                            )
                        ),
                        note = candidate.merchant ?: candidate.subject ?: "Movimiento detectado por correo",
                        currency = account.currency,
                        debtId = debt?.id
                    )
                    categoryName = _uiState.value.categories.firstOrNull { it.id == finalCategoryId }?.name
                        ?: chosenCategory?.name
                        ?: "Préstamos a terceros"
                    accountName = account.name
                    transactionRepository.addTransactionWithBalance(transaction)
                    check(transactionRepository.getTransaction(transaction.id) == transaction) {
                        "No se pudo verificar el movimiento guardado"
                    }
                    // Nace ya atado, asi que solo hay que mover la deuda.
                    debtLedger.onLinkedTransactionAdded(transaction)
                }
                movementReady = true
                // El recurrente se crea despues del movimiento y sin tumbar el flujo si
                // falla: lo importante ya quedo guardado y esto es una comodidad.
                if (recurringFrequency != null) {
                    runCatching {
                        plannedPaymentRepository.addPlannedPayment(
                            PlannedPayment(
                                id = UUID.randomUUID().toString(),
                                name = transaction.note.ifBlank { categoryName },
                                accountId = transaction.accountId,
                                categoryId = transaction.categoryId,
                                amount = transaction.amount,
                                type = transaction.type,
                                frequency = recurringFrequency,
                                nextDueDate = nextOccurrence(transaction.date, recurringFrequency),
                                isActive = true
                            )
                        )
                    }
                }
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

    /**
     * Quita un correo de la lista (la "x" de la tarjeta). Si ya tiene movimiento creado
     * se confirma como clasificado —no como descartado— para no enseñarle al clasificador
     * que ese correo no era un movimiento.
     */
    fun remove(candidateId: String) {
        if (_uiState.value.reviewCandidateId != null || _uiState.value.actionProvider != null) return
        val candidate = _uiState.value.candidates.firstOrNull { it.id == candidateId } ?: return
        _uiState.value = _uiState.value.copy(reviewCandidateId = candidateId, message = null)
        viewModelScope.launch {
            val outcome = confirmCandidate(candidate)
            _uiState.value = _uiState.value.copy(
                candidates = if (outcome) {
                    _uiState.value.candidates.filterNot { it.id == candidateId }
                } else {
                    _uiState.value.candidates
                },
                reviewCandidateId = null,
                message = if (outcome) null else "No se pudo quitar el correo. Inténtalo de nuevo."
            )
        }
    }

    /**
     * Marca [candidateId] como duplicado de [originalId].
     *
     * No usa 'dismiss' a proposito: descartar le ensena al clasificador que ese
     * remitente no manda movimientos, y si manda: solo que ese cargo ya llego por
     * otro buzon. Aprender de aqui envenenaria las detecciones futuras.
     */
    fun markAsDuplicate(candidateId: String, originalId: String) {
        if (_uiState.value.reviewCandidateId != null || _uiState.value.actionProvider != null) return
        _uiState.value = _uiState.value.copy(reviewCandidateId = candidateId, message = null)
        viewModelScope.launch {
            val ok = runCatching {
                repository.reviewCandidate(candidateId, "duplicate", null, originalId)
            }.isSuccess
            _uiState.value = _uiState.value.copy(
                candidates = if (ok) {
                    _uiState.value.candidates.filterNot { it.id == candidateId }
                } else {
                    _uiState.value.candidates
                },
                reviewCandidateId = null,
                message = if (ok) {
                    "Marcado como duplicado. Se conserva el otro movimiento."
                } else {
                    "No se pudo marcar como duplicado. Intentalo de nuevo."
                }
            )
        }
    }

    /** Limpia de una vez todos los correos pendientes que se ven en pantalla. */
    fun removeAll() {
        if (_uiState.value.reviewCandidateId != null || _uiState.value.actionProvider != null) return
        val candidates = _uiState.value.candidates
        if (candidates.isEmpty()) return
        _uiState.value = _uiState.value.copy(reviewCandidateId = CLEAR_ALL_LOCK, message = null)
        viewModelScope.launch {
            val failed = candidates.filterNot { confirmCandidate(it) }
            _uiState.value = _uiState.value.copy(
                candidates = failed,
                reviewCandidateId = null,
                message = when {
                    failed.isEmpty() -> "Se limpió la lista de correos."
                    else -> "No se pudieron quitar ${failed.size} correos. Inténtalo de nuevo."
                }
            )
        }
    }

    /**
     * Marca el correo como revisado en el backend. Devuelve false si la llamada falló,
     * para que la tarjeta siga en pantalla en vez de desaparecer sin haberse guardado.
     */
    private suspend fun confirmCandidate(candidate: EmailCandidate): Boolean {
        val booked = _uiState.value.bookedCandidates[candidate.id]
        val action = if (booked != null) "categorize" else "dismiss"
        val category = booked?.let {
            categoryRepository.getCategory(it.categoryId)?.name
                ?: candidate.categorySuggestion
                ?: "Otros"
        }
        return runCatching { repository.reviewCandidate(candidate.id, action, category) }.isSuccess
    }

    /**
     * Un correo cuyo movimiento ya se creó localmente pero cuya confirmación al backend
     * falló (sesión vencida, sin red) sigue "pendiente" en el servidor y reaparece en cada
     * refresco. Al cargar se reintenta la confirmación y se saca de la lista.
     */
    private suspend fun reconcileBookedCandidates(candidates: List<EmailCandidate>): List<EmailCandidate> {
        val booked = _uiState.value.bookedCandidates
        if (booked.isEmpty()) return candidates
        val alreadyBooked = candidates.filter { booked.containsKey(it.id) }
        if (alreadyBooked.isEmpty()) return candidates
        val confirmed = alreadyBooked.filter { confirmCandidate(it) }.mapTo(mutableSetOf()) { it.id }
        return candidates.filterNot { it.id in confirmed }
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
                    message = when (action) {
                        "dismiss" -> "El correo fue descartado y la corrección se usará en el futuro."
                        "duplicate" -> "Movimiento marcado como duplicado."
                        else -> "Movimiento clasificado. La categoría se usará en futuros correos similares."
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
            // Los que el servidor ya emparejo no se bajan a la lista; de los que quedan,
            // se reintenta confirmar los que tienen movimiento creado pero sin confirmar.
            val candidates = reconcileBookedCandidates(
                repository.getCandidates().filterNot { it.status == "duplicate" }
            )
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

/**
 * Cuenta que debe venir preseleccionada al aceptar un correo. Se prefiere la tarjeta
 * cuyos ultimos cuatro digitos coinciden con los del correo; si el correo no los trae
 * o ninguna cuenta coincide, se cae a la primera compatible en divisa.
 *
 * Una coincidencia de digitos con divisa incompatible se descarta: registrar el
 * movimiento en esa cuenta daria un importe erroneo.
 */
internal fun preselectedAccountId(candidate: EmailCandidate, accounts: List<Account>): String {
    val compatible = accounts.filter { candidateAmountForAccount(candidate, it) != null }
    val digits = candidate.cardLastFour?.takeIf { it.isNotBlank() }
    val byCard = digits?.let { last4 -> compatible.filter { it.cardLastFour == last4 } }

    // Con dos tarjetas del mismo final no se puede decidir: mejor no adivinar.
    return when {
        byCard != null && byCard.size == 1 -> byCard.first().id
        else -> compatible.firstOrNull()?.id.orEmpty()
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

/** Importe en unidades menores tal como se escribe en un campo de texto: 2065823 -> "20658.23". */
internal fun formatAmountForEditing(amountMinor: Long): String =
    BigDecimal.valueOf(kotlin.math.abs(amountMinor), 2).toPlainString()

/**
 * Lee el monto que escribio el usuario y lo devuelve en unidades menores.
 *
 * Devuelve null si no es un importe positivo utilizable, para que el boton siga
 * deshabilitado en vez de guardar un movimiento en cero o con basura.
 */
internal fun parseEditedAmountMinor(text: String): Long? {
    var cleaned = text.trim().replace(" ", "").replace(" ", "")
    if (cleaned.isEmpty()) return null
    // Con separador de miles y de decimales a la vez, la coma es de miles (20,658.23).
    cleaned = if (cleaned.contains(',') && cleaned.contains('.')) {
        cleaned.replace(",", "")
    } else {
        cleaned.replace(',', '.')
    }
    val value = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
    if (value <= BigDecimal.ZERO) return null
    return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

/**
 * Cuando toca la proxima vez, contando desde [fromMillis].
 *
 * Se adelanta una ocurrencia a proposito: la de [fromMillis] es la que se acaba de
 * registrar desde el correo, asi que anotarla otra vez la duplicaria.
 */
internal fun nextOccurrence(fromMillis: Long, frequency: String): Long {
    val date = Instant.ofEpochMilli(fromMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val next = when (frequency) {
        "WEEKLY" -> date.plusWeeks(1)
        "BIWEEKLY" -> date.plusWeeks(2)
        "YEARLY" -> date.plusYears(1)
        else -> date.plusMonths(1)
    }

    return next.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

internal fun candidateOccurredAtMillis(value: String): Long? =
    candidateTransactionDateMillis(value, selectedDateMillis = null)

internal fun candidateTransactionDateMillis(
    occurredAt: String,
    selectedDateMillis: Long?,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long? = runCatching {
    val originalInstant = Instant.parse(occurredAt)
    if (selectedDateMillis == null) {
        return@runCatching originalInstant.toEpochMilli()
    }

    val selectedDate = Instant.ofEpochMilli(selectedDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    val originalLocalTime = originalInstant.atZone(zoneId).toLocalTime()
    selectedDate.atTime(originalLocalTime)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}.getOrNull()

/** Id ficticio que bloquea la pantalla mientras se limpia la lista completa. */
private const val CLEAR_ALL_LOCK = "__clear_all__"
private const val EMAIL_TRANSACTION_PREFIX = "email_"
private fun emailTransactionId(candidateId: String) = EMAIL_TRANSACTION_PREFIX + candidateId
