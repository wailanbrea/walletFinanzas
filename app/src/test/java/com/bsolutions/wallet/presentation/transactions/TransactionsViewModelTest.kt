package com.bsolutions.wallet.presentation.transactions

import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `adding an expense decreases account balance and stores transaction`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Efectivo", "CASH", 10_000L, "DOP"))
        val transactionRepository = FakeTransactionRepository()
        val viewModel = TransactionsViewModel(transactionRepository, accountRepository, FakeCategoryRepository())

        viewModel.addTransaction("account-1", 2_500L, "EXPENSE", "food", "Compra")
        advanceUntilIdle()

        assertEquals(7_500L, accountRepository.getAccount("account-1")!!.balance)
        assertEquals(1, transactionRepository.transactions.value.size)
        assertEquals(2_500L, transactionRepository.transactions.value.single().amount)
    }

    @Test
    fun `deleting an expense restores its effect on balance`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Efectivo", "CASH", 7_500L, "DOP"))
        val transactionRepository = FakeTransactionRepository()
        val transaction = Transaction("tx-1", "account-1", 2_500L, "EXPENSE", "food", 1L, "Compra")
        transactionRepository.addTransaction(transaction)
        val viewModel = TransactionsViewModel(transactionRepository, accountRepository, FakeCategoryRepository())

        viewModel.deleteTransaction(transaction)
        advanceUntilIdle()

        assertEquals(10_000L, accountRepository.getAccount("account-1")!!.balance)
        assertEquals(emptyList<Transaction>(), transactionRepository.transactions.value)
    }

    @Test
    fun `editing an expense applies only the net balance difference`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Efectivo", "CASH", 7_500L, "DOP"))
        val transactionRepository = FakeTransactionRepository()
        val original = Transaction("tx-1", "account-1", 2_500L, "EXPENSE", "food", 1L, "Compra")
        transactionRepository.addTransaction(original)
        val viewModel = TransactionsViewModel(transactionRepository, accountRepository, FakeCategoryRepository())

        viewModel.updateTransaction(original, newAmount = 4_000L, newCategoryId = "transport", newNote = "Taxi")
        advanceUntilIdle()

        assertEquals(6_000L, accountRepository.getAccount("account-1")!!.balance)
        assertEquals(4_000L, transactionRepository.transactions.value.single().amount)
        assertEquals("transport", transactionRepository.transactions.value.single().categoryId)
    }

    private class FakeAccountRepository(account: Account) : AccountRepository {
        private val accounts = MutableStateFlow(listOf(account))
        override fun getAccounts(): Flow<List<Account>> = accounts
        override suspend fun getAccount(id: String): Account? = accounts.value.firstOrNull { it.id == id }
        override suspend fun addAccount(account: Account) { accounts.value += account }
        override suspend fun updateAccount(account: Account) { accounts.value = accounts.value.map { if (it.id == account.id) account else it } }
        override suspend fun deleteAccount(id: String) { accounts.value = accounts.value.filterNot { it.id == id } }
    }

    private class FakeTransactionRepository : TransactionRepository {
        val transactions = MutableStateFlow<List<Transaction>>(emptyList())
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

    private class FakeCategoryRepository : CategoryRepository {
        override fun getCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun getCategory(id: String): Category? = null
        override suspend fun addCategory(category: Category) = Unit
        override suspend fun deleteCategory(id: String) = Unit
    }
}
