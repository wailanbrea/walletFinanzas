package com.bsolutions.wallet.presentation.transactions

import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.usecase.DebtLedger
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
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val viewModel = createViewModel(transactionRepository, accountRepository)

        viewModel.addTransaction("account-1", 2_500L, "EXPENSE", "food", "Compra")
        advanceUntilIdle()

        assertEquals(7_500L, accountRepository.getAccount("account-1")!!.balance)
        assertEquals(1, transactionRepository.transactions.value.size)
        assertEquals(2_500L, transactionRepository.transactions.value.single().amount)
    }

    @Test
    fun `deleting an expense restores its effect on balance`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Efectivo", "CASH", 7_500L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val transaction = Transaction("tx-1", "account-1", 2_500L, "EXPENSE", "food", 1L, "Compra")
        transactionRepository.addTransaction(transaction)
        val viewModel = createViewModel(transactionRepository, accountRepository)

        viewModel.deleteTransaction(transaction)
        advanceUntilIdle()

        assertEquals(10_000L, accountRepository.getAccount("account-1")!!.balance)
        assertEquals(emptyList<Transaction>(), transactionRepository.transactions.value)
    }

    @Test
    fun `editing an expense applies only the net balance difference`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Efectivo", "CASH", 7_500L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val original = Transaction("tx-1", "account-1", 2_500L, "EXPENSE", "food", 1L, "Compra")
        transactionRepository.addTransaction(original)
        val viewModel = createViewModel(transactionRepository, accountRepository)

        viewModel.updateTransaction(original, newAmount = 4_000L, newCategoryId = "transport", newNote = "Taxi")
        advanceUntilIdle()

        assertEquals(6_000L, accountRepository.getAccount("account-1")!!.balance)
        assertEquals(4_000L, transactionRepository.transactions.value.single().amount)
        assertEquals("transport", transactionRepository.transactions.value.single().categoryId)
    }

    @Test
    fun `lending money opens a receivable without touching the amount or the account`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Popular", "BANK", 100_000L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        // El caso real: la compra por Amazon para un amigo.
        val purchase = Transaction("tx-1", "account-1", 2_186_799L, "EXPENSE", "cat_otros", 1L, "PayPal")
        transactionRepository.addTransaction(purchase)
        val debtRepository = FakeDebtRepository()
        val viewModel = createViewModel(transactionRepository, accountRepository, debtRepository)

        viewModel.markAsLoan(purchase, "Pedro")
        advanceUntilIdle()

        val debt = debtRepository.debts.value.single()
        assertEquals("Pedro", debt.name)
        assertEquals("OWED_TO_ME", debt.direction)
        assertEquals(2_186_799L, debt.totalAmount)
        assertEquals(2_186_799L, debt.remainingAmount)

        val linked = transactionRepository.transactions.value.single()
        assertEquals(debt.id, linked.debtId)
        // No es consumo: pasa a la categoria de prestamos.
        assertEquals("cat_prestamos_terceros", linked.categoryId)
        // El dinero ya habia salido: el saldo no se toca otra vez.
        assertEquals(100_000L, accountRepository.getAccount("account-1")!!.balance)
    }

    @Test
    fun `an income applied to the debt reduces what is owed and closes it when complete`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Popular", "BANK", 0L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val purchase = Transaction("tx-1", "account-1", 20_000L, "EXPENSE", "cat_otros", 1L, "PayPal")
        transactionRepository.addTransaction(purchase)
        val debtRepository = FakeDebtRepository()
        val viewModel = createViewModel(transactionRepository, accountRepository, debtRepository)
        viewModel.markAsLoan(purchase, "Pedro")
        advanceUntilIdle()
        val debtId = debtRepository.debts.value.single().id

        // Primer abono: la mitad.
        val first = Transaction("tx-2", "account-1", 12_000L, "INCOME", "cat_otros", 2L, "Me pago Pedro")
        transactionRepository.addTransaction(first)
        viewModel.applyToDebt(first, debtId)
        advanceUntilIdle()
        assertEquals(12_000L, debtRepository.debts.value.single().paidAmount)
        assertEquals(8_000L, debtRepository.debts.value.single().remainingAmount)
        assertEquals(false, debtRepository.debts.value.single().isClosed)

        // Resto: la deuda se cierra sola.
        val second = Transaction("tx-3", "account-1", 8_000L, "INCOME", "cat_otros", 3L, "Resto")
        transactionRepository.addTransaction(second)
        viewModel.applyToDebt(second, debtId)
        advanceUntilIdle()
        assertEquals(20_000L, debtRepository.debts.value.single().paidAmount)
        assertEquals(true, debtRepository.debts.value.single().isClosed)
    }

    @Test
    fun `deleting a payment gives the debt back what is still owed`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Popular", "BANK", 0L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val purchase = Transaction("tx-1", "account-1", 20_000L, "EXPENSE", "cat_otros", 1L, "PayPal")
        transactionRepository.addTransaction(purchase)
        val debtRepository = FakeDebtRepository()
        val viewModel = createViewModel(transactionRepository, accountRepository, debtRepository)
        viewModel.markAsLoan(purchase, "Pedro")
        advanceUntilIdle()
        val debtId = debtRepository.debts.value.single().id

        val payment = Transaction("tx-2", "account-1", 20_000L, "INCOME", "cat_otros", 2L, "Pago")
        transactionRepository.addTransaction(payment)
        viewModel.applyToDebt(payment, debtId)
        advanceUntilIdle()
        assertEquals(true, debtRepository.debts.value.single().isClosed)

        // Se borra el abono: lo cobrado se recalcula de los movimientos reales.
        viewModel.deleteTransaction(transactionRepository.getTransaction("tx-2")!!)
        advanceUntilIdle()

        assertEquals(0L, debtRepository.debts.value.single().paidAmount)
        assertEquals(20_000L, debtRepository.debts.value.single().remainingAmount)
        assertEquals(false, debtRepository.debts.value.single().isClosed)
    }

    @Test
    fun `a new charge for the same thing grows the existing debt`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Popular", "BANK", 100_000L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val purchase = Transaction("tx-1", "account-1", 20_000L, "EXPENSE", "cat_otros", 1L, "La mica")
        transactionRepository.addTransaction(purchase)
        val debtRepository = FakeDebtRepository()
        val viewModel = createViewModel(transactionRepository, accountRepository, debtRepository)
        viewModel.markAsLoan(purchase, "David")
        advanceUntilIdle()
        val debtId = debtRepository.debts.value.single().id

        // El currier de esa misma mica: no abre otra deuda, engorda la que hay.
        val courier = Transaction("tx-2", "account-1", 5_000L, "EXPENSE", "cat_otros", 2L, "Currier")
        transactionRepository.addTransaction(courier)
        viewModel.applyToDebt(courier, debtId)
        advanceUntilIdle()

        val debt = debtRepository.debts.value.single()
        assertEquals(25_000L, debt.totalAmount)
        assertEquals(25_000L, debt.remainingAmount)
        assertEquals(0L, debt.paidAmount)
        assertEquals(false, debt.isClosed)
    }

    @Test
    fun `linking to a hand made debt keeps what was already written by hand`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Popular", "BANK", 100_000L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val debtRepository = FakeDebtRepository()
        // Como la deuda de Samuel: 600 en total y 380.01 abonados a mano, sin ningun
        // movimiento detras. Recalcular desde los movimientos la dejaria en cero.
        debtRepository.addDebt(
            Debt("d-1", "Samuel", "Cena", "OWED_TO_ME", 60_000L, 38_001L, null, false)
        )
        val viewModel = createViewModel(transactionRepository, accountRepository, debtRepository)

        val extra = Transaction("tx-9", "account-1", 10_000L, "EXPENSE", "cat_otros", 5L, "Otro gasto")
        transactionRepository.addTransaction(extra)
        viewModel.applyToDebt(extra, "d-1")
        advanceUntilIdle()

        val debt = debtRepository.debts.value.single()
        assertEquals(70_000L, debt.totalAmount)
        // Lo abonado a mano sobrevive.
        assertEquals(38_001L, debt.paidAmount)
    }

    @Test
    fun `unlinking a charge takes it back off the debt`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Popular", "BANK", 100_000L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val purchase = Transaction("tx-1", "account-1", 20_000L, "EXPENSE", "cat_otros", 1L, "La mica")
        transactionRepository.addTransaction(purchase)
        val debtRepository = FakeDebtRepository()
        val viewModel = createViewModel(transactionRepository, accountRepository, debtRepository)
        viewModel.markAsLoan(purchase, "David")
        advanceUntilIdle()
        val debtId = debtRepository.debts.value.single().id

        val courier = Transaction("tx-2", "account-1", 5_000L, "EXPENSE", "cat_otros", 2L, "Currier")
        transactionRepository.addTransaction(courier)
        viewModel.applyToDebt(courier, debtId)
        advanceUntilIdle()
        assertEquals(25_000L, debtRepository.debts.value.single().totalAmount)

        viewModel.unlinkFromDebt(transactionRepository.getTransaction("tx-2")!!)
        advanceUntilIdle()

        assertEquals(20_000L, debtRepository.debts.value.single().totalAmount)
    }

    @Test
    fun `editing a charge moves the debt by the difference only`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Popular", "BANK", 100_000L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val purchase = Transaction("tx-1", "account-1", 20_000L, "EXPENSE", "cat_otros", 1L, "La mica")
        transactionRepository.addTransaction(purchase)
        val debtRepository = FakeDebtRepository()
        val viewModel = createViewModel(transactionRepository, accountRepository, debtRepository)
        viewModel.markAsLoan(purchase, "David")
        advanceUntilIdle()

        // El currier salio mas caro de lo anotado: 20.000 -> 23.000, no 43.000.
        val linked = transactionRepository.getTransaction("tx-1")!!
        viewModel.updateTransaction(linked, newAmount = 23_000L, newCategoryId = "", newNote = "La mica")
        advanceUntilIdle()

        assertEquals(23_000L, debtRepository.debts.value.single().totalAmount)
    }

    @Test
    fun `the loan expense itself is never counted as a payment`() = runTest {
        val accountRepository = FakeAccountRepository(Account("account-1", "Popular", "BANK", 0L, "DOP"))
        val transactionRepository = FakeTransactionRepository(accountRepository)
        val purchase = Transaction("tx-1", "account-1", 20_000L, "EXPENSE", "cat_otros", 1L, "PayPal")
        transactionRepository.addTransaction(purchase)
        val debtRepository = FakeDebtRepository()
        val viewModel = createViewModel(transactionRepository, accountRepository, debtRepository)

        viewModel.markAsLoan(purchase, "Pedro")
        advanceUntilIdle()

        // El gasto que origino la deuda tambien esta atado a ella, pero va en la
        // direccion contraria: si contara como abono, la deuda nacería pagada.
        assertEquals(0L, debtRepository.debts.value.single().paidAmount)
    }

    private class FakeAccountRepository(account: Account) : AccountRepository {
        private val accounts = MutableStateFlow(listOf(account))
        override fun getAccounts(): Flow<List<Account>> = accounts
        override suspend fun getAccount(id: String): Account? = accounts.value.firstOrNull { it.id == id }
        override suspend fun addAccount(account: Account) { accounts.value += account }
        override suspend fun updateAccount(account: Account) { accounts.value = accounts.value.map { if (it.id == account.id) account else it } }
        override suspend fun deleteAccount(id: String): List<Transaction> {
            accounts.value = accounts.value.filterNot { it.id == id }

            return emptyList()
        }
        /** Simula el ajuste de saldo que el DAO hace dentro de la transacción atómica. */
        fun adjustBalance(accountId: String, delta: Long) {
            accounts.value = accounts.value.map { if (it.id == accountId) it.copy(balance = it.balance + delta) else it }
        }
    }

    private class FakeTransactionRepository(private val accounts: FakeAccountRepository) : TransactionRepository {
        val transactions = MutableStateFlow<List<Transaction>>(emptyList())
        override fun getTransactions(): Flow<List<Transaction>> = transactions
        override fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>> = transactions
        override suspend fun getTransaction(id: String): Transaction? = transactions.value.firstOrNull { it.id == id }

        override suspend fun getTransactionsForDebt(debtId: String): List<Transaction> =
            transactions.value.filter { it.debtId == debtId }
        override suspend fun addTransaction(transaction: Transaction) { transactions.value += transaction }
        override suspend fun addTransactionWithBalance(transaction: Transaction) {
            transactions.value += transaction
            accounts.adjustBalance(transaction.accountId, if (transaction.type == "INCOME") transaction.amount else -transaction.amount)
        }
        override suspend fun updateTransaction(transaction: Transaction) { transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it } }
        override suspend fun updateTransactionWithBalance(transaction: Transaction, oldAmount: Long) {
            val diff = transaction.amount - oldAmount
            accounts.adjustBalance(transaction.accountId, if (transaction.type == "INCOME") diff else -diff)
            transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it }
        }
        override suspend fun deleteTransaction(id: String) { transactions.value = transactions.value.filterNot { it.id == id } }
        override suspend fun deleteTransactionWithBalance(transaction: Transaction) {
            accounts.adjustBalance(transaction.accountId, if (transaction.type == "INCOME") -transaction.amount else transaction.amount)
            transactions.value = transactions.value.filterNot { it.id == transaction.id }
        }
    }

    private class FakeCategoryRepository : CategoryRepository {
        private val categories = MutableStateFlow(
            listOf(
                Category("food", "Alimentación", "restaurant", "#1B873F"),
                Category("transport", "Transporte", "directions_car", "#C62828")
            )
        )
        override fun getCategories(): Flow<List<Category>> = categories
        override suspend fun getCategory(id: String): Category? = categories.value.firstOrNull { it.id == id }
        override suspend fun getAllCategoryIdsIncludingDeleted(): Set<String> = categories.value.mapTo(mutableSetOf()) { it.id }
        override suspend fun addCategory(category: Category) = Unit
        override suspend fun deleteCategory(id: String) = Unit
    }

    private class FakeDebtRepository : DebtRepository {
        val debts = MutableStateFlow<List<Debt>>(emptyList())
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

    private fun createViewModel(
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        debtRepository: DebtRepository = FakeDebtRepository()
    ) = TransactionsViewModel(
        transactionRepository,
        accountRepository,
        FakeCategoryRepository(),
        debtRepository,
        DebtLedger(transactionRepository, debtRepository)
    )
}
