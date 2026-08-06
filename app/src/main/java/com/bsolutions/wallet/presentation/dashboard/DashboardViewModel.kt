package com.bsolutions.wallet.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.AccountBalances
import com.bsolutions.wallet.core.common.CategoryRuleRepository
import com.bsolutions.wallet.core.common.CategoryPlaceholders
import com.bsolutions.wallet.core.common.DefaultCategories
import com.bsolutions.wallet.core.common.EmptyCategoryRules
import com.bsolutions.wallet.core.common.ExpenseCategorizer
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.data.preferences.UserProfilePreferences
import com.bsolutions.wallet.domain.model.TRANSFER_CATEGORY_ID
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.repository.GoalRepository
import com.bsolutions.wallet.domain.usecase.DEBT_OWED_TO_ME
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
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

private data class DashboardFilters(
    val period: DashboardPeriodFilter = DashboardPeriodFilter.THIS_MONTH,
    val categoryId: String? = null
)

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
            // Incluye tombstones: una categoría eliminada por el usuario no debe
            // reaparecer en el siguiente arranque por efecto del seed.
            val existingIds = categoryRepository.getAllCategoryIdsIncludingDeleted()
            DefaultCategories.asCategories()
                .filter { it.id !in existingIds }
                .forEach { categoryRepository.addCategory(it) }
            migrateFoodCategories()
        }
    }

    private val selectedPeriod = MutableStateFlow(DashboardPeriodFilter.THIS_MONTH)
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val filters = combine(selectedPeriod, selectedCategoryId) { period, categoryId ->
        DashboardFilters(period, categoryId)
    }
    private val refreshTick = MutableStateFlow(0L)
    private val filtersWithRefresh = combine(filters, refreshTick) { activeFilters, _ -> activeFilters }
    internal var nowMillisProvider: () -> Long = System::currentTimeMillis


    /** Normaliza el grupo de alimentos sin tocar montos ni saldos históricos. */
    private suspend fun migrateFoodCategories() {
        val categories = categoryRepository.getCategories().first()
        val supermarket = categories.firstOrNull { it.id == "cat_supermercado" } ?: return
        val aliases = categories.filter { category ->
            category.id != supermarket.id && ExpenseCategorizer.normalizeText(category.name) in setOf("super mercado", "supermercado")
        }
        val transactions = transactionRepository.getTransactions().first()
        val supermarketIds = aliases.mapTo(mutableSetOf()) { it.id } + supermarket.id
        transactions.filter { transaction ->
            transaction.categoryId in aliases.map { it.id } ||
                (transaction.type == "EXPENSE" && transaction.categoryId == "cat_alimentacion" &&
                    ExpenseCategorizer.inferCategoryId(transaction.note) == "cat_supermercado")
        }.forEach { transaction ->
            if (transaction.categoryId != supermarket.id) {
                transactionRepository.updateTransaction(transaction.copy(categoryId = supermarket.id))
            }
        }
        aliases.forEach { categoryRepository.deleteCategory(it.id) }
        val supermarketWords = setOf("supermerc", "colmado", "nacional", "jumbo", "sirena", "pola", "bravo", "market", "grocer")
        categoryRules.rules.first().filter { rule ->
            rule.categoryId in supermarketIds && supermarketWords.any { word -> ExpenseCategorizer.normalizeText(rule.keyword).contains(word) }
        }.forEach { rule ->
            categoryRules.remove(rule.keyword)
            categoryRules.add(rule.keyword, supermarket.id)
        }
    }

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
        refreshTick.value += 1L
    }

    fun setDashboardCardEnabled(card: DashboardCardType, enabled: Boolean) {
        if (card == DashboardCardType.TOTAL_BALANCE && !enabled) return
        viewModelScope.launch {
            val cardIds = userPreferencesRepository.profile.first().dashboardCardIds.toMutableSet()
            if (enabled) cardIds += card.storageId else cardIds -= card.storageId
            userPreferencesRepository.setDashboardCardIds(cardIds)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        accountRepository.getAccounts(),
        transactionRepository.getTransactions(),
        categoryRepository.getCategories(),
        userPreferencesRepository.profile,
        filtersWithRefresh
    ) { accounts, transactions, categories, profile, activeFilters ->
        // Balance principal solo en RD$; otras divisas van como subtotales aparte
        val balance = AccountBalances.primaryTotal(accounts)
        val foreignSubtitle = AccountBalances.foreignSubtitle(accounts)
        val categoryMap = categories.associateBy { it.id }
        val effectiveCategoryId = activeFilters.categoryId?.takeIf(categoryMap::containsKey)

        val nowMillis = nowMillisProvider()
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val prev = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }

        fun inMonth(dateMillis: Long, month: Int, year: Int): Boolean {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            return cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
        }

        fun isPrimaryCurrency(currency: String?): Boolean {
            if (currency.isNullOrBlank()) return true
            val c = currency.trim().uppercase()
            return c == "DOP" || c == "RD$" || c == "RD"
        }

        // Ingresos/Gastos del filtro: solo movimientos del período seleccionado.
        val startMillis = activeFilters.period.startMillis(nowMillis)
        val endMillis = activeFilters.period.endMillis(nowMillis)
        val filteredTransactions = transactions
            .filter { it.date in startMillis..endMillis }
            .filter { effectiveCategoryId == null || it.categoryId == effectiveCategoryId }

        val primaryTx = filteredTransactions.filter { isPrimaryCurrency(it.currency) }
        val thisMonthTx = if (primaryTx.isNotEmpty() || transactions.none { isPrimaryCurrency(it.currency) }) {
            primaryTx.ifEmpty { filteredTransactions }
        } else {
            primaryTx
        }
        // Prestar dinero y cobrarlo no es consumo: el patrimonio no cambia, se cambia
        // efectivo por un derecho de cobro. Si contara, el mes en que prestas se veria
        // como un gasto enorme y el mes en que te pagan como un ingreso que no ganaste.
        // El saldo de la cuenta si se movio, y eso lo refleja el balance, no estos totales.
        val consumptionTx = thisMonthTx.filter { it.isConsumption }
        val income = consumptionTx.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expenses = consumptionTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        // Lo prestado sale del bolsillo aunque no sea gasto: sin mostrarlo, el dinero que
        // falta en la cuenta no aparece en ningun lado del flujo y no cuadra.
        // Solo lo atado a una deuda. Filtrar por "no es consumo" metia aqui tambien las
        // transferencias, y mover dinero a tu propia tarjeta aparecia como cobrado.
        // El reparto entre prestado y cobrado se hace mas abajo, cuando se conocen las
        // direcciones: pagar una deuda propia tambien es un gasto atado a una deuda, y
        // aqui no hay forma de distinguirlo de prestar.

        // Tendencia real: gasto de este mes vs el mes anterior
        val prevExpenses = transactions
            .filter { inMonth(it.date, prev.get(Calendar.MONTH), prev.get(Calendar.YEAR)) }
            .filter { effectiveCategoryId == null || it.categoryId == effectiveCategoryId }
            .filter { isPrimaryCurrency(it.currency) }
            .filter { it.isConsumption && it.type == "EXPENSE" }
            .sumOf { it.amount }
        val trend = if (activeFilters.period == DashboardPeriodFilter.THIS_MONTH && prevExpenses > 0) {
            (((expenses - prevExpenses).toDouble() / prevExpenses) * 100).toInt()
        } else null

        // Ritmo de gasto acumulado vs lo esperado para el día del mes
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val expectedSpendingSoFar = if (prevExpenses > 0) (prevExpenses * (currentDay.toDouble() / daysInMonth)).toLong() else 0L
        val pace = if (activeFilters.period == DashboardPeriodFilter.THIS_MONTH && expectedSpendingSoFar > 0) {
            (((expenses - expectedSpendingSoFar).toDouble() / expectedSpendingSoFar) * 100).toInt()
        } else null

        // Gasto filtrado por categoría (para el donut del dashboard)
        val spending = consumptionTx
            .filter { it.type == "EXPENSE" }
            .groupBy { CategoryPlaceholders.aggregateId(it.categoryId, categoryMap) }
            .map { (catId, txs) ->
                val cat = categoryMap[catId] ?: CategoryPlaceholders.uncategorized()
                val amount = txs.sumOf { it.amount }
                CategorySpend(
                    category = cat,
                    amount = amount,
                    percentage = if (expenses > 0) ((amount.toDouble() / expenses) * 100).toInt() else 0
                )
            }
            .sortedByDescending { it.amount }

        // Transacciones recientes: las 5 más recientes (filtradas por categoría si hay filtro activo)
        val recent = filteredTransactions
            .sortedByDescending { it.date }
            .take(5)
            .ifEmpty {
                transactions
                    .filter { effectiveCategoryId == null || it.categoryId == effectiveCategoryId }
                    .sortedByDescending { it.date }
                    .take(5)
            }

        DashboardUiState(
            totalBalance = balance,
            foreignBalancesSubtitle = foreignSubtitle,
            monthlyIncome = income,
            monthlyExpenses = expenses,
            periodDebtTransactions = thisMonthTx.filter { it.debtId != null },
            expenseTrendPercent = trend,
            spendingPacePercent = pace,
            categorySpending = spending,
            recentTransactions = recent,
            accounts = accounts.toList(),
            categories = categoryMap,
            selectedPeriod = activeFilters.period,
            selectedCategoryId = effectiveCategoryId,
            selectedCards = DashboardCardType.fromStorageIds(profile.dashboardCardIds),
            balancesHidden = profile.balancesHidden
        )
    }
        // Encadenado y no dentro del combine porque el overload tipado se queda en cinco.
        .combine(debtRepository.getDebts()) { state, debts ->
            // Solo las deudas a tu favor cuentan como prestar y cobrar. Pagar una deuda
            // propia es un gasto atado a una deuda igual que prestar, y sin mirar la
            // direccion se sumaba a "Prestado" dinero que en realidad estabas devolviendo.
            val receivables = debts.filterTo(mutableSetOf()) { it.direction == DEBT_OWED_TO_ME }
                .mapTo(mutableSetOf()) { it.id }
            val mine = state.periodDebtTransactions.filter { it.debtId in receivables }
            val open = debts.filter { it.direction == DEBT_OWED_TO_ME && !it.isClosed }

            state.copy(
                monthlyLent = mine.filter { it.type == "EXPENSE" }.sumOf { it.amount },
                monthlyCollected = mine.filter { it.type == "INCOME" }.sumOf { it.amount },
                periodDebtTransactions = emptyList(),
                outstandingReceivable = open.sumOf { it.remainingAmount },
                openReceivableCount = open.size
            )
        }
        .combine(goalRepository.getGoals()) { state, goals ->
            val smallest = goals.filterNot { it.isCompleted }.minByOrNull { it.targetAmount }
            state.copy(waterLevel = smallest?.progress?.coerceIn(0f, 1f) ?: 0.5f)
        }
        .stateIn(
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
