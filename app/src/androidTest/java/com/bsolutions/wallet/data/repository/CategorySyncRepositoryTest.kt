package com.bsolutions.wallet.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bsolutions.wallet.core.database.WalletDatabase
import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.core.network.AccountDto
import com.bsolutions.wallet.core.network.ApiEnvelope
import com.bsolutions.wallet.core.network.AuthPayload
import com.bsolutions.wallet.core.network.CategoryDto
import com.bsolutions.wallet.core.network.CreateAccountRequest
import com.bsolutions.wallet.core.network.CreateCategoryRequest
import com.bsolutions.wallet.core.network.CreateTransactionRequest
import com.bsolutions.wallet.core.network.CursorPage
import com.bsolutions.wallet.core.network.EmailAuthorizationDto
import com.bsolutions.wallet.core.network.EmailCandidateDto
import com.bsolutions.wallet.core.network.EmailCandidateReviewRequest
import com.bsolutions.wallet.core.network.EmailConnectionDto
import com.bsolutions.wallet.core.network.EmailSyncDto
import com.bsolutions.wallet.core.network.ForgotPasswordRequest
import com.bsolutions.wallet.core.network.LoginRequest
import com.bsolutions.wallet.core.network.MessageResponse
import com.bsolutions.wallet.core.network.RegisterRequest
import com.bsolutions.wallet.core.network.TransactionDto
import com.bsolutions.wallet.core.network.AuthUserDto
import com.bsolutions.wallet.core.network.UpdateProfileRequest
import com.bsolutions.wallet.core.network.UpdateTransactionRequest
import com.bsolutions.wallet.core.network.WalletApi
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import retrofit2.Response
import com.bsolutions.wallet.core.network.BudgetSyncDto
import com.bsolutions.wallet.core.network.GoalSyncDto
import com.bsolutions.wallet.core.network.DebtSyncDto
import com.bsolutions.wallet.core.network.PlannedPaymentSyncDto
import com.bsolutions.wallet.data.local.entity.BudgetEntity
import com.bsolutions.wallet.data.local.entity.GoalEntity
import com.bsolutions.wallet.data.local.entity.DebtEntity
import com.bsolutions.wallet.data.local.entity.PlannedPaymentEntity
import com.bsolutions.wallet.data.local.entity.CategoryEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategorySyncRepositoryTest {
    private lateinit var database: WalletDatabase
    private lateinit var api: CategorySyncFakeApi
    private lateinit var repository: SyncRepository
    private val ownerId = "user:1"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WalletDatabase::class.java
        ).allowMainThreadQueries().build()
        api = CategorySyncFakeApi()
        val session = CategorySyncSessionStore()
        val ownerScope = WalletOwnerScope(session)
        repository = SyncRepository(
            api = api,
            session = session,
            pendingOps = database.pendingOperationDao(),
            accountDao = database.accountDao(),
            categoryDao = database.categoryDao(),
            transactionDao = database.transactionDao(),
            budgetDao = database.budgetDao(),
            goalDao = database.goalDao(),
            debtDao = database.debtDao(),
            plannedPaymentDao = database.plannedPaymentDao(),
            ownerScope = ownerScope,
            preferences = UserPreferencesRepository(
                ApplicationProvider.getApplicationContext(),
                ownerScope
            ),
            gson = Gson()
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun categoryIsPushedBeforeTransactionThatReferencesIt() = runBlocking {
        val category = CategoryEntity(
            id = "cat_transporte",
            name = "Transporte",
            icon = "directions_car",
            colorHex = "#64B5F6",
            ownerId = ownerId
        )
        database.categoryDao().insertCategory(category)
        val transaction = TransactionEntity(
            id = "00000000-0000-4000-8000-000000000001",
            accountId = "00000000-0000-4000-8000-000000000002",
            amount = 500,
            type = "EXPENSE",
            categoryId = category.id,
            date = 1_700_000_000_000,
            note = "Taxi",
            currency = "DOP",
            ownerId = ownerId
        )
        database.pendingOperationDao().insert(SyncRepository.transactionOp(Gson(), transaction))

        val outcome = repository.sync()

        assertEquals(SyncOutcome.Success(pushed = 2, pulled = 0), outcome)
        assertEquals(listOf("CATEGORY:cat_transporte", "TRANSACTION:${transaction.id}"), api.calls)
        assertTrue(database.categoryDao().getCategoriesNeedingSync(ownerId).isEmpty())
    }

    @Test
    fun remoteTombstoneDeletesLocalCategoryWithoutCreatingAnotherPush() = runBlocking {
        database.categoryDao().insertCategory(
            CategoryEntity(
                id = "custom-1",
                name = "Negocio",
                icon = "work",
                colorHex = "#123456",
                needsSync = false,
                ownerId = ownerId
            )
        )
        api.remoteCategories = listOf(
            CategoryDto(
                id = "custom-1",
                name = "Negocio",
                icon = "work",
                colorHex = "#123456",
                isDeleted = true,
                updatedAt = "2026-07-22T10:00:00Z"
            )
        )

        val outcome = repository.sync()

        assertEquals(SyncOutcome.Success(pushed = 0, pulled = 1), outcome)
        assertNull(database.categoryDao().getCategoryById(ownerId, "custom-1"))
        val tombstone = database.categoryDao().getCategoryByIdIncludingDeleted(ownerId, "custom-1")
        assertEquals(true, tombstone?.isDeleted)
        assertEquals(false, tombstone?.needsSync)
    }

    @Test
    fun financialPlanningResourcesArePushedAndMarkedSynced() = runBlocking {
        database.budgetDao().insertBudget(
            BudgetEntity(
                id = "00000000-0000-4000-8000-000000000011",
                categoryId = "cat_servicios",
                limitAmount = 10_000,
                spentAmount = 1_000,
                ownerId = ownerId
            )
        )
        database.goalDao().insertGoal(
            GoalEntity(
                id = "00000000-0000-4000-8000-000000000012",
                name = "Reserva",
                targetAmount = 50_000,
                ownerId = ownerId
            )
        )
        database.debtDao().insertDebt(
            DebtEntity(
                id = "00000000-0000-4000-8000-000000000013",
                name = "Prestamo",
                totalAmount = 25_000,
                ownerId = ownerId
            )
        )
        database.plannedPaymentDao().insertPlannedPayment(
            PlannedPaymentEntity(
                id = "00000000-0000-4000-8000-000000000014",
                name = "Internet",
                accountId = "00000000-0000-4000-8000-000000000002",
                amount = 3_500,
                nextDueDate = 1_800_000_000_000,
                ownerId = ownerId
            )
        )

        val first = repository.sync()

        assertEquals(SyncOutcome.Success(pushed = 4, pulled = 0), first)
        assertEquals(listOf("BUDGET", "GOAL", "DEBT", "PLANNED_PAYMENT"), api.calls)
        assertTrue(database.budgetDao().getNeedingSync(ownerId).isEmpty())
        assertTrue(database.goalDao().getNeedingSync(ownerId).isEmpty())
        assertTrue(database.debtDao().getNeedingSync(ownerId).isEmpty())
        assertTrue(database.plannedPaymentDao().getNeedingSync(ownerId).isEmpty())
    }

    @Test
    fun remoteFinancialPlanningTombstonesAreAppliedWithoutRequeue() = runBlocking {
        api.remoteBudgets = listOf(
            BudgetSyncDto(
                id = "00000000-0000-4000-8000-000000000021",
                categoryId = "cat_servicios",
                limitAmount = 10_000,
                spentAmount = 2_000,
                period = "MONTHLY",
                isDeleted = true
            )
        )
        api.remoteGoals = listOf(
            GoalSyncDto(
                id = "00000000-0000-4000-8000-000000000022",
                name = "Reserva",
                icon = "savings",
                targetAmount = 50_000,
                savedAmount = 20_000,
                targetDate = null,
                isCompleted = false,
                isDeleted = true
            )
        )
        api.remoteDebts = listOf(
            DebtSyncDto(
                id = "00000000-0000-4000-8000-000000000023",
                name = "Prestamo",
                description = "",
                direction = "I_OWE",
                totalAmount = 25_000,
                paidAmount = 5_000,
                dueDate = null,
                isClosed = false,
                isDeleted = true
            )
        )
        api.remotePlannedPayments = listOf(
            PlannedPaymentSyncDto(
                id = "00000000-0000-4000-8000-000000000024",
                name = "Internet",
                accountId = "00000000-0000-4000-8000-000000000002",
                categoryId = "",
                amount = 3_500,
                type = "EXPENSE",
                frequency = "MONTHLY",
                nextDueDate = 1_800_000_000_000,
                isActive = false,
                isDeleted = true
            )
        )

        val outcome = repository.sync()

        assertEquals(SyncOutcome.Success(pushed = 0, pulled = 4), outcome)
        assertTrue(database.ownerIsolationDao().budgets(ownerId).single().isDeleted)
        assertTrue(database.ownerIsolationDao().goals(ownerId).single().isDeleted)
        assertTrue(database.ownerIsolationDao().debts(ownerId).single().isDeleted)
        assertTrue(database.ownerIsolationDao().plannedPayments(ownerId).single().isDeleted)
        assertTrue(database.budgetDao().getNeedingSync(ownerId).isEmpty())
        assertTrue(database.goalDao().getNeedingSync(ownerId).isEmpty())
        assertTrue(database.debtDao().getNeedingSync(ownerId).isEmpty())
        assertTrue(database.plannedPaymentDao().getNeedingSync(ownerId).isEmpty())
    }
}

