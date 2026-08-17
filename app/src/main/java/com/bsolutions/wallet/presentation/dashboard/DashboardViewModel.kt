package com.bsolutions.wallet.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.CategoryRuleRepository
import com.bsolutions.wallet.core.common.EmptyCategoryRules
import com.bsolutions.wallet.core.common.ExpenseCategorizer
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.data.preferences.UserProfilePreferences
import com.bsolutions.wallet.domain.model.TRANSFER_CATEGORY_ID
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.repository.GoalRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class CategorySpend(
    val category: Category,
    val amount: Long,
    val percentage: Int
)

enum class DashboardCardType(val storageId: String) {
    TOTAL_BALANCE("total_balance"),
    CASH_FLOW("cash_flow"),
    EXPENSE_STRUCTURE("expense_structure"),
    RECENT_TRANSACTIONS("recent_transactions"),
    ACCOUNT_BALANCES("account_balances");

    companion object {
        val defaultCards: Set<DashboardCardType> = entries.toSet()

        fun fromStorageIds(ids: Set<String>): Set<DashboardCardType> =
            entries.filterTo(linkedSetOf()) { it.storageId in ids }
                .apply { add(TOTAL_BALANCE) }
    }
}

enum class DashboardPeriodFilter {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_12_WEEKS,
    LAST_6_MONTHS,
    LAST_1_YEAR,
    LAST_5_YEARS;

    fun startMillis(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val startDate = when (this) {
            TODAY -> today
            THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            THIS_MONTH -> today.withDayOfMonth(1)
            THIS_YEAR -> today.withDayOfYear(1)
            LAST_7_DAYS -> today.minusDays(6)
            LAST_30_DAYS -> today.minusDays(29)
            LAST_12_WEEKS -> today.minusDays(83)
            LAST_6_MONTHS -> today.minusMonths(6)
            LAST_1_YEAR -> today.minusYears(1)
            LAST_5_YEARS -> today.minusYears(5)
        }
        return startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun endMillis(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val endDate = when (this) {
            TODAY -> today
            THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6)
            THIS_MONTH -> today.with(TemporalAdjusters.lastDayOfMonth())
            THIS_YEAR -> today.with(TemporalAdjusters.lastDayOfYear())
            else -> today
        }
        return endDate.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()
    }
}

