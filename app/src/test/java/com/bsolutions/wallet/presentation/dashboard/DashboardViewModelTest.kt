package com.bsolutions.wallet.presentation.dashboard

import com.bsolutions.wallet.data.preferences.UserProfilePrefs
import com.bsolutions.wallet.data.preferences.UserProfilePreferences
import com.bsolutions.wallet.core.common.DefaultCategories
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction

import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.Goal
import com.bsolutions.wallet.domain.repository.GoalRepository
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tests de la lógica de mes en curso, tendencia de gasto y gasto por categoría
 * del dashboard. Las categorías nunca están vacías para no disparar el seed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Helpers de fechas relativas al mes en curso ---
    private fun thisMonth(day: Int = 10): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
    }.timeInMillis

    private fun lastMonth(day: Int = 10): Long = Calendar.getInstance().apply {
        add(Calendar.MONTH, -1)
        set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
    }.timeInMillis

    private fun tx(id: String, amount: Long, type: String, date: Long, categoryId: String = "food") =
        Transaction(id, "acc-1", amount, type, categoryId, date, "nota-$id")

    private fun buildViewModel(
        transactions: List<Transaction>,
        categories: List<Category> = listOf(Category("food", "Alimentación", "restaurant", "#1B873F")),
        preferences: FakeUserProfilePreferences = FakeUserProfilePreferences(),
        debts: List<Debt> = emptyList(),
        goals: List<Goal> = emptyList()
    ): DashboardViewModel = DashboardViewModel(
        FakeAccountRepository(Account("acc-1", "Cuenta", "BANK", 50_000L, "DOP")),
        FakeTransactionRepository(transactions),
        FakeCategoryRepository(categories),
        preferences,
        FakeDebtRepository(debts),
        FakeGoalRepository(goals)
    )

    private suspend fun kotlinx.coroutines.test.TestScope.awaitState(viewModel: DashboardViewModel): DashboardUiState {
        val job = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val state = viewModel.uiState.value
        job.cancel()
        return state
    }

    @Test
    fun `solo cuenta ingresos y gastos del mes en curso`() = runTest {
        val viewModel = buildViewModel(
            listOf(
                tx("1", 10_000L, "INCOME", thisMonth()),
                tx("2", 3_000L, "EXPENSE", thisMonth()),
                tx("3", 99_000L, "INCOME", lastMonth()), // mes anterior: fuera
                tx("4", 88_000L, "EXPENSE", lastMonth())
            )
        )

        val state = awaitState(viewModel)

        assertEquals(10_000L, state.monthlyIncome)
        assertEquals(3_000L, state.monthlyExpenses)
    }

    @Test
    fun `un prestamo no es gasto pero si aparece en el flujo`() = runTest {
        val viewModel = buildViewModel(
            listOf(
                tx("1", 3_000L, "EXPENSE", thisMonth()),
                // Prestado y cobrado: atados a una deuda, no son consumo propio.
                tx("2", 20_000L, "EXPENSE", thisMonth()).copy(debtId = "d-1"),
                tx("3", 5_000L, "INCOME", thisMonth()).copy(debtId = "d-1")
            ),
            debts = listOf(Debt("d-1", "David", "La mica", "OWED_TO_ME", 20_000L, 5_000L, null, false))
        )

        val state = awaitState(viewModel)

        // Fuera de gastos e ingresos: si contaran, el mes se veria como un gasto enorme.
        assertEquals(3_000L, state.monthlyExpenses)
        assertEquals(0L, state.monthlyIncome)
        // Pero visibles en el flujo, porque el dinero si se movio de la cuenta.
        assertEquals(20_000L, state.monthlyLent)
        assertEquals(5_000L, state.monthlyCollected)
        assertEquals(15_000L, state.outstandingReceivable)
        // Y tampoco distorsionan el donut.
        assertEquals(3_000L, state.categorySpending.sumOf { it.amount })
    }

    @Test
    fun `tendencia compara gasto de este mes contra el anterior`() = runTest {
        val viewModel = buildViewModel(
            listOf(
                tx("1", 15_000L, "EXPENSE", thisMonth()),
                tx("2", 10_000L, "EXPENSE", lastMonth())
            )
        )

        val state = awaitState(viewModel)

        // (15000 - 10000) / 10000 = +50%
        assertEquals(50, state.expenseTrendPercent)
    }

    @Test
    fun `sin gasto previo no hay tendencia`() = runTest {
        val viewModel = buildViewModel(listOf(tx("1", 5_000L, "EXPENSE", thisMonth())))

        val state = awaitState(viewModel)

        assertNull(state.expenseTrendPercent)
    }

    @Test
    fun `gasto por categoria calcula porcentajes del mes`() = runTest {
        val categories = listOf(
            Category("food", "Alimentación", "restaurant", "#1B873F"),
            Category("transport", "Transporte", "directions_car", "#C62828")
        )
        val viewModel = buildViewModel(
            listOf(
                tx("1", 7_500L, "EXPENSE", thisMonth(), categoryId = "food"),
                tx("2", 2_500L, "EXPENSE", thisMonth(), categoryId = "transport"),
                tx("3", 90_000L, "EXPENSE", lastMonth(), categoryId = "food") // fuera del mes
            ),
            categories = categories
        )

        val state = awaitState(viewModel)

        assertEquals(2, state.categorySpending.size)
        val food = state.categorySpending.first { it.category.id == "food" }
        val transport = state.categorySpending.first { it.category.id == "transport" }
        assertEquals(7_500L, food.amount)
        assertEquals(75, food.percentage)
        assertEquals(25, transport.percentage)
        // Ordenado por monto descendente
        assertEquals("food", state.categorySpending.first().category.id)
    }

    @Test
    fun `gasto sin categoria o con referencia eliminada permanece visible`() = runTest {
        val viewModel = buildViewModel(
            listOf(
                tx("blank", 1_000L, "EXPENSE", thisMonth(), categoryId = ""),
                tx("deleted", 2_000L, "EXPENSE", thisMonth(), categoryId = "deleted-id"),
                tx("known", 3_000L, "EXPENSE", thisMonth(), categoryId = "food")
            )
        )

        val state = awaitState(viewModel)

        assertEquals(6_000L, state.monthlyExpenses)
        assertEquals(2, state.categorySpending.size)
        val uncategorized = state.categorySpending.first { it.category.id == "__uncategorized__" }
        assertEquals(3_000L, uncategorized.amount)
        assertEquals(50, uncategorized.percentage)
    }

    @Test
    fun `transacciones recientes ordenadas por fecha descendente y limitadas a 5`() = runTest {
        val base = thisMonth(day = 1)
        val viewModel = buildViewModel(
            (1..7).map { tx("tx-$it", 100L * it, "EXPENSE", base + it * 1_000L) }
        )

        val state = awaitState(viewModel)

        assertEquals(5, state.recentTransactions.size)
        assertEquals("tx-7", state.recentTransactions.first().id)
        assertEquals("tx-3", state.recentTransactions.last().id)
    }

    @Test
    fun `rangos de fecha producen los inicios esperados`() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-07-20T15:30:00Z").toEpochMilli()
        val expected = mapOf(
            DashboardPeriodFilter.TODAY to LocalDate.of(2026, 7, 20),
            DashboardPeriodFilter.THIS_WEEK to LocalDate.of(2026, 7, 20),
            DashboardPeriodFilter.THIS_MONTH to LocalDate.of(2026, 7, 1),
            DashboardPeriodFilter.THIS_YEAR to LocalDate.of(2026, 1, 1),
            DashboardPeriodFilter.LAST_7_DAYS to LocalDate.of(2026, 7, 14),
            DashboardPeriodFilter.LAST_30_DAYS to LocalDate.of(2026, 6, 21),
            DashboardPeriodFilter.LAST_12_WEEKS to LocalDate.of(2026, 4, 28),
            DashboardPeriodFilter.LAST_6_MONTHS to LocalDate.of(2026, 1, 20),
            DashboardPeriodFilter.LAST_1_YEAR to LocalDate.of(2025, 7, 20),
            DashboardPeriodFilter.LAST_5_YEARS to LocalDate.of(2021, 7, 20)
        )

        expected.forEach { (period, date) ->
            val actual = Instant.ofEpochMilli(period.startMillis(now, zone)).atZone(zone).toLocalDate()
            assertEquals(period.name, date, actual)
        }
    }

    @Test
    fun `filtro por hoy y categoria recalcula resumen grafico y recientes`() = runTest {
        val fixedNow = Instant.parse("2026-07-20T15:30:00Z").toEpochMilli()
        val today = Instant.parse("2026-07-20T12:00:00Z").toEpochMilli()
        val yesterday = Instant.parse("2026-07-19T12:00:00Z").toEpochMilli()
        val categories = listOf(
            Category("food", "Alimentación", "restaurant", "#1B873F"),
            Category("transport", "Transporte", "directions_car", "#C62828")
        )
        val viewModel = buildViewModel(
            listOf(
                tx("food-today", 1_000L, "EXPENSE", today, "food"),
                tx("transport-today", 2_000L, "EXPENSE", today, "transport"),
                tx("food-yesterday", 3_000L, "EXPENSE", yesterday, "food")
            ),
            categories
        )
        viewModel.nowMillisProvider = { fixedNow }
        val job = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setPeriodFilter(DashboardPeriodFilter.TODAY)
        viewModel.setCategoryFilter("food")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DashboardPeriodFilter.TODAY, state.selectedPeriod)
        assertEquals("food", state.selectedCategoryId)
        assertEquals(1_000L, state.monthlyExpenses)
        assertEquals(listOf("food-today"), state.recentTransactions.map { it.id })
        assertEquals(listOf("food"), state.categorySpending.map { it.category.id })
        job.cancel()
    }

    @Test
    fun `tarjetas predeterminadas se muestran y quitar una se persiste`() = runTest {
        val preferences = FakeUserProfilePreferences()
        val viewModel = buildViewModel(emptyList(), preferences = preferences)
        val job = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(DashboardCardType.defaultCards, viewModel.uiState.value.selectedCards)

        viewModel.setDashboardCardEnabled(DashboardCardType.EXPENSE_STRUCTURE, false)
        advanceUntilIdle()

        assertEquals(
            DashboardCardType.defaultCards - DashboardCardType.EXPENSE_STRUCTURE,
            viewModel.uiState.value.selectedCards
        )
        assertEquals(
            viewModel.uiState.value.selectedCards.mapTo(mutableSetOf()) { it.storageId },
            preferences.profile.value.dashboardCardIds
        )
        job.cancel()
    }

    @Test
    fun `balance total permanece porque contiene el unico acceso a filtros`() = runTest {
        val viewModel = buildViewModel(emptyList())
        val job = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setDashboardCardEnabled(DashboardCardType.TOTAL_BALANCE, false)
        advanceUntilIdle()

        assertEquals(true, DashboardCardType.TOTAL_BALANCE in viewModel.uiState.value.selectedCards)
        job.cancel()
    }

    @Test
    fun `refrescar reloj actualiza el filtro hoy al cambiar de dia`() = runTest {
        var now = Instant.parse("2026-07-20T23:59:00Z").toEpochMilli()
        val viewModel = buildViewModel(
            listOf(
                tx("day-one", 1_000L, "EXPENSE", Instant.parse("2026-07-20T12:00:00Z").toEpochMilli()),
                tx("day-two", 2_000L, "EXPENSE", Instant.parse("2026-07-21T12:00:00Z").toEpochMilli())
            )
        )
        viewModel.nowMillisProvider = { now }
        val job = backgroundScope.launch { viewModel.uiState.collect { } }
        viewModel.setPeriodFilter(DashboardPeriodFilter.TODAY)
        advanceUntilIdle()
        assertEquals(listOf("day-one"), viewModel.uiState.value.recentTransactions.map { it.id })

        now = Instant.parse("2026-07-21T23:59:00Z").toEpochMilli()
        viewModel.refreshTime()
        advanceUntilIdle()

        assertEquals(listOf("day-two"), viewModel.uiState.value.recentTransactions.map { it.id })
        job.cancel()
    }

    @Test
    fun `categorias predeterminadas eliminadas no se vuelven a sembrar`() = runTest {
        val deletedIds = DefaultCategories.seeds.mapTo(mutableSetOf()) { it.id }
        val categories = FakeCategoryRepository(emptyList(), deletedIds)
        DashboardViewModel(
            FakeAccountRepository(Account("acc-1", "Cuenta", "BANK", 0L, "DOP")),
            FakeTransactionRepository(emptyList()),
            categories,
            FakeUserProfilePreferences(),
            FakeDebtRepository(),
            FakeGoalRepository()
        )

        advanceUntilIdle()

        assertEquals(emptyList<Category>(), categories.activeCategories)
    }

    // --- Fakes ---

    private class FakeAccountRepository(account: Account) : AccountRepository {
        private val accounts = MutableStateFlow(listOf(account))
        override fun getAccounts(): Flow<List<Account>> = accounts
        override suspend fun getAccount(id: String): Account? = accounts.value.firstOrNull { it.id == id }
        override suspend fun addAccount(account: Account) { accounts.value += account }
        override suspend fun updateAccount(account: Account) { accounts.value = accounts.value.map { if (it.id == account.id) account else it } }
        override suspend fun deleteAccount(id: String) { accounts.value = accounts.value.filterNot { it.id == id } }
    }

    private class FakeTransactionRepository(initial: List<Transaction>) : TransactionRepository {
        val transactions = MutableStateFlow(initial)
        override fun getTransactions(): Flow<List<Transaction>> = transactions
        override fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>> = transactions
        override suspend fun getTransaction(id: String): Transaction? = transactions.value.firstOrNull { it.id == id }

        override suspend fun getTransactionsForDebt(debtId: String): List<Transaction> =
            transactions.value.filter { it.debtId == debtId }
        override suspend fun addTransaction(transaction: Transaction) { transactions.value += transaction }
        override suspend fun addTransactionWithBalance(transaction: Transaction) { transactions.value += transaction }
        override suspend fun executeTransfer(fromAccountId: String, toAccountId: String, amount: Long, transaction: Transaction): Boolean {
            transactions.value += transaction
            return true
        }
        override suspend fun updateTransaction(transaction: Transaction) { transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it } }
        override suspend fun updateTransactionWithBalance(transaction: Transaction, oldAmount: Long) { transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it } }
        override suspend fun deleteTransaction(id: String) { transactions.value = transactions.value.filterNot { it.id == id } }
        override suspend fun deleteTransactionWithBalance(transaction: Transaction) { transactions.value = transactions.value.filterNot { it.id == transaction.id } }
    }

    private class FakeUserProfilePreferences : UserProfilePreferences {
        override val profile = MutableStateFlow(UserProfilePrefs())

        override suspend fun setBalancesHidden(hidden: Boolean) {
            profile.value = profile.value.copy(balancesHidden = hidden)
        }

        override suspend fun setDashboardCardIds(cardIds: Set<String>) {
            profile.value = profile.value.copy(dashboardCardIds = cardIds)
        }
    }

    private class FakeGoalRepository(initial: List<Goal> = emptyList()) : GoalRepository {
        private val goals = MutableStateFlow(initial)
        override fun getGoals(): Flow<List<Goal>> = goals
        override suspend fun getGoal(id: String): Goal? = goals.value.firstOrNull { it.id == id }
        override suspend fun addGoal(goal: Goal) { goals.value = goals.value + goal }
        override suspend fun updateGoal(goal: Goal) {
            goals.value = goals.value.map { if (it.id == goal.id) goal else it }
        }
        override suspend fun deleteGoal(id: String) { goals.value = goals.value.filterNot { it.id == id } }
    }

    private class FakeDebtRepository(initial: List<Debt> = emptyList()) : DebtRepository {
        private val debts = MutableStateFlow(initial)
        override fun getDebts(): Flow<List<Debt>> = debts
        override suspend fun getDebt(id: String): Debt? = debts.value.firstOrNull { it.id == id }
        override suspend fun addDebt(debt: Debt) { debts.value = debts.value + debt }
        override suspend fun updateDebt(debt: Debt) {
            debts.value = debts.value.map { if (it.id == debt.id) debt else it }
        }
        override suspend fun deleteDebt(id: String) {
            debts.value = debts.value.filterNot { it.id == id }
        }
    }

    private class FakeCategoryRepository(
        categories: List<Category>,
        allKnownIds: Set<String> = categories.mapTo(mutableSetOf()) { it.id }
    ) : CategoryRepository {
        private val state = MutableStateFlow(categories)
        private val allIds = allKnownIds.toMutableSet()
        val activeCategories: List<Category> get() = state.value
        override fun getCategories(): Flow<List<Category>> = state
        override suspend fun getCategory(id: String): Category? = state.value.firstOrNull { it.id == id }
        override suspend fun getAllCategoryIdsIncludingDeleted(): Set<String> = allIds
        override suspend fun addCategory(category: Category) {
            allIds += category.id
            state.value = state.value.filterNot { it.id == category.id } + category
        }
        override suspend fun deleteCategory(id: String) { state.value = state.value.filterNot { it.id == id } }
    }
}