private class CategorySyncSessionStore : WalletSessionStore {
    override var token: String? = "test-token"
    override var user: AuthUser? = AuthUser("1", "test@example.com")

    override fun save(token: String, user: AuthUser) {
        this.token = token
        this.user = user
    }

    override fun clear() {
        token = null
        user = null
    }
}

private class CategorySyncFakeApi : WalletApi {
    val calls = mutableListOf<String>()
    var remoteCategories: List<CategoryDto> = emptyList()
    var remoteBudgets: List<BudgetSyncDto> = emptyList()
    var remoteGoals: List<GoalSyncDto> = emptyList()
    var remoteDebts: List<DebtSyncDto> = emptyList()
    var remotePlannedPayments: List<PlannedPaymentSyncDto> = emptyList()

    override suspend fun createCategory(request: CreateCategoryRequest): ApiEnvelope<CategoryDto> {
        calls += "CATEGORY:${request.id}"
        return ApiEnvelope(
            CategoryDto(
                id = request.id,
                name = request.name,
                icon = request.icon,
                colorHex = request.colorHex,
                type = request.type,
                isDeleted = request.isDeleted,
                updatedAt = null
            )
        )
    }

    override suspend fun createTransaction(request: CreateTransactionRequest): ApiEnvelope<TransactionDto> {
        calls += "TRANSACTION:${request.idempotencyKey}"
        return ApiEnvelope(
            TransactionDto(
                id = request.idempotencyKey,
                idempotencyKey = request.idempotencyKey,
                accountId = request.accountId,
                amount = request.amount,
                currency = request.currency,
                description = request.description,
                categoryId = request.categoryId,
                timestamp = request.timestamp,
                status = request.status,
                updatedAt = null
            )
        )
    }

