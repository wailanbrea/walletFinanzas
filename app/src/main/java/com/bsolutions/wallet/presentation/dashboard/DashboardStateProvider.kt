package com.bsolutions.wallet.presentation.dashboard

import com.bsolutions.wallet.core.common.AccountBalances
import com.bsolutions.wallet.core.common.CategoryPlaceholders
import com.bsolutions.wallet.data.preferences.UserProfilePrefs
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.Goal
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.usecase.DEBT_OWED_TO_ME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.Calendar

/**
 * Deriva el estado reactivo del dashboard a partir de los Flows de cada repositorio.
 *
 * Este objeto es testeable sin Hilt: recibe los Flows como parámetros y devuelve
 * un Flow con el estado combinado. El ViewModel se encarga del scope y del sharing.
 * Los tests pueden inyectar Flows controlados y verificar el resultado sin
 * inicializar el ViewModel completo.
 */
object DashboardStateProvider {

    fun combine(
        accounts: Flow<List<Account>>,
        transactions: Flow<List<Transaction>>,
        categories: Flow<List<Category>>,
        profile: Flow<UserProfilePrefs>,
        selectedPeriod: Flow<DashboardPeriodFilter>,
        selectedCategoryId: Flow<String?>,
        debts: Flow<List<Debt>>,
        goals: Flow<List<Goal>>,
        nowMillis: Flow<Long> = MutableStateFlow(System.currentTimeMillis())
    ): Flow<DashboardUiState> {

        // Combinar period + categoría en un solo Flow interno.
        val filters = combine(selectedPeriod, selectedCategoryId) { period, categoryId ->
            period to categoryId
        }

        // Paso 1: combinar los Flows base (sin tiempo ni debts/goals).
        val base = combine(
            accounts,
            transactions,
            categories,
            profile,
            filters
        ) { accounts: List<Account>, transactions: List<Transaction>, categories: List<Category>, profile: UserProfilePrefs, filter: Pair<DashboardPeriodFilter, String?> ->
            val period = filter.first
            val categoryId = filter.second
            val balance = AccountBalances.primaryTotal(accounts)
            val foreignSubtitle = AccountBalances.foreignSubtitle(accounts)
            val categoryMap = categories.associateBy { it.id }
            val effectiveCategoryId = categoryId?.takeIf(categoryMap::containsKey)
            BaseDashboardState(
                balance = balance,
                foreignSubtitle = foreignSubtitle,
                categoryMap = categoryMap,
                effectiveCategoryId = effectiveCategoryId,
                accounts = accounts.toList(),
                allTransactions = transactions,
                selectedPeriod = period,
                selectedCards = DashboardCardType.fromStorageIds(profile.dashboardCardIds),
                balancesHidden = profile.balancesHidden
            )
        }

        // Paso 2: combinar con nowMillis para calcular campos dependientes del tiempo.
        val withTime = base
            .combine(nowMillis) { base: BaseDashboardState, now: Long ->
                val nowCal = Calendar.getInstance().apply { timeInMillis = now }
                val prev = (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }

                fun inMonth(dateMillis: Long, month: Int, year: Int): Boolean {
                    val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    return cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
                }

                fun isPrimaryCurrency(currency: String?): Boolean {
                    if (currency.isNullOrBlank()) return true
                    val c = currency.trim().uppercase()
                    return c == "DOP" || c == "RD$" || c == "RD"
                }

                val period = base.selectedPeriod
                val categoryId = base.effectiveCategoryId
                val categoryMap = base.categoryMap
                val transactions = base.allTransactions

                val startMillis = period.startMillis(now)
                val endMillis = period.endMillis(now)
                val filteredTransactions = transactions
                    .filter { it.date in startMillis..endMillis }
                    .filter { categoryId == null || it.categoryId == categoryId }

                val primaryTx = filteredTransactions.filter { isPrimaryCurrency(it.currency) }
                val thisMonthTx = if (primaryTx.isNotEmpty() || transactions.none { isPrimaryCurrency(it.currency) }) {
                    primaryTx.ifEmpty { filteredTransactions }
                } else {
                    primaryTx
                }
                val consumptionTx = thisMonthTx.filter { it.isConsumption }
                val income = consumptionTx.filter { it.type == "INCOME" }.sumOf { it.amount }
                val expenses = consumptionTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }

                val prevExpenses = transactions
                    .filter { inMonth(it.date, prev.get(Calendar.MONTH), prev.get(Calendar.YEAR)) }
                    .filter { categoryId == null || it.categoryId == categoryId }
                    .filter { isPrimaryCurrency(it.currency) }
                    .filter { it.isConsumption && it.type == "EXPENSE" }
                    .sumOf { it.amount }
                val trend = if (period == DashboardPeriodFilter.THIS_MONTH && prevExpenses > 0) {
                    (((expenses - prevExpenses).toDouble() / prevExpenses) * 100).toInt()
                } else null

                val daysInMonth = nowCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val currentDay = nowCal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
                val expectedSpendingSoFar = if (prevExpenses > 0) (prevExpenses * (currentDay.toDouble() / daysInMonth)).toLong() else 0L
                val pace = if (period == DashboardPeriodFilter.THIS_MONTH && expectedSpendingSoFar > 0) {
                    (((expenses - expectedSpendingSoFar).toDouble() / expectedSpendingSoFar) * 100).toInt()
                } else null

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

                // Sin fallback: un filtro que no encuentra nada en el periodo debe devolver
                // lista vacia, no movimientos de otro rango que engarian al usuario.
                val recent = filteredTransactions
                    .sortedByDescending { it.date }
                    .take(5)

                DashboardUiState(
                    totalBalance = base.balance,
                    foreignBalancesSubtitle = base.foreignSubtitle,
                    monthlyIncome = income,
                    monthlyExpenses = expenses,
                    periodDebtTransactions = thisMonthTx.filter { it.debtId != null },
                    expenseTrendPercent = trend,
                    spendingPacePercent = pace,
                    categorySpending = spending,
                    recentTransactions = recent,
                    accounts = base.accounts,
                    categories = categoryMap,
                    selectedPeriod = period,
                    selectedCategoryId = base.effectiveCategoryId,
                    selectedCards = base.selectedCards,
                    balancesHidden = base.balancesHidden
                )
            }

        // Paso 3: encadenar debts.
        val withDebts = withTime
            .combine(debts) { state: DashboardUiState, debts: List<Debt> ->
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

        // Paso 4: encadenar goals.
        return withDebts
            .combine(goals) { state: DashboardUiState, goals: List<Goal> ->
                val smallest = goals.filterNot { it.isCompleted }.minByOrNull { it.targetAmount }
                state.copy(waterLevel = smallest?.progress?.coerceIn(0f, 1f) ?: 0.5f)
            }
    }
}

/**
 * Estado base del dashboard: campos que no dependen del tiempo actual.
 */
private data class BaseDashboardState(
    val balance: Long,
    val foreignSubtitle: String?,
    val categoryMap: Map<String, Category>,
    val effectiveCategoryId: String?,
    val accounts: List<Account>,
    val allTransactions: List<Transaction>,
    val selectedPeriod: DashboardPeriodFilter,
    val selectedCards: Set<DashboardCardType>,
    val balancesHidden: Boolean
)