data class DashboardUiState(
    val totalBalance: Long = 0L,
    /** Subtotales en divisas distintas de RD$ (null si todas las cuentas son DOP). */
    val foreignBalancesSubtitle: String? = null,
    val monthlyIncome: Long = 0L,
    val monthlyExpenses: Long = 0L,
    /** Dinero que salió en el período por préstamos a terceros. */
    val monthlyLent: Long = 0L,
    /** Dinero que volvió en el período por deudas cobradas. */
    val monthlyCollected: Long = 0L,
    /** Lo que sigue pendiente de cobrar, de todas las deudas abiertas. */
    val outstandingReceivable: Long = 0L,
    /** Cuantas deudas a tu favor siguen abiertas; acompana a [outstandingReceivable]. */
    val openReceivableCount: Int = 0,
    /**
     * Movimientos del período atados a una deuda, a la espera de saber su dirección.
     *
     * Se llena en el paso que agrega los movimientos y se vacía en el que conoce las
     * deudas: pagar una deuda propia y prestar dinero son ambos un gasto atado a una
     * deuda, y solo la dirección los distingue.
     */
    val periodDebtTransactions: List<Transaction> = emptyList(),
    /**
     * Cuánto llena el agua de la tarjeta de balance.
     *
     * Sin metas es decorativa y llega a la mitad. Con metas representa el avance de la
     * más pequeña, que es la que está más cerca de cumplirse.
     */
    val waterLevel: Float = 0.5f,
    /** Variación % del gasto de este mes vs el anterior; null si no hay base de comparación. */
    val expenseTrendPercent: Int? = null,
    /** Ritmo de gasto acumulado vs lo esperado para el día del mes (% ritmico). */
    val spendingPacePercent: Int? = null,
    val categorySpending: List<CategorySpend> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    val selectedPeriod: DashboardPeriodFilter = DashboardPeriodFilter.THIS_MONTH,
    val selectedCategoryId: String? = null,
    val selectedCards: Set<DashboardCardType> = DashboardCardType.defaultCards,
    /** Modo privacidad: si está activo, la UI ofusca los montos. */
    val balancesHidden: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserProfilePreferences,
    private val debtRepository: DebtRepository,
    private val goalRepository: GoalRepository,
    // Default para tests: Hilt inyecta la implementación real de todos modos.
    private val categoryRules: CategoryRuleRepository = EmptyCategoryRules
) : ViewModel() {

    init {
        // Asegura que existan las categorías por defecto (aditivo por id): siembra las que
        // falten, así también aparecen categorías nuevas del set en instalaciones existentes.
        viewModelScope.launch {
            DashboardCategorySeeder.seed(
                categoryRepository,
                transactionRepository,
                categoryRules
            )
        }
    }

    private val selectedPeriod = MutableStateFlow(DashboardPeriodFilter.THIS_MONTH)
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    internal val nowMillis: StateFlow<Long> = _nowMillis
    internal var nowMillisProvider: () -> Long = { System.currentTimeMillis() }
        set(value) { field = value; _nowMillis.value = value() }

    fun toggleBalancesHidden() {
        viewModelScope.launch {
            val current = userPreferencesRepository.profile.first().balancesHidden
            userPreferencesRepository.setBalancesHidden(!current)
        }
    }

    fun setPeriodFilter(period: DashboardPeriodFilter) {
        selectedPeriod.value = period
    }

    fun setCategoryFilter(categoryId: String?) {
        selectedCategoryId.value = categoryId?.takeIf { it.isNotBlank() }
    }

    fun refreshTime() {
        _nowMillis.value = nowMillisProvider()
    }

    fun setDashboardCardEnabled(card: DashboardCardType, enabled: Boolean) {
        if (card == DashboardCardType.TOTAL_BALANCE && !enabled) return
        viewModelScope.launch {
            val cardIds = userPreferencesRepository.profile.first().dashboardCardIds.toMutableSet()
            if (enabled) cardIds += card.storageId else cardIds -= card.storageId
            userPreferencesRepository.setDashboardCardIds(cardIds)
        }
    }

    val uiState: StateFlow<DashboardUiState> = DashboardStateProvider.combine(
        accounts = accountRepository.getAccounts(),
        transactions = transactionRepository.getTransactions(),
        categories = categoryRepository.getCategories(),
        profile = userPreferencesRepository.profile,
        selectedPeriod = selectedPeriod,
        selectedCategoryId = selectedCategoryId,
        debts = debtRepository.getDebts(),
        goals = goalRepository.getGoals(),
        nowMillis = nowMillis
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun addTransaction(accountId: String, amount: Long, type: String, categoryId: String, note: String) {
        if (amount <= 0L || accountId.isBlank()) return
        viewModelScope.launch {
            // Divisa heredada de la cuenta; saldo + movimiento en una sola transacción atómica.
            val account = accountRepository.getAccount(accountId) ?: return@launch
            // Sin categoría elegida: se infiere de la nota (reglas del usuario primero).
            val finalCategoryId = categoryId.ifBlank {
                ExpenseCategorizer.categoryIdFor(
                    text = note,
                    categories = categoryRepository.getCategories().first(),
                    customRules = categoryRules.rules.first()
                ).orEmpty()
            }
            transactionRepository.addTransactionWithBalance(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    amount = amount,
                    type = type,
                    categoryId = finalCategoryId,
                    date = System.currentTimeMillis(),
                    note = note,
                    currency = account.currency
                )
            )
        }
    }

    /** Mueve dinero entre dos cuentas registrando un movimiento TRANSFER en cada una. */
    fun transfer(fromAccountId: String, toAccountId: String, amount: Long, note: String) {
        if (amount <= 0L || fromAccountId.isBlank() || toAccountId.isBlank() || fromAccountId == toAccountId) return
        viewModelScope.launch {
            val from = accountRepository.getAccount(fromAccountId) ?: return@launch
            val to = accountRepository.getAccount(toAccountId) ?: return@launch
            val now = System.currentTimeMillis()
            val label = note.ifBlank { "Transferencia ${from.name} → ${to.name}" }

            // Dos movimientos y no uno. El servidor no tiene el concepto de
            // transferencia: solo movimientos con importe sobre una cuenta. Con uno solo
            // la operacion nunca llegaba, y al sincronizar los saldos volvian del
            // servidor como si no se hubiera hecho.
            //
            // Van por addTransactionWithBalance, que ajusta el saldo y encola la subida;
            // executeTransfer escribia en local sin encolar nada, que era el fallo.
            val base = UUID.randomUUID().toString()
            transactionRepository.addTransactionWithBalance(
                Transaction(
                    id = "$base-out",
                    accountId = fromAccountId,
                    amount = amount,
                    type = "EXPENSE",
                    categoryId = TRANSFER_CATEGORY_ID,
                    date = now,
                    note = label,
                    currency = from.currency
                )
            )
            transactionRepository.addTransactionWithBalance(
                Transaction(
                    id = "$base-in",
                    accountId = toAccountId,
                    amount = amount,
                    type = "INCOME",
                    categoryId = TRANSFER_CATEGORY_ID,
                    date = now,
                    note = label,
                    currency = to.currency
                )
            )
        }
    }

    fun saveCategoryRule(keyword: String, categoryId: String) {
        if (keyword.isBlank() || categoryId.isBlank()) return
        viewModelScope.launch {
            categoryRules.add(keyword, categoryId)
        }
    }

    fun addSplitTransaction(
        accountId: String,
        type: String,
        splits: List<Pair<Long, String>>,
        note: String
    ) {
        if (splits.isEmpty() || accountId.isBlank()) return
        viewModelScope.launch {
            val account = accountRepository.getAccount(accountId) ?: return@launch
            val now = System.currentTimeMillis()
            val baseNote = note.ifBlank { "Transacción dividida" }

            splits.forEachIndexed { index, (amount, categoryId) ->
                if (amount > 0L) {
                    val finalCategoryId = categoryId.ifBlank {
                        ExpenseCategorizer.categoryIdFor(
                            text = baseNote,
                            categories = categoryRepository.getCategories().first(),
                            customRules = categoryRules.rules.first()
                        ).orEmpty()
                    }
                    transactionRepository.addTransactionWithBalance(
                        Transaction(
                            id = UUID.randomUUID().toString(),
                            accountId = accountId,
                            amount = amount,
                            type = type,
                            categoryId = finalCategoryId,
                            date = now + index,
                            note = "$baseNote (${index + 1}/${splits.size})",
                            currency = account.currency
                        )
                    )
                }
            }
        }
    }
}
