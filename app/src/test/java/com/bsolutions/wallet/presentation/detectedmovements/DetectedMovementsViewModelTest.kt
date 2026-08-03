package com.bsolutions.wallet.presentation.detectedmovements

import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.core.notifications.ParsedBankNotice
import com.bsolutions.wallet.data.local.dao.DetectedMovementDao
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import com.bsolutions.wallet.data.repository.AuthUser
import com.bsolutions.wallet.data.repository.DetectedMovementRepository
import com.bsolutions.wallet.data.repository.EmailCandidate
import com.bsolutions.wallet.data.repository.EmailConnection
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import com.bsolutions.wallet.data.repository.EmailProvider
import com.bsolutions.wallet.data.repository.EmailSyncResult
import com.bsolutions.wallet.data.repository.WalletSessionStore
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetectedMovementsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `push and email are presented as one group with two evidences`() = runTest {
        val fixture = Fixture()
        fixture.ingestStrongEmailAndPush()
        fixture.ingestStrongEmailAndPush()

        val viewModel = fixture.viewModel()
        advanceUntilIdle()

        assertEquals(DetectedMovementsPhase.CONTENT, viewModel.uiState.value.phase)
        val group = viewModel.uiState.value.groups.single()
        assertEquals("notification:notice-1", group.root.id)
        assertEquals(2, group.evidence.size)
        assertEquals(setOf("EMAIL_GMAIL", "BANK_NOTIFICATION"), group.evidence.mapTo(mutableSetOf()) { it.source })
    }

    @Test
    fun `today is default and date filters use occurredAt calendar dates`() = runTest {
        val fixture = Fixture().apply {
            nowMillis = Instant.parse("2026-08-20T18:00:00Z").toEpochMilli()
            seedPending("today", "2026-08-20T16:00:00Z")
            seedPending("yesterday", "2026-08-19T16:00:00Z")
            seedPending("monday", "2026-08-17T16:00:00Z")
            seedPending("month", "2026-08-02T16:00:00Z")
            seedPending("previous-month", "2026-07-31T16:00:00Z")
        }
        val viewModel = fixture.viewModel()
        advanceUntilIdle()

        assertEquals(DetectedMovementDateFilter.TODAY, viewModel.uiState.value.selectedDateFilter)
        assertEquals(listOf("today"), viewModel.uiState.value.groups.map { it.root.id })
        assertEquals(5, viewModel.uiState.value.allActionableCount)

        viewModel.setDateFilter(DetectedMovementDateFilter.YESTERDAY)
        advanceUntilIdle()
        assertEquals(listOf("yesterday"), viewModel.uiState.value.groups.map { it.root.id })

        viewModel.setDateFilter(DetectedMovementDateFilter.THIS_WEEK)
        advanceUntilIdle()
        assertEquals(
            listOf("today", "yesterday", "monday"),
            viewModel.uiState.value.groups.map { it.root.id }
        )

        viewModel.setDateFilter(DetectedMovementDateFilter.THIS_MONTH)
        advanceUntilIdle()
        assertEquals(
            listOf("today", "yesterday", "monday", "month"),
            viewModel.uiState.value.groups.map { it.root.id }
        )
    }

    @Test
    fun `duplicate evidence never creates another visible card`() {
        val root = DetectedMovementEntity(
            id = "canonical",
            source = "BANK_NOTIFICATION",
            occurredAt = 1_700_000_000_000,
            canonicalId = "canonical"
        )
        val evidence = DetectedMovementEntity(
            id = "email-evidence",
            source = "EMAIL_GMAIL",
            occurredAt = 1_700_000_001_000,
            canonicalId = "canonical",
            duplicateOfId = "canonical",
            dedupeState = "DUPLICATE"
        )

        val group = listOf(root, evidence, evidence).toActionableGroups().single()

        assertEquals("canonical", group.root.id)
        assertEquals(listOf("canonical", "email-evidence"), group.evidence.map { it.id })
    }

    @Test
    fun `booking is idempotent and removes movement from pending view`() = runTest {
        val fixture = Fixture()
        fixture.repository.ingestEmailCandidates(
            listOf(fixture.emailCandidate().copy(occurredAt = "2023-11-14T22:41:40Z")),
            "guest"
        )
        fixture.emailRepository.failRemoteReviews = true
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val canonicalId = viewModel.uiState.value.groups.single().root.id
        val request = DetectedMovementBookingRequest(
            canonicalId = canonicalId,
            accountId = "account-1",
            categoryId = "cat_food",
            amountMinor = 10_000,
            direction = "expense",
            occurredAt = 1_700_000_100_000
        )

        viewModel.book(request)
        advanceUntilIdle()
        viewModel.book(request)
        advanceUntilIdle()

        assertEquals(1, fixture.transactionRepository.added.size)
        assertTrue(viewModel.uiState.value.groups.isEmpty())
    }

    @Test
    fun `keeping an ambiguous detection separate clears the warning`() = runTest {
        val fixture = Fixture()
        fixture.repository.ingestEmailCandidates(
            listOf(fixture.emailCandidate(merchant = null, last4 = null)),
            "guest"
        )
        fixture.repository.ingestNotification(
            ownerId = "guest",
            noticeId = "notice-ambiguous",
            appLabel = "Banco",
            title = "Aviso",
            occurredAt = 1_700_000_100_000,
            parsed = fixture.notice(merchant = null, last4 = null)
        )
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val possible = viewModel.uiState.value.groups.single { it.isPossibleDuplicate }

        viewModel.resolvePossibleDuplicate(possible.root.id, keepSeparate = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.groups.none { it.root.id == possible.root.id && it.isPossibleDuplicate })
        assertEquals(2, viewModel.uiState.value.groups.size)
    }
}

