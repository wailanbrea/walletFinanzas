package com.bsolutions.wallet.presentation.budgets

import com.bsolutions.wallet.domain.model.Budget
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.BudgetRepository
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
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Tests de presupuestos: el gasto se limita al mes en curso y el diálogo de
 * creación solo ofrece categorías sin presupuesto. Siempre se parte de
 * presupuestos NO vacíos para no disparar el seed del init.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun thisMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 10.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
    }.timeInMillis

    private fun lastMonth(): Long = Calendar.getInstance().apply {
        add(Calendar.MONTH, -1)
        set(Calendar.DAY_OF_MONTH, 10.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
    }.timeInMillis

    private val foodCategory = Category("food", "Alimentación", "restaurant", "#1B873F")
    private val transportCategory = Category("transport", "Transporte", "directions_car", "#C62828")

    private suspend fun kotlinx.coroutines.test.TestScope.awaitState(viewModel: BudgetsViewModel): BudgetsUiState {
        val job = backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val state = viewModel.uiState.value
        job.cancel()
        return state
    }

    @Test
    fun `el gasto del presupuesto solo suma el mes en curso`() = runTest {
        val budgets = FakeBudgetRepository(listOf(Budget("b1", "food", 20_000L, 0L, "MONTHLY")))
        val transactions = FakeTransactionRepository(
            listOf(
                Transaction("1", "acc", 5_000L, "EXPENSE", "food", thisMonth(), ""),
                Transaction("2", "acc", 90_000L, "EXPENSE", "food", lastMonth(), ""), // fuera
                Transaction("3", "acc", 7_000L, "INCOME", "food", thisMonth(), "") // ingreso: fuera
            )
        )
        val viewModel = BudgetsViewModel(budgets, FakeCategoryRepository(listOf(foodCategory)), transactions)

        val state = awaitState(viewModel)

        assertEquals(5_000L, state.budgetItems.single().spentAmount)
        assertEquals(20_000L, state.totalLimit)
        assertEquals(5_000L, state.totalSpent)
    }

    @Test
    fun `solo ofrece categorias sin presupuesto para crear uno nuevo`() = runTest {
        val budgets = FakeBudgetRepository(listOf(Budget("b1", "food", 20_000L, 0L, "MONTHLY")))
        val viewModel = BudgetsViewModel(
            budgets,
            FakeCategoryRepository(listOf(foodCategory, transportCategory)),
            FakeTransactionRepository(emptyList())
        )

        val state = awaitState(viewModel)

        assertEquals(listOf("transport"), state.availableCategories.map { it.id })
    }

    @Test
    fun `actualizar limite conserva la categoria y elimina funciona`() = runTest {
        val budgets = FakeBudgetRepository(listOf(Budget("b1", "food", 20_000L, 0L, "MONTHLY")))
        val viewModel = BudgetsViewModel(
            budgets,
            FakeCategoryRepository(listOf(foodCategory)),
            FakeTransactionRepository(emptyList())
        )

        viewModel.updateBudgetLimit("b1", "food", 35_000L)
        advanceUntilIdle()
        assertEquals(35_000L, budgets.budgets.value.single().limitAmount)
        assertEquals("food", budgets.budgets.value.single().categoryId)

        viewModel.deleteBudget("b1")
        advanceUntilIdle()
        assertEquals(0, budgets.budgets.value.size)
    }

    @Test
    fun `presupuesto de categoria eliminada sigue visible y cuenta en totales`() = runTest {
        val budgets = FakeBudgetRepository(listOf(Budget("b1", "deleted", 20_000L, 0L, "MONTHLY")))
        val transactions = FakeTransactionRepository(
            listOf(Transaction("1", "acc", 5_000L, "EXPENSE", "deleted", thisMonth(), ""))
        )
        val viewModel = BudgetsViewModel(budgets, FakeCategoryRepository(emptyList()), transactions)

        val state = awaitState(viewModel)

        assertEquals(1, state.budgetItems.size)
        assertEquals("deleted", state.budgetItems.single().category.id)
        assertEquals("Categoría eliminada", state.budgetItems.single().category.name)
        assertEquals(20_000L, state.totalLimit)
        assertEquals(5_000L, state.totalSpent)
    }

    // --- Fakes ---

    private class FakeBudgetRepository(initial: List<Budget>) : BudgetRepository {
        val budgets = MutableStateFlow(initial)
        override fun getBudgets(): Flow<List<Budget>> = budgets
        override suspend fun getBudgetByCategory(categoryId: String): Budget? =
            budgets.value.firstOrNull { it.categoryId == categoryId }
        override suspend fun addBudget(budget: Budget) {
            // Refleja el REPLACE del DAO: sustituye por id o inserta
            budgets.value = budgets.value.filterNot { it.id == budget.id } + budget
        }
        override suspend fun updateBudget(budget: Budget) {
            budgets.value = budgets.value.map { if (it.id == budget.id) budget else it }
        }
        override suspend fun deleteBudget(id: String) {
            budgets.value = budgets.value.filterNot { it.id == id }
        }
    }

    private class FakeTransactionRepository(initial: List<Transaction>) : TransactionRepository {
        private val transactions = MutableStateFlow(initial)
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

    private class FakeCategoryRepository(categories: List<Category>) : CategoryRepository {
        private val state = MutableStateFlow(categories)
        override fun getCategories(): Flow<List<Category>> = state
        override suspend fun getCategory(id: String): Category? = state.value.firstOrNull { it.id == id }
        override suspend fun getAllCategoryIdsIncludingDeleted(): Set<String> = state.value.mapTo(mutableSetOf()) { it.id }
        override suspend fun addCategory(category: Category) { state.value += category }
        override suspend fun deleteCategory(id: String) { state.value = state.value.filterNot { it.id == id } }
    }
}