    override suspend fun pullCategories(updatedSince: String?, cursor: String?, perPage: Int) =
        CursorPage(data = remoteCategories)

    override suspend fun pullAccounts(updatedSince: String?, cursor: String?, perPage: Int) =
        CursorPage<AccountDto>(emptyList())

    override suspend fun pullTransactions(updatedSince: String?, cursor: String?, perPage: Int) =
        CursorPage<TransactionDto>(emptyList())

    override suspend fun upsertBudget(request: BudgetSyncDto): ApiEnvelope<BudgetSyncDto> {
        calls += "BUDGET"
        return ApiEnvelope(request)
    }

    override suspend fun upsertGoal(request: GoalSyncDto): ApiEnvelope<GoalSyncDto> {
        calls += "GOAL"
        return ApiEnvelope(request)
    }

    override suspend fun upsertDebt(request: DebtSyncDto): ApiEnvelope<DebtSyncDto> {
        calls += "DEBT"
        return ApiEnvelope(request)
    }

    override suspend fun upsertPlannedPayment(request: PlannedPaymentSyncDto): ApiEnvelope<PlannedPaymentSyncDto> {
        calls += "PLANNED_PAYMENT"
        return ApiEnvelope(request)
    }

    override suspend fun pullBudgets(updatedSince: String?, cursor: String?, perPage: Int) =
        CursorPage(data = remoteBudgets)
    override suspend fun pullGoals(updatedSince: String?, cursor: String?, perPage: Int) =
        CursorPage(data = remoteGoals)
    override suspend fun pullDebts(updatedSince: String?, cursor: String?, perPage: Int) =
        CursorPage(data = remoteDebts)
    override suspend fun pullPlannedPayments(updatedSince: String?, cursor: String?, perPage: Int) =
        CursorPage(data = remotePlannedPayments)

