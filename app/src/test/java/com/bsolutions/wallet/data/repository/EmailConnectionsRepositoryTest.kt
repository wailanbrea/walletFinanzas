package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.network.UpdateProfileRequest
import com.bsolutions.wallet.core.network.AuthUserDto
import com.bsolutions.wallet.core.network.AccountDto
import com.bsolutions.wallet.core.network.ApiEnvelope
import com.bsolutions.wallet.core.network.AuthPayload
import com.bsolutions.wallet.core.network.EmailAuthorizationDto
import com.bsolutions.wallet.core.network.EmailCandidateDto
import com.bsolutions.wallet.core.network.EmailConnectionDto
import com.bsolutions.wallet.core.network.EmailSyncDto
import com.bsolutions.wallet.core.network.ForgotPasswordRequest
import com.bsolutions.wallet.core.network.LoginRequest
import com.bsolutions.wallet.core.network.MessageResponse
import com.bsolutions.wallet.core.network.RegisterRequest
import com.bsolutions.wallet.core.network.WalletApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailConnectionsRepositoryTest {

    @Test
    fun `maps nullable backend connection fields without storing provider tokens`() = runTest {
        val api = FakeEmailConnectionsApi(
            connections = listOf(
                EmailConnectionDto(
                    provider = "gmail",
                    displayName = "Gmail",
                    status = "connected",
                    email = "ana@example.com",
                    configurationReady = true,
                    connectedAt = "2026-07-20T10:15:00Z",
                    expiresAt = null
                )
            )
        )
        val repository = DefaultEmailConnectionsRepository(api, TestWalletSessionStore())

        val connection = repository.getConnections().single()

        assertEquals(EmailProvider.GMAIL, connection.provider)
        assertEquals("ana@example.com", connection.email)
        assertEquals(EmailConnectionStatus.CONNECTED, connection.status)
        assertNull(connection.expiresAt)
    }

    @Test
    fun `sync forwards the selected date to backend`() = runTest {
        val api = FakeEmailConnectionsApi()
        val repository = DefaultEmailConnectionsRepository(api, TestWalletSessionStore())

        repository.sync(EmailProvider.GMAIL, "2026-07-01T04:00:00Z", "2026-07-01")

        assertEquals(
            listOf("gmail" to "2026-07-01T04:00:00Z"),
            api.syncRequests.map { it.first to it.second.syncFromAt }
        )
        assertEquals("2026-07-01", api.syncRequests.single().second.syncFromDate)
    }

    @Test
    fun `sync polls one logical run until every batch completes`() = runTest {
        val api = FakeEmailConnectionsApi(
            syncRuns = listOf(
                EmailSyncDto(syncRunId = 7L, status = "queued", messagesDiscovered = 100),
                EmailSyncDto(syncRunId = 7L, status = "running", messagesDiscovered = 100),
                EmailSyncDto(
                    syncRunId = 7L,
                    status = "completed",
                    messagesDiscovered = 125,
                    candidatesCreated = 6
                )
            )
        )
        val repository = DefaultEmailConnectionsRepository(api, TestWalletSessionStore())

        val result = repository.sync(EmailProvider.GMAIL)

        assertEquals(125, result.messagesDiscovered)
        assertEquals(6, result.candidatesCreated)
        assertEquals(listOf(7L, 7L), api.syncRunRequests)
    }

    @Test
    fun `requests authorization URL and disconnects using backend provider values`() = runTest {
        val api = FakeEmailConnectionsApi(authorizationUrl = "https://accounts.example/authorize")
        val repository = DefaultEmailConnectionsRepository(api, TestWalletSessionStore())

        assertEquals("https://accounts.example/authorize", repository.getAuthorizationUrl(EmailProvider.MICROSOFT))
        repository.disconnect(EmailProvider.MICROSOFT)

        assertEquals(listOf("microsoft"), api.authorizationProviders)
        assertEquals(listOf("microsoft"), api.deletedProviders)
    }

    @Test
    fun `unknown backend status maps to error instead of appearing connected`() = runTest {
        val api = FakeEmailConnectionsApi(
            connections = listOf(
                EmailConnectionDto("gmail", "Gmail", "unexpected", null, true, null, null)
            )
        )

        val connection = DefaultEmailConnectionsRepository(api, TestWalletSessionStore()).getConnections().single()

        assertEquals(EmailConnectionStatus.ERROR, connection.status)
    }

    @Test
    fun `unknown provider is rejected instead of mapped to Microsoft`() = runTest {
        val api = FakeEmailConnectionsApi(
            connections = listOf(
                EmailConnectionDto("unknown", "Unknown", "connected", null, true, null, null)
            )
        )
        var rejected = false

        try {
            DefaultEmailConnectionsRepository(api, TestWalletSessionStore()).getConnections()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
    }
}

private class TestWalletSessionStore : WalletSessionStore {
    override var token: String? = null
    override var user: AuthUser? = null

    override fun save(token: String, user: AuthUser) {
        this.token = token
        this.user = user
    }

    override fun clear() {
        token = null
        user = null
    }
}

private class FakeEmailConnectionsApi(
    private val connections: List<EmailConnectionDto> = emptyList(),
    private val candidates: List<EmailCandidateDto> = emptyList(),
    private val authorizationUrl: String = "https://example.test/authorize",
    private val syncRuns: List<EmailSyncDto> = listOf(EmailSyncDto(syncRunId = 1L, status = "completed"))
) : WalletApi {
    override suspend fun getProfile(): ApiEnvelope<AuthUserDto> = error("sin uso")
    override suspend fun updateProfile(request: UpdateProfileRequest): ApiEnvelope<AuthUserDto> = error("sin uso")
    val authorizationProviders = mutableListOf<String>()
    val deletedProviders = mutableListOf<String>()

    override suspend fun emailConnections() = ApiEnvelope(connections)

    val syncRunRequests = mutableListOf<Long>()
    val syncRequests = mutableListOf<Pair<String, com.bsolutions.wallet.core.network.EmailSyncRequest>>()
    override suspend fun emailAuthorizationUrl(provider: String): ApiEnvelope<EmailAuthorizationDto> {
        authorizationProviders += provider
        return ApiEnvelope(EmailAuthorizationDto(authorizationUrl))
    }

    override suspend fun syncEmailConnection(
        provider: String,
        request: com.bsolutions.wallet.core.network.EmailSyncRequest
    ): ApiEnvelope<EmailSyncDto> {
        syncRequests += provider to request
        return ApiEnvelope(syncRuns.first())
    }

    override suspend fun emailSyncRun(provider: String, runId: Long): ApiEnvelope<EmailSyncDto> {
        syncRunRequests += runId
        return ApiEnvelope(syncRuns[minOf(syncRunRequests.size, syncRuns.lastIndex)])
    }

    override suspend fun emailCandidates() = ApiEnvelope(candidates)

    override suspend fun reviewEmailCandidate(
        candidateId: String,
        request: com.bsolutions.wallet.core.network.EmailCandidateReviewRequest
    ): ApiEnvelope<EmailCandidateDto> = unsupported()

    override suspend fun deleteEmailConnection(provider: String) {
        deletedProviders += provider
    }

    override suspend fun register(request: RegisterRequest): ApiEnvelope<AuthPayload> = unsupported()
    override suspend fun login(request: LoginRequest): ApiEnvelope<AuthPayload> = unsupported()
    override suspend fun logout(): MessageResponse = unsupported()
    override suspend fun forgotPassword(request: ForgotPasswordRequest): MessageResponse = unsupported()
    override suspend fun accounts(): ApiEnvelope<List<AccountDto>> = unsupported()
    override suspend fun createAccount(request: com.bsolutions.wallet.core.network.CreateAccountRequest): ApiEnvelope<AccountDto> = unsupported()
    override suspend fun createTransaction(request: com.bsolutions.wallet.core.network.CreateTransactionRequest): ApiEnvelope<com.bsolutions.wallet.core.network.TransactionDto> = unsupported()
    override suspend fun updateTransaction(id: String, request: com.bsolutions.wallet.core.network.UpdateTransactionRequest): ApiEnvelope<com.bsolutions.wallet.core.network.TransactionDto> = error("Not used")
    override suspend fun deleteTransaction(id: String): retrofit2.Response<Unit> = error("Not used")
    override suspend fun createCategory(request: com.bsolutions.wallet.core.network.CreateCategoryRequest): ApiEnvelope<com.bsolutions.wallet.core.network.CategoryDto> = unsupported()
    override suspend fun pullAccounts(updatedSince: String?, cursor: String?, perPage: Int): com.bsolutions.wallet.core.network.CursorPage<AccountDto> = unsupported()
    override suspend fun pullTransactions(updatedSince: String?, cursor: String?, perPage: Int): com.bsolutions.wallet.core.network.CursorPage<com.bsolutions.wallet.core.network.TransactionDto> = unsupported()
    override suspend fun pullCategories(updatedSince: String?, cursor: String?, perPage: Int): com.bsolutions.wallet.core.network.CursorPage<com.bsolutions.wallet.core.network.CategoryDto> = unsupported()
    override suspend fun upsertBudget(request: com.bsolutions.wallet.core.network.BudgetSyncDto) = unsupported<com.bsolutions.wallet.core.network.ApiEnvelope<com.bsolutions.wallet.core.network.BudgetSyncDto>>()
    override suspend fun upsertGoal(request: com.bsolutions.wallet.core.network.GoalSyncDto) = unsupported<com.bsolutions.wallet.core.network.ApiEnvelope<com.bsolutions.wallet.core.network.GoalSyncDto>>()
    override suspend fun upsertDebt(request: com.bsolutions.wallet.core.network.DebtSyncDto) = unsupported<com.bsolutions.wallet.core.network.ApiEnvelope<com.bsolutions.wallet.core.network.DebtSyncDto>>()
    override suspend fun upsertPlannedPayment(request: com.bsolutions.wallet.core.network.PlannedPaymentSyncDto) = unsupported<com.bsolutions.wallet.core.network.ApiEnvelope<com.bsolutions.wallet.core.network.PlannedPaymentSyncDto>>()
    override suspend fun pullBudgets(updatedSince: String?, cursor: String?, perPage: Int) = unsupported<com.bsolutions.wallet.core.network.CursorPage<com.bsolutions.wallet.core.network.BudgetSyncDto>>()
    override suspend fun pullGoals(updatedSince: String?, cursor: String?, perPage: Int) = unsupported<com.bsolutions.wallet.core.network.CursorPage<com.bsolutions.wallet.core.network.GoalSyncDto>>()
    override suspend fun pullDebts(updatedSince: String?, cursor: String?, perPage: Int) = unsupported<com.bsolutions.wallet.core.network.CursorPage<com.bsolutions.wallet.core.network.DebtSyncDto>>()
    override suspend fun pullPlannedPayments(updatedSince: String?, cursor: String?, perPage: Int) = unsupported<com.bsolutions.wallet.core.network.CursorPage<com.bsolutions.wallet.core.network.PlannedPaymentSyncDto>>()

    private fun <T> unsupported(): T = throw UnsupportedOperationException("Not needed by this test")
}
