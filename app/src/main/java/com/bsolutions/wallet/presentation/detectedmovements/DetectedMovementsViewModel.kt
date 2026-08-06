package com.bsolutions.wallet.presentation.detectedmovements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import com.bsolutions.wallet.data.repository.BankNotificationRepository
import com.bsolutions.wallet.data.repository.DetectedMovementRepository
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import com.bsolutions.wallet.domain.email.SyncConnectedEmailAccounts
import com.bsolutions.wallet.data.repository.PossibleDuplicateResolution
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import com.bsolutions.wallet.core.common.ExpenseCategorizer
import com.bsolutions.wallet.core.common.CategoryRuleRepository
import com.bsolutions.wallet.core.common.CustomCategoryRule
import com.bsolutions.wallet.core.common.EmptyCategoryRules
import com.bsolutions.wallet.core.network.UsdDopRateService
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.abs

enum class DetectedMovementsPhase { LOADING, CONTENT, EMPTY }

enum class DetectedMovementDateFilter {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH
}

internal data class DetectedMovementDateBounds(
    val startInclusive: Long,
    val endExclusive: Long
)

data class DetectedMovementGroup(
    val root: DetectedMovementEntity,
    val evidence: List<DetectedMovementEntity>
) {
    val hasPendingEmailConfirmation: Boolean
        get() = evidence.any { it.needsSync && it.source.isEmailEvidence() }

    val isPossibleDuplicate: Boolean
        get() = root.possibleDuplicateOfId != null
}

internal data class DetectedMovementViewOptions(
    val filter: DetectedMovementDateFilter,
    val customDate: LocalDate?,
    val sortAscending: Boolean,
    val usdDopRateMicros: Long?
)

data class DetectedMovementsUiState(
    val phase: DetectedMovementsPhase = DetectedMovementsPhase.LOADING,
    val groups: List<DetectedMovementGroup> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedDateFilter: DetectedMovementDateFilter = DetectedMovementDateFilter.TODAY,
    val selectedDate: LocalDate? = null,
    val sortAscending: Boolean = false,
    val currentUsdDopRateMicros: Long? = null,
    val allActionableCount: Int = 0,
    val activeMovementId: String? = null,
    val isRefreshing: Boolean = false,
    val message: String? = null
)