    override suspend fun register(request: RegisterRequest): ApiEnvelope<AuthPayload> = unsupported()
    override suspend fun login(request: LoginRequest): ApiEnvelope<AuthPayload> = unsupported()
    override suspend fun logout(): MessageResponse = unsupported()
    override suspend fun forgotPassword(request: ForgotPasswordRequest): MessageResponse = unsupported()
    override suspend fun accounts(): ApiEnvelope<List<AccountDto>> = unsupported()
    override suspend fun createAccount(request: CreateAccountRequest): ApiEnvelope<AccountDto> = unsupported()
    override suspend fun emailConnections(): ApiEnvelope<List<EmailConnectionDto>> = unsupported()
    override suspend fun emailAuthorizationUrl(provider: String): ApiEnvelope<EmailAuthorizationDto> = unsupported()
    override suspend fun syncEmailConnection(provider: String): ApiEnvelope<EmailSyncDto> = unsupported()
    override suspend fun emailSyncRun(provider: String, runId: Long): ApiEnvelope<EmailSyncDto> = unsupported()
    override suspend fun emailCandidates(): ApiEnvelope<List<EmailCandidateDto>> = unsupported()
    override suspend fun reviewEmailCandidate(
        candidateId: String,
        request: EmailCandidateReviewRequest
    ): ApiEnvelope<EmailCandidateDto> = unsupported()
    override suspend fun deleteEmailConnection(provider: String) = Unit
    override suspend fun getProfile(): ApiEnvelope<AuthUserDto> = unsupported()
    override suspend fun updateProfile(request: UpdateProfileRequest): ApiEnvelope<AuthUserDto> = unsupported()

    /** Guarda lo que se corrige para poder comprobarlo desde las pruebas. */
    val updatedTransactions = mutableMapOf<String, UpdateTransactionRequest>()

    override suspend fun updateTransaction(
        id: String,
        request: UpdateTransactionRequest
    ): ApiEnvelope<TransactionDto> {
        calls += "TRANSACTION_UPDATE:$id"
        updatedTransactions[id] = request

        return ApiEnvelope(
            TransactionDto(
                id = id,
                idempotencyKey = id,
                accountId = "",
                amount = request.amount,
                currency = "DOP",
                description = request.description,
                categoryId = request.categoryId,
                debtId = request.debtId,
                timestamp = request.timestamp,
                status = "completed",
                updatedAt = null
            )
        )
    }

    override suspend fun deleteTransaction(id: String): Response<Unit> {
        calls += "TRANSACTION_DELETE:$id"

        return Response.success(null)
    }

    private fun <T> unsupported(): T = throw UnsupportedOperationException("Not used")
}