private class Fixture {
    private val dao = ViewModelDetectedMovementDao()
    val repository = DetectedMovementRepository(dao)
    val emailRepository = ViewModelEmailRepository()
    val accountRepository = ViewModelAccountRepository()
    val categoryRepository = ViewModelCategoryRepository()
    val transactionRepository = ViewModelTransactionRepository()
    private val ownerScope = WalletOwnerScope(ViewModelSessionStore())
    var nowMillis: Long = Instant.parse("2023-11-14T23:00:00Z").toEpochMilli()

    fun viewModel() = DetectedMovementsViewModel(
        detectedMovementRepository = repository,
        emailConnectionsRepository = emailRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRepository = transactionRepository,
        ownerScope = ownerScope,
        bankNotificationRepository = null
    ).also { it.nowMillisProvider = { nowMillis } }

    suspend fun seedPending(id: String, occurredAt: String) {
        dao.insertMovement(
            DetectedMovementEntity(
                id = id,
                source = "BANK_NOTIFICATION",
                sourceReference = id,
                merchant = id,
                amountMinor = 1_000,
                currency = "DOP",
                occurredAt = Instant.parse(occurredAt).toEpochMilli(),
                canonicalId = id,
                ownerId = "guest"
            )
        )
    }

    suspend fun ingestStrongEmailAndPush() {
        repository.ingestEmailCandidates(listOf(emailCandidate()), "guest")
        repository.ingestNotification(
            ownerId = "guest",
            noticeId = "notice-1",
            appLabel = "Banco Popular",
            title = "Compra aprobada",
            occurredAt = 1_700_000_100_000,
            parsed = notice()
        )
    }

    fun emailCandidate(merchant: String? = "Amazon", last4: String? = "1234") = EmailCandidate(
        id = "gmail-1",
        provider = EmailProvider.GMAIL,
        merchant = merchant,
        cardLastFour = last4,
        amount = -10_000,
        currency = "DOP",
        direction = "expense",
        eventType = "CARD_PURCHASE_APPROVED",
        categorySuggestion = "Comida",
        occurredAt = "2023-11-14T22:13:20Z",
        confidence = 90,
        status = "pending",
        subject = "Compra aprobada"
    )

    fun notice(merchant: String? = "Amazon", last4: String? = "1234") = ParsedBankNotice(
        merchant = merchant,
        amountMinor = 10_000,
        currency = "DOP",
        last4Digits = last4,
        suggestedCategoryId = "cat_food"
    )
}

private class ViewModelDetectedMovementDao : DetectedMovementDao {
    private val movements = mutableListOf<DetectedMovementEntity>()
    private val flow = MutableStateFlow<List<DetectedMovementEntity>>(emptyList())

    private fun publish() {
        flow.value = movements.sortedBy { it.occurredAt }
    }

    override fun observeAll(ownerId: String): Flow<List<DetectedMovementEntity>> =
        flow.map { rows -> rows.filter { it.ownerId == ownerId } }

