package com.bsolutions.wallet.presentation.dashboard

import com.bsolutions.wallet.data.preferences.UserProfilePrefs
import com.bsolutions.wallet.data.preferences.UserProfilePreferences
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction

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
        categories: List<Category> = listOf(Category("food", "Alimentación", "restaurant", "#1B873F"))
    ): DashboardViewModel = DashboardViewModel(
        FakeAccountRepository(Account("acc-1", "Cuenta", "BANK", 50_000L, "DOP")),
        FakeTransactionRepository(transactions),
        FakeCategoryRepository(categories),
        FakeUserProfilePreferences()
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
        override suspend fun addTransaction(transaction: Transaction) { transactions.value += transaction }
        override suspend fun executeTransfer(fromAccountId: String, toAccountId: String, amount: Long, transaction: Transaction): Boolean {
            transactions.value += transaction
            return true
        }
        override suspend fun updateTransaction(transaction: Transaction) { transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it } }
        override suspend fun deleteTransaction(id: String) { transactions.value = transactions.value.filterNot { it.id == id } }
    }

    private class FakeUserProfilePreferences : UserProfilePreferences {
        override val profile = MutableStateFlow(UserProfilePrefs())

        override suspend fun setBalancesHidden(hidden: Boolean) {
            profile.value = profile.value.copy(balancesHidden = hidden)
        }
    }

    private class FakeCategoryRepository(categories: List<Category>) : CategoryRepository {
        private val state = MutableStateFlow(categories)
        override fun getCategories(): Flow<List<Category>> = state
        override suspend fun getCategory(id: String): Category? = state.value.firstOrNull { it.id == id }
        override suspend fun addCategory(category: Category) { state.value += category }
        override suspend fun deleteCategory(id: String) { state.value = state.value.filterNot { it.id == id } }
    }
}