data class DetectedMovementBookingRequest(
    val canonicalId: String,
    val accountId: String,
    val categoryId: String,
    val amountMinor: Long,
    val direction: String,
    val occurredAt: Long,
    val rememberCategory: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetectedMovementsViewModel @Inject constructor(
    private val detectedMovementRepository: DetectedMovementRepository,
    private val emailConnectionsRepository: EmailConnectionsRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val ownerScope: WalletOwnerScope,
    private val bankNotificationRepository: BankNotificationRepository? = null,
    private val categoryRules: CategoryRuleRepository = EmptyCategoryRules,
    private val usdDopRateService: UsdDopRateService,
    private val syncConnectedEmailAccounts: SyncConnectedEmailAccounts
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetectedMovementsUiState())
    val uiState: StateFlow<DetectedMovementsUiState> = _uiState.asStateFlow()
    private val selectedDateFilter = MutableStateFlow(DetectedMovementDateFilter.TODAY)
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val sortAscending = MutableStateFlow(false)
    private val dateRefreshTick = MutableStateFlow(0L)
    private val dateFilterState = combine(selectedDateFilter, selectedDate, sortAscending, dateRefreshTick) { filter, customDate, ascending, _ ->
        DetectedMovementViewOptions(filter, customDate, ascending, null)
    }
    private val currentUsdDopRateMicros = MutableStateFlow<Long?>(null)
    private val viewOptionsState = combine(dateFilterState, currentUsdDopRateMicros) { options, rate ->
        options.copy(usdDopRateMicros = rate)
    }
    internal var nowMillisProvider: () -> Long = System::currentTimeMillis

    init {
        viewModelScope.launch {
            runCatching {
                val ownerId = ownerScope.currentOwnerId()
                detectedMovementRepository.deduplicateExistingPendingMovements(ownerId)
                autoBookMultiEvidenceMovements()
            }
        }
        viewModelScope.launch {
            combine(
                ownerScope.ownerId.flatMapLatest(detectedMovementRepository::observeAll),
                accountRepository.getAccounts(),
                categoryRepository.getCategories(),
                categoryRules.rules,
                viewOptionsState,
            ) { movements, accounts, categories, rules, options ->
                val allGroups = movements.toActionableGroups().map { it.withCategorySuggestion(categories, rules) }
                val groups = allGroups
                    .filterByDate(options.filter, nowMillisProvider(), customDate = options.customDate)
                    .sortedByDate(options.sortAscending)
                _uiState.value.copy(
                    phase = if (groups.isEmpty()) DetectedMovementsPhase.EMPTY else DetectedMovementsPhase.CONTENT,
                    groups = groups,
                    accounts = accounts.sortedBy { it.name },
                    categories = categories.sortedBy { it.name },
                    selectedDateFilter = options.filter,
                    selectedDate = options.customDate,
                    sortAscending = options.sortAscending,
                    currentUsdDopRateMicros = options.usdDopRateMicros,
                    allActionableCount = allGroups.size
                )
            }.collect(_uiState)
        }
        refresh()
    }

    private suspend fun autoBookMultiEvidenceMovements() {
        runCatching {
            val ownerId = ownerScope.currentOwnerId()
            val movements = detectedMovementRepository.getAllMovements(ownerId)
            val groups = movements.toActionableGroups().filter {
                it.root.status == "PENDING" && !it.isPossibleDuplicate && it.evidence.size >= 2
            }
            if (groups.isEmpty()) return
            // book() resuelve cuenta y categoria contra _uiState, asi que registrar antes
            // de que el estado se llene falla siempre. Es lo que pasaba corriendo esto solo
            // en el init: se ejecutaba contra un estado vacio y no registraba nada.
            if (_uiState.value.accounts.isEmpty() || _uiState.value.categories.isEmpty()) return
            val accounts = accountRepository.getAccounts().first()
            val categories = categoryRepository.getCategories().first()
            val customRules = categoryRules.rules.first()

            for (group in groups) {
                val root = group.root
                val amount = root.amountMinor ?: continue
                // Solo se registra solo cuando se sabe a que cuenta va. Antes, si los
                // cuatro digitos no cuadraban con ninguna, se cogia la primera de la lista:
                // el movimiento acababa en una cuenta que no era, sin avisar, y descuadraba
                // dos saldos de una vez. Sin cuenta identificada se queda pendiente, que es
                // un toque, no una correccion.
                val account = accounts.firstOrNull { acc ->
                    root.last4Digits != null && acc.cardLastFour == root.last4Digits
                } ?: continue

                val targetText = root.merchant ?: root.title
                // categoryIdFor y no inferCategoryId: el primero mira tus reglas propias
                // antes que las integradas. Con inferCategoryId, apuntar "boxpaq -> Amazon"
                // en Reglas de categorias no servia de nada por esta via, que es
                // precisamente la que registra sin preguntar.
                // Otros y no Alimentacion cuando no se reconoce el comercio. Un consumo
                // sin identificar no es comida: WWW.BRAVE.COM entro como Alimentacion solo
                // porque era el valor por defecto, y un dato inventado con confianza es
                // peor que uno vacio, porque nadie va a ir a revisarlo.
                val catId = root.suggestedCategoryId
                    ?: ExpenseCategorizer.categoryIdFor(targetText, categories, customRules)
                    ?: "cat_otros"

                val category = categories.firstOrNull { it.id == catId }
                    ?: categories.firstOrNull { it.id == "cat_otros" }
                    ?: categories.firstOrNull() ?: continue

                val request = DetectedMovementBookingRequest(
                    canonicalId = root.canonicalId ?: root.id,
                    accountId = account.id,
                    categoryId = category.id,
                    amountMinor = amount,
                    direction = root.direction,
                    occurredAt = root.occurredAt
                )
                book(request)
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        dateRefreshTick.value += 1L
        _uiState.value = _uiState.value.copy(isRefreshing = true, message = null)
        viewModelScope.launch {
            val noticeFailure = runCatching {
                bankNotificationRepository?.reconcileCapturedNotices()
            }.exceptionOrNull()
            // Primero se le pide al buzon que se escanee, y solo despues se baja la lista.
            // Sin la primera parte esto no actualizaba nada: descargaba los candidatos que
            // el servidor ya tuviera, y el servidor solo los rehacia cuando le tocaba al
            // worker de cada 15 minutos. Comprar con la tarjeta, recibir el correo, abrir
            // la app y tocar actualizar no traia el movimiento: no habia nadie mirando el
            // buzon en ese momento.
            val emailFailure = runCatching {
                syncConnectedEmailAccounts()
                emailConnectionsRepository.getCandidates()
            }.exceptionOrNull()
            val currentRate = runCatching { usdDopRateService.currentRateMicros() }.getOrNull()
            currentUsdDopRateMicros.value = currentRate

            // Lo recien llegado tambien se registra solo. Correr esto una sola vez en el
            // init dejaba fuera justo lo que acaba de entrar: comprabas, abrias la app, el
            // movimiento aparecia con sus evidencias y ahi se quedaba esperando un toque.
            autoBookMultiEvidenceMovements()
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                message = when {
                    noticeFailure != null && emailFailure != null ->
                        "Se muestran los datos locales; no se pudieron actualizar correo ni notificaciones."
                    emailFailure != null ->
                        "Se muestran los datos locales; no se pudo actualizar el correo."
                    noticeFailure != null ->
                        "Se muestran los datos locales; no se pudieron reprocesar las notificaciones."
                    else -> null
                }
            )
        }
    }

    fun setDateFilter(filter: DetectedMovementDateFilter) {
        selectedDateFilter.value = filter
        selectedDate.value = null
    }

    fun setCustomDate(date: LocalDate?) {
        selectedDate.value = date
    }

    fun toggleSortOrder() {
        sortAscending.value = !sortAscending.value
    }

    fun resolvePossibleDuplicate(canonicalId: String, keepSeparate: Boolean) = runAction(canonicalId) {
        val resolution = detectedMovementRepository.resolvePossibleDuplicate(
            movementId = canonicalId,
            keepSeparate = keepSeparate,
            ownerId = ownerScope.currentOwnerId()
        )
        when (resolution) {
            PossibleDuplicateResolution.KEPT_SEPARATE ->
                "Los movimientos se conservaron por separado."
            PossibleDuplicateResolution.MERGED_WITH_DETECTED_MOVEMENT ->
                "Las evidencias quedaron unidas en un solo movimiento."
            PossibleDuplicateResolution.MATCHED_EXISTING_TRANSACTION ->
                "La detección quedó vinculada al movimiento que ya habías registrado."
        }
    }

    fun dismiss(canonicalId: String) = runAction(canonicalId) {
        val group = currentGroup(canonicalId)
        val failures = group.evidence.filter { it.source.isEmailEvidence() }
            .filterNot { evidence ->
                runCatching {
                    emailConnectionsRepository.reviewCandidateRemotely(
                        id = requireNotNull(evidence.sourceReference),
                        action = "dismiss",
                        category = null
                    )
                }.isSuccess
            }
        check(failures.isEmpty()) {
            "No se confirmó el descarte en todos los buzones; el movimiento sigue pendiente."
        }
        detectedMovementRepository.dismissCanonicalGroup(canonicalId, ownerScope.currentOwnerId())
        "Movimiento descartado."
    }

    fun book(request: DetectedMovementBookingRequest) = runAction(request.canonicalId) {
        val group = currentGroup(request.canonicalId)
        require(!group.isPossibleDuplicate) {
            "Primero confirma si realmente es un duplicado."
        }
        val account = requireNotNull(_uiState.value.accounts.firstOrNull { it.id == request.accountId }) {
            "Selecciona una cuenta válida."
        }
        val category = requireNotNull(_uiState.value.categories.firstOrNull { it.id == request.categoryId }) {
            "Selecciona una categoría válida."
        }
        val type = request.direction.toTransactionType()
        require(category.type == type || category.type == "BOTH") {
            "La categoría no corresponde al tipo de movimiento."
        }
        require(request.amountMinor > 0L) { "El monto debe ser mayor que cero." }
        require(amountForAccount(group.root, account, _uiState.value.currentUsdDopRateMicros) != null) {
            "La divisa detectada no es compatible con la cuenta."
        }
        require(request.occurredAt > 0L) { "La fecha del movimiento no es válida." }

        val transactionId = DetectedMovementRepository.transactionIdForCanonical(request.canonicalId)
        val existing = transactionRepository.getTransaction(transactionId)
        val transaction = existing ?: Transaction(
                id = transactionId,
                accountId = account.id,
                amount = request.amountMinor,
                type = type,
                categoryId = category.id,
                date = request.occurredAt,
                note = group.root.merchant ?: group.root.title.ifBlank { "Movimiento detectado" },
                currency = account.currency
            )
        if (existing == null) {
            transactionRepository.addTransactionWithBalance(transaction)
            check(transactionRepository.getTransaction(transaction.id) == transaction) {
                "No se pudo verificar el movimiento guardado."
            }
        }

        val bookedCategory = requireNotNull(categoryRepository.getCategory(transaction.categoryId)) {
            "La categoría del movimiento ya no existe."
        }
        val bookedAccount = requireNotNull(accountRepository.getAccount(transaction.accountId)) {
            "La cuenta del movimiento ya no existe."
        }
        val failedEvidence = confirmEmailEvidence(group, bookedCategory.name)
        detectedMovementRepository.completeBookingReview(
            canonicalId = request.canonicalId,
            reviewedEvidenceIds = group.evidence.map { it.id }.toSet(),
            failedEmailEvidenceIds = failedEvidence,
            ownerId = ownerScope.currentOwnerId()
        )
        detectedMovementRepository.resolveSiblingsPointingToBooked(
            canonicalId = request.canonicalId,
            ownerId = ownerScope.currentOwnerId()
        )
        if (request.rememberCategory) {
            val keyword = group.root.merchant ?: group.root.title
            if (keyword.isNotBlank()) categoryRules.add(keyword, bookedCategory.id)
        }
        if (failedEvidence.isEmpty()) {
            "Movimiento agregado a ${bookedAccount.name}."
        } else {
            "El movimiento fue agregado; falta confirmar ${failedEvidence.size} correo(s)."
        }
    }

    fun retryEmailConfirmation(canonicalId: String) = runAction(canonicalId) {
        val group = currentGroup(canonicalId)
        val transactionId = DetectedMovementRepository.transactionIdForCanonical(canonicalId)
        val transaction = requireNotNull(transactionRepository.getTransaction(transactionId)) {
            "No se encontró el movimiento local que debe confirmarse."
        }
        val categoryName = requireNotNull(categoryRepository.getCategory(transaction.categoryId)) {
            "La categoría del movimiento ya no existe."
        }.name
        val retryGroup = group.copy(evidence = group.evidence.filter { it.needsSync })
        val failedEvidence = confirmEmailEvidence(retryGroup, categoryName)
        detectedMovementRepository.completeBookingReview(
            canonicalId = canonicalId,
            reviewedEvidenceIds = retryGroup.evidence.map { it.id }.toSet(),
            failedEmailEvidenceIds = failedEvidence,
            ownerId = ownerScope.currentOwnerId()
        )
        if (failedEvidence.isEmpty()) {
            "Correos confirmados correctamente."
        } else {
            "Aún falta confirmar ${failedEvidence.size} correo(s)."
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private suspend fun confirmEmailEvidence(
        group: DetectedMovementGroup,
        categoryName: String
    ): Set<String> = group.evidence.filter { it.source.isEmailEvidence() }
        .mapNotNullTo(linkedSetOf()) { evidence ->
            val candidateId = evidence.sourceReference ?: return@mapNotNullTo evidence.id
            evidence.id.takeUnless {
                runCatching {
                    emailConnectionsRepository.reviewCandidateRemotely(
                        id = candidateId,
                        action = "categorize",
                        category = categoryName
                    )
                }.isSuccess
            }
        }

    private fun runAction(canonicalId: String, action: suspend () -> String) {
        if (_uiState.value.activeMovementId != null) return
        _uiState.value = _uiState.value.copy(activeMovementId = canonicalId, message = null)
        viewModelScope.launch {
            val result = runCatching { action() }
            val currentRate = runCatching { usdDopRateService.currentRateMicros() }.getOrNull()
            currentUsdDopRateMicros.value = currentRate
            _uiState.value = _uiState.value.copy(
                activeMovementId = null,
                message = result.getOrElse { error ->
                    error.message ?: "No se pudo completar la operación."
                }
            )
        }
    }

    private fun currentGroup(canonicalId: String): DetectedMovementGroup = requireNotNull(
        _uiState.value.groups.firstOrNull { it.root.id == canonicalId }
    ) { "El movimiento ya no está pendiente." }
}

internal fun amountForAccount(
    movement: DetectedMovementEntity,
    account: Account,
    usdDopRateMicros: Long? = null
): Long? = when {
    movement.currency.equals(account.currency, ignoreCase = true) -> movement.amountMinor?.safeAbsoluteValue()
    movement.currency.equals("USD", ignoreCase = true) && account.currency.equals("DOP", ignoreCase = true) && usdDopRateMicros != null ->
        movement.amountMinor?.let { UsdDopRateService.convertUsdMinorToDop(it, usdDopRateMicros) }?.safeAbsoluteValue()
    movement.baseCurrency.equals(account.currency, ignoreCase = true) -> movement.baseAmountMinor?.safeAbsoluteValue()
    else -> null
}

internal fun DetectedMovementGroup.withCategorySuggestion(
    categories: List<Category>,
    customRules: List<CustomCategoryRule>
): DetectedMovementGroup {
    val text = root.merchant ?: root.title
    val suggestion = root.suggestedCategoryId ?: ExpenseCategorizer.categoryIdFor(text, categories, customRules)
    return if (suggestion == root.suggestedCategoryId) this
    else copy(root = root.copy(suggestedCategoryId = suggestion))
}

internal fun List<DetectedMovementEntity>.toActionableGroups(): List<DetectedMovementGroup> {
    val roots = filter { it.duplicateOfId == null }
    return roots.mapNotNull { root ->
        val evidence = filter {
            it.id == root.id ||
            it.canonicalId == root.id ||
            (root.merchant != null && it.merchant == root.merchant &&
             root.amountMinor != null && it.amountMinor == root.amountMinor &&
             root.last4Digits != null && it.last4Digits == root.last4Digits)
        }.distinctBy { it.id }
        val actionable = root.status == "PENDING"
        if (!actionable) null else DetectedMovementGroup(root, evidence.sortedBy { it.occurredAt })
    }.distinctBy { group ->
        val r = group.root
        if (r.merchant != null && r.amountMinor != null && r.last4Digits != null) {
            "${r.merchant.lowercase(Locale.ROOT)}_${r.amountMinor}_${r.last4Digits}_${r.occurredAt / 3600000}"
        } else {
            r.canonicalId ?: r.id
        }
    }.sortedByDescending { it.root.occurredAt }
}

internal fun List<DetectedMovementGroup>.filterByDate(
    filter: DetectedMovementDateFilter,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    customDate: LocalDate? = null
): List<DetectedMovementGroup> {
    val bounds = customDate?.let { date ->
        DetectedMovementDateBounds(
            startInclusive = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endExclusive = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    } ?: filter.bounds(nowMillis, zoneId)
    return filter { it.root.occurredAt >= bounds.startInclusive && it.root.occurredAt < bounds.endExclusive }
}

internal fun List<DetectedMovementGroup>.sortedByDate(ascending: Boolean): List<DetectedMovementGroup> =
    if (ascending) sortedBy { it.root.occurredAt } else sortedByDescending { it.root.occurredAt }

internal fun DetectedMovementDateFilter.bounds(
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): DetectedMovementDateBounds {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val startDate = when (this) {
        DetectedMovementDateFilter.TODAY -> today
        DetectedMovementDateFilter.YESTERDAY -> today.minusDays(1)
        DetectedMovementDateFilter.THIS_WEEK ->
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DetectedMovementDateFilter.THIS_MONTH -> today.withDayOfMonth(1)
    }
    val endDateExclusive = when (this) {
        DetectedMovementDateFilter.YESTERDAY -> today
        else -> today.plusDays(1)
    }
    return DetectedMovementDateBounds(
        startInclusive = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        endExclusive = endDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()
    )
}

internal fun String.isEmailEvidence(): Boolean =
    this == "EMAIL_GMAIL" || this == "EMAIL_MICROSOFT" || this == "EMAIL"

private fun String.toTransactionType(): String = when (lowercase(Locale.ROOT)) {
    "expense" -> "EXPENSE"
    "income" -> "INCOME"
    else -> error("Las transferencias requieren elegir dos cuentas.")
}

private fun Long.safeAbsoluteValue(): Long = if (this == Long.MIN_VALUE) Long.MAX_VALUE else abs(this)