    override fun getPendingMovements(ownerId: String): Flow<List<DetectedMovementEntity>> =
        flow.map { rows ->
            rows.filter { it.ownerId == ownerId && it.status == "PENDING" && it.duplicateOfId == null }
        }

    override suspend fun getMovementById(ownerId: String, id: String) =
        movements.firstOrNull { it.ownerId == ownerId && it.id == id }

    override suspend fun getByOrigin(ownerId: String, source: String, sourceReference: String) =
        movements.firstOrNull {
            it.ownerId == ownerId && it.source == source && it.sourceReference == sourceReference
        }

    override suspend fun findCanonicalCandidates(ownerId: String, fromInclusive: Long, toInclusive: Long) =
        movements.filter {
            it.ownerId == ownerId && it.status == "PENDING" && it.duplicateOfId == null &&
                it.occurredAt in fromInclusive..toInclusive
        }

    override suspend fun getEvidence(ownerId: String, canonicalId: String) = movements.filter {
        it.ownerId == ownerId && (it.id == canonicalId || it.canonicalId == canonicalId)
    }

    override suspend fun insertMovement(movement: DetectedMovementEntity): Long {
        if (movements.any {
                it.ownerId == movement.ownerId &&
                    (it.id == movement.id ||
                        (movement.sourceReference != null && it.source == movement.source &&
                            it.sourceReference == movement.sourceReference))
            }
        ) return -1
        movements += movement
        publish()
        return movements.size.toLong()
    }

    override suspend fun insertAll(movements: List<DetectedMovementEntity>) =
        movements.map { insertMovement(it) }

    override suspend fun reassignCanonicalGroup(
        ownerId: String,
        oldCanonicalId: String,
        newCanonicalId: String,
        reason: String
    ) {
        movements.replaceAll { movement ->
            if (movement.ownerId == ownerId &&
                (movement.id == oldCanonicalId || movement.canonicalId == oldCanonicalId)
            ) movement.copy(
                canonicalId = newCanonicalId,
                duplicateOfId = newCanonicalId,
                possibleDuplicateOfId = null,
                dedupeState = "DUPLICATE",
                dedupeReason = reason
            ) else movement
        }
        publish()
    }

    override suspend fun resolveCanonicalAsSeparate(ownerId: String, canonicalId: String): Int =
        updateOne(ownerId, canonicalId) {
            it.copy(possibleDuplicateOfId = null, dedupeState = "CANONICAL", dedupeReason = null)
        }

    override suspend fun resolveCanonicalAsTransactionDuplicate(
        ownerId: String,
        canonicalId: String,
        transactionReference: String,
        reason: String
    ): Int = updateGroup(ownerId, canonicalId) {
        it.copy(
            status = "DISMISSED",
            needsSync = false,
            duplicateOfId = transactionReference,
            possibleDuplicateOfId = null,
            dedupeState = "DUPLICATE",
            dedupeReason = reason
        )
    }

    override suspend fun updateEvidenceReviewState(
        ownerId: String,
        evidenceId: String,
        status: String,
        needsSync: Boolean
    ): Int = updateOne(ownerId, evidenceId) { it.copy(status = status, needsSync = needsSync) }

    override suspend fun updateCanonicalGroupStatus(
        ownerId: String,
        canonicalId: String,
        status: String,
        needsSync: Boolean
    ) {
        updateGroup(ownerId, canonicalId) { it.copy(status = status, needsSync = needsSync) }
    }

    override suspend fun updateStatus(ownerId: String, id: String, status: String, needsSync: Boolean) {
        updateOne(ownerId, id) { it.copy(status = status, needsSync = needsSync) }
    }

    override suspend fun deleteMovement(ownerId: String, id: String) {
        movements.removeAll { it.ownerId == ownerId && it.id == id }
        publish()
    }

    override suspend fun purgeProcessedMovements(ownerId: String) {
        movements.removeAll { it.ownerId == ownerId && it.status != "PENDING" }
        publish()
    }

    override suspend fun getAll(ownerId: String) = movements.filter { it.ownerId == ownerId }

    private fun updateOne(
        ownerId: String,
        id: String,
        transform: (DetectedMovementEntity) -> DetectedMovementEntity
    ): Int {
        var count = 0
        movements.replaceAll {
            if (it.ownerId == ownerId && it.id == id) {
                count++
                transform(it)
            } else it
        }
        publish()
        return count
    }

