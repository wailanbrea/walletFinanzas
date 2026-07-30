package com.bsolutions.wallet.presentation.emailconnections

import com.bsolutions.wallet.data.repository.EmailConnection
import com.bsolutions.wallet.data.repository.EmailConnectionStatus
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import com.bsolutions.wallet.data.repository.EmailCandidate
import com.bsolutions.wallet.data.repository.EmailProvider
import com.bsolutions.wallet.data.repository.EmailSyncResult
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailConnectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load exposes content returned by backend`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()))
        val viewModel = createViewModel(repository)

        advanceUntilIdle()

        assertEquals(EmailConnectionsPhase.CONTENT, viewModel.uiState.value.phase)
        assertEquals("ana@example.com", viewModel.uiState.value.connections.single().email)
    }

    @Test
    fun `load distinguishes empty and error states`() = runTest {
        val emptyViewModel = createViewModel(FakeRepository())
        advanceUntilIdle()
        assertEquals(EmailConnectionsPhase.EMPTY, emptyViewModel.uiState.value.phase)

        val errorViewModel = createViewModel(FakeRepository(loadError = true))
        advanceUntilIdle()
        assertEquals(EmailConnectionsPhase.ERROR, errorViewModel.uiState.value.phase)
        assertEquals("No se pudieron cargar las conexiones de correo.", errorViewModel.uiState.value.message)
    }

    @Test
    fun `connect emits one authorization URL and consume clears it`() = runTest {
        val viewModel = createViewModel(FakeRepository(authorizationUrl = "https://login.example/oauth"))
        advanceUntilIdle()

        viewModel.connect(EmailProvider.GMAIL)
        advanceUntilIdle()

        assertEquals("https://login.example/oauth", viewModel.uiState.value.authorizationUrl)
        viewModel.consumeAuthorizationUrl()
        assertNull(viewModel.uiState.value.authorizationUrl)
    }

    @Test
    fun `disconnect refreshes connections after backend confirms`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.disconnect(EmailProvider.GMAIL)
        advanceUntilIdle()

        assertEquals(listOf(EmailProvider.GMAIL), repository.disconnected)
        assertEquals(EmailConnectionsPhase.EMPTY, viewModel.uiState.value.phase)
    }

    @Test
    fun `returning from authorization refreshes backend state`() = runTest {
        val repository = FakeRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        repository.connections = listOf(gmailConnected())

        viewModel.onAuthorizationReturn()
        advanceUntilIdle()

        assertEquals(2, repository.loadCount)
        assertEquals(EmailConnectionsPhase.CONTENT, viewModel.uiState.value.phase)
    }

    @Test
    fun `manual sync refreshes classified candidates and exposes counts`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        repository.candidates = listOf(financialCandidate())

        viewModel.sync(EmailProvider.GMAIL)
        advanceUntilIdle()

        assertEquals(listOf(EmailProvider.GMAIL), repository.synced)
        assertEquals(1, viewModel.uiState.value.candidates.size)
        assertEquals(1, viewModel.uiState.value.syncResult?.candidatesCreated)
        assertNull(viewModel.uiState.value.actionProvider)
    }

    @Test
    fun `candidates are separated by Gmail and Microsoft`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected())).apply {
            candidates = listOf(
                financialCandidate("gmail-1", EmailProvider.GMAIL),
                financialCandidate("microsoft-1", EmailProvider.MICROSOFT)
            )
        }
        val viewModel = createViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf("gmail-1"), viewModel.uiState.value.candidatesByProvider[EmailProvider.GMAIL]?.map { it.id })
        assertEquals(listOf("microsoft-1"), viewModel.uiState.value.candidatesByProvider[EmailProvider.MICROSOFT]?.map { it.id })
    }

    @Test
    fun `duplicate candidates are hidden and can be marked manually`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected())).apply {
            candidates = listOf(
                financialCandidate("pending"),
                financialCandidate("already-duplicate").copy(status = "duplicate")
            )
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("pending"), viewModel.uiState.value.candidates.map { it.id })

        viewModel.markDuplicate("pending")
        advanceUntilIdle()

        assertEquals(Triple("pending", "duplicate", null), repository.reviews.single())
        assertEquals(emptyList<EmailCandidate>(), viewModel.uiState.value.candidates)
        assertEquals("Movimiento marcado como duplicado.", viewModel.uiState.value.message)
    }

    @Test
    fun `classifying candidate calls backend and removes reviewed item`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected())).apply {
            candidates = listOf(financialCandidate())
        }
        val transactions = FakeTransactionRepository()
        val viewModel = createViewModel(repository, transactionRepository = transactions)
        advanceUntilIdle()

        viewModel.classify("candidate-1", "account-1", "cat_alimentacion")
        advanceUntilIdle()

        assertEquals(Triple("candidate-1", "categorize", "Alimentación"), repository.reviews.single())
        assertEquals(
            Transaction(
                id = "email_candidate-1",
                accountId = "account-1",
                amount = 325000,
                type = "EXPENSE",
                categoryId = "cat_alimentacion",
                date = 1784557800000,
                note = "Supermercado Nacional",
                currency = "DOP"
            ),
            transactions.added.single()
        )
        assertEquals(emptyList<EmailCandidate>(), viewModel.uiState.value.candidates)
    }

    @Test
    fun `retry after backend failure does not duplicate local movement`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()), reviewError = true).apply {
            candidates = listOf(financialCandidate())
        }
        val transactions = FakeTransactionRepository()
        val viewModel = createViewModel(repository, transactionRepository = transactions)
        advanceUntilIdle()

        viewModel.classify("candidate-1", "account-1", "cat_alimentacion")
        advanceUntilIdle()
        assertEquals(
            "El movimiento ya fue agregado, pero no se pudo confirmar el correo. Reintenta para finalizar.",
            viewModel.uiState.value.message
        )
        repository.reviewError = false
        viewModel.classify("candidate-1", "account-1", "cat_alimentacion")
        advanceUntilIdle()

        assertEquals(1, transactions.added.size)
        assertEquals(emptyList<EmailCandidate>(), viewModel.uiState.value.candidates)
    }

    @Test
    fun `retry preserves the original account even if another account is supplied`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()), reviewError = true).apply {
            candidates = listOf(financialCandidate())
        }
        val transactions = FakeTransactionRepository()
        val viewModel = createViewModel(repository, transactionRepository = transactions)
        advanceUntilIdle()

        viewModel.classify("candidate-1", "account-1", "cat_alimentacion")
        advanceUntilIdle()
        repository.reviewError = false
        viewModel.classify("candidate-1", "account-2", "cat_alimentacion")
        advanceUntilIdle()

        assertEquals(1, transactions.added.size)
        assertEquals("account-1", transactions.added.single().accountId)
        assertEquals(Triple("candidate-1", "categorize", "Alimentación"), repository.reviews.single())
        assertEquals(emptyList<EmailCandidate>(), viewModel.uiState.value.candidates)
    }

    @Test
    fun `restart restores booked account and category from local transaction`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected())).apply {
            candidates = listOf(financialCandidate())
        }
        val existing = Transaction(
            id = "email_candidate-1",
            accountId = "account-2",
            amount = 325000,
            type = "EXPENSE",
            categoryId = "cat_alimentacion",
            date = 1784557800000,
            note = "Supermercado Nacional",
            currency = "DOP"
        )
        val transactions = FakeTransactionRepository(listOf(existing))

        val viewModel = createViewModel(repository, transactionRepository = transactions)
        advanceUntilIdle()

        assertEquals(existing, viewModel.uiState.value.bookedCandidates["candidate-1"])
        viewModel.classify("candidate-1", "account-2", "cat_alimentacion")
        advanceUntilIdle()
        assertEquals(emptyList<Transaction>(), transactions.added)
        assertEquals(emptyList<EmailCandidate>(), viewModel.uiState.value.candidates)
    }

    @Test
    fun `candidate with committed movement cannot be dismissed after review failure`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()), reviewError = true).apply {
            candidates = listOf(financialCandidate())
        }
        val transactions = FakeTransactionRepository()
        val viewModel = createViewModel(repository, transactionRepository = transactions)
        advanceUntilIdle()

        viewModel.classify("candidate-1", "account-1", "cat_alimentacion")
        advanceUntilIdle()
        viewModel.dismiss("candidate-1")
        advanceUntilIdle()

        assertEquals(emptyList<Triple<String, String, String?>>(), repository.reviews)
        assertEquals(
            "Este movimiento ya fue agregado. Clasifícalo para completar la confirmación.",
            viewModel.uiState.value.message
        )
    }

    @Test
    fun `converted amount is required when account currency differs`() {
        val account = Account("account-1", "Cuenta", "BANK", 0, "DOP")

        assertNull(candidateAmountForAccount(financialCandidate().copy(currency = "USD"), account))
        assertEquals(
            18_500L,
            candidateAmountForAccount(
                financialCandidate().copy(currency = "USD", convertedAmount = 18_500, convertedCurrency = "DOP"),
                account
            )
        )
        assertNull(candidateAmountForAccount(financialCandidate().copy(amount = 0), account))
        assertNull(candidateAmountForAccount(financialCandidate().copy(amount = Long.MIN_VALUE), account))
        assertNull(candidateOccurredAtMillis("not-a-date"))
    }

    private fun gmailConnected() = EmailConnection(
        provider = EmailProvider.GMAIL,
        displayName = "Gmail",
        status = EmailConnectionStatus.CONNECTED,
        email = "ana@example.com",
        configurationReady = true,
        connectedAt = "2026-07-20T10:15:00Z",
        expiresAt = null
    )

    private fun createViewModel(
        repository: EmailConnectionsRepository,
        accountRepository: AccountRepository = FakeAccountRepository(),
        transactionRepository: TransactionRepository = FakeTransactionRepository()
    ) = EmailConnectionsViewModel(repository, FakeCategoryRepository(), accountRepository, transactionRepository)

    private fun financialCandidate(
        id: String = "candidate-1",
        provider: EmailProvider = EmailProvider.GMAIL
    ) = EmailCandidate(
        id = id,
        provider = provider,
        merchant = "Supermercado Nacional",
        amount = -325000,
        currency = "DOP",
        direction = "expense",
        categorySuggestion = "Alimentación",
        occurredAt = "2026-07-20T14:30:00Z",
        confidence = 90,
        status = "pending",
        subject = "Compra aprobada"
    )

    private class FakeRepository(
        var connections: List<EmailConnection> = emptyList(),
        private val authorizationUrl: String = "https://example.test/oauth",
        private val loadError: Boolean = false,
        var reviewError: Boolean = false
    ) : EmailConnectionsRepository {
        var loadCount = 0
        val disconnected = mutableListOf<EmailProvider>()
        val synced = mutableListOf<EmailProvider>()
        val reviews = mutableListOf<Triple<String, String, String?>>()
        var candidates: List<EmailCandidate> = emptyList()

        override suspend fun getConnections(): List<EmailConnection> {
            loadCount++
            if (loadError) error("network")
            return connections
        }

        override suspend fun getAuthorizationUrl(provider: EmailProvider): String = authorizationUrl

        override suspend fun getCandidates(): List<EmailCandidate> = candidates

        override suspend fun sync(provider: EmailProvider): EmailSyncResult {
            synced += provider
            return EmailSyncResult(messagesDiscovered = 1, messagesCreated = 1, candidatesCreated = 1)
        }

        override suspend fun reviewCandidate(id: String, action: String, category: String?): EmailCandidate {
            if (reviewError) error("network")
            reviews += Triple(id, action, category)
            val current = candidates.first { it.id == id }
            val reviewed = current.copy(
                status = if (action == "dismiss") "dismissed" else "classified",
                categorySuggestion = category ?: current.categorySuggestion
            )
            candidates = candidates.filterNot { it.id == id }
            return reviewed
        }

        override suspend fun disconnect(provider: EmailProvider) {
            disconnected += provider
            connections = connections.filterNot { it.provider == provider }
        }
    }

    private class FakeAccountRepository : AccountRepository {
        private val accounts = MutableStateFlow(
            listOf(
                Account("account-1", "Cuenta principal", "BANK", 500_000, "DOP"),
                Account("account-2", "Cuenta secundaria", "BANK", 200_000, "DOP")
            )
        )

        override fun getAccounts(): Flow<List<Account>> = accounts
        override suspend fun getAccount(id: String): Account? = accounts.value.firstOrNull { it.id == id }
        override suspend fun addAccount(account: Account) {
            accounts.value += account
        }
        override suspend fun updateAccount(account: Account) {
            accounts.value = accounts.value.filterNot { it.id == account.id } + account
        }
        override suspend fun deleteAccount(id: String) {
            accounts.value = accounts.value.filterNot { it.id == id }
        }
    }

    private class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
        private val transactions = MutableStateFlow(initial)
        val added = mutableListOf<Transaction>()

        override fun getTransactions(): Flow<List<Transaction>> = transactions
        override fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>> = transactions
        override suspend fun getTransaction(id: String): Transaction? = transactions.value.firstOrNull { it.id == id }
        override suspend fun addTransaction(transaction: Transaction) {
            transactions.value += transaction
        }
        override suspend fun addTransactionWithBalance(transaction: Transaction) {
            if (transactions.value.none { it.id == transaction.id }) {
                transactions.value += transaction
                added += transaction
            }
        }
        override suspend fun executeTransfer(
            fromAccountId: String,
            toAccountId: String,
            amount: Long,
            transaction: Transaction
        ) = false
        override suspend fun updateTransaction(transaction: Transaction) = Unit
        override suspend fun updateTransactionWithBalance(transaction: Transaction, oldAmount: Long) = Unit
        override suspend fun deleteTransaction(id: String) = Unit
        override suspend fun deleteTransactionWithBalance(transaction: Transaction) = Unit
    }

    private class FakeCategoryRepository : CategoryRepository {
        private val categories = MutableStateFlow(
            listOf(Category("cat_alimentacion", "Alimentación", "restaurant", "#E57373"))
        )

        override fun getCategories(): Flow<List<Category>> = categories
        override suspend fun getCategory(id: String): Category? = categories.value.firstOrNull { it.id == id }
        override suspend fun getAllCategoryIdsIncludingDeleted(): Set<String> = categories.value.mapTo(mutableSetOf()) { it.id }
        override suspend fun addCategory(category: Category) {
            categories.value = categories.value.filterNot { it.id == category.id } + category
        }
        override suspend fun deleteCategory(id: String) {
            categories.value = categories.value.filterNot { it.id == id }
        }
    }
}