    private fun updateGroup(
        ownerId: String,
        canonicalId: String,
        transform: (DetectedMovementEntity) -> DetectedMovementEntity
    ): Int {
        var count = 0
        movements.replaceAll {
            if (it.ownerId == ownerId && (it.id == canonicalId || it.canonicalId == canonicalId)) {
                count++
                transform(it)
            } else it
        }
        publish()
        return count
    }
}

private class ViewModelEmailRepository : EmailConnectionsRepository {
    var failRemoteReviews = false
    val remoteReviews = mutableListOf<String>()

    override suspend fun getConnections(): List<EmailConnection> = emptyList()
    override suspend fun getCandidates(): List<EmailCandidate> = emptyList()
    override suspend fun getAuthorizationUrl(provider: EmailProvider) = "https://example.test"
    override suspend fun sync(provider: EmailProvider, syncFromAt: String?, syncFromDate: String?) =
        EmailSyncResult(0, 0, 0)

    override suspend fun reviewCandidate(
        id: String,
        action: String,
        category: String?,
        duplicateOfId: String?
    ) = candidate(id)

    override suspend fun reviewCandidateRemotely(
        id: String,
        action: String,
        category: String?,
        duplicateOfId: String?
    ): EmailCandidate {
        remoteReviews += id
        if (failRemoteReviews) error("network")
        return candidate(id)
    }

    override suspend fun disconnect(provider: EmailProvider) = Unit

    private fun candidate(id: String) = EmailCandidate(
        id = id,
        provider = EmailProvider.GMAIL,
        merchant = "Amazon",
        amount = -10_000,
        currency = "DOP",
        direction = "expense",
        categorySuggestion = "Comida",
        occurredAt = "2023-11-14T22:13:20Z",
        confidence = 90,
        status = "categorized",
        subject = "Compra"
    )
}

private class ViewModelAccountRepository : AccountRepository {
    private val accounts = MutableStateFlow(
        listOf(Account("account-1", "Cuenta principal", "BANK", 500_000, "DOP"))
    )

    override fun getAccounts(): Flow<List<Account>> = accounts
    override suspend fun getAccount(id: String) = accounts.value.firstOrNull { it.id == id }
    override suspend fun addAccount(account: Account) { accounts.value += account }
    override suspend fun updateAccount(account: Account) = Unit
    override suspend fun deleteAccount(id: String): List<Transaction> = emptyList()
}

private class ViewModelCategoryRepository : CategoryRepository {
    private val categories = MutableStateFlow(
        listOf(Category("cat_food", "Comida", "restaurant", "#E57373", "EXPENSE"))
    )

    override fun getCategories(): Flow<List<Category>> = categories
    override suspend fun getCategory(id: String) = categories.value.firstOrNull { it.id == id }
    override suspend fun getAllCategoryIdsIncludingDeleted() = categories.value.mapTo(mutableSetOf()) { it.id }
    override suspend fun addCategory(category: Category) { categories.value += category }
    override suspend fun deleteCategory(id: String) = Unit
}

private class ViewModelTransactionRepository : TransactionRepository {
    private val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val added = mutableListOf<Transaction>()

    override fun getTransactions(): Flow<List<Transaction>> = transactions
    override fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>> =
        transactions.map { rows -> rows.filter { it.accountId == accountId } }
    override suspend fun getTransaction(id: String) = transactions.value.firstOrNull { it.id == id }
    override suspend fun getTransactionsForDebt(debtId: String) = emptyList<Transaction>()
    override suspend fun addTransaction(transaction: Transaction) { transactions.value += transaction }
    override suspend fun addTransactionWithBalance(transaction: Transaction) {
        if (transactions.value.none { it.id == transaction.id }) {
            transactions.value += transaction
            added += transaction
        }
    }
    override suspend fun updateTransaction(transaction: Transaction) = Unit
    override suspend fun updateTransactionWithBalance(transaction: Transaction, oldAmount: Long) = Unit
    override suspend fun deleteTransaction(id: String) = Unit
    override suspend fun deleteTransactionWithBalance(transaction: Transaction) = Unit
}

private class ViewModelSessionStore : WalletSessionStore {
    override val token: String? = null
    override val user: AuthUser? = null
    override fun save(token: String, user: AuthUser) = Unit
    override fun clear() = Unit
}
