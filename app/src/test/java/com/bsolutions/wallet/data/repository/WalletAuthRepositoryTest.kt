package com.bsolutions.wallet.data.repository

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
import com.bsolutions.wallet.core.network.handleWalletResponse
import com.bsolutions.wallet.core.network.walletDeviceName
import com.bsolutions.wallet.core.database.LocalDataIsolation
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class WalletAuthRepositoryTest {

    @Test
    fun `device name distinguishes phones of the same model`() {
        assertNotEquals(
            walletDeviceName("SM-G998U", "device-a"),
            walletDeviceName("SM-G998U", "device-b")
        )
    }

    @Test
    fun `global unauthorized response clears session but server error does not`() {
        val session = FakeWalletSessionStore().apply {
            save("token", AuthUser("1", "ada@example.com"))
        }

        handleWalletResponse(500, session)
        assertEquals("token", session.token)

        handleWalletResponse(401, session)
        assertEquals(null, session.token)
        assertEquals(null, session.user)
    }

    @Test
    fun `email 401 clears stale session and reports expiration`() = runTest {
        val api = FakeWalletApi().apply {
            emailConnectionsFailure = HttpException(Response.error<Unit>(401, "".toResponseBody()))
        }
        val session = FakeWalletSessionStore().apply {
            save("stale-token", AuthUser("1", "ada@example.com"))
        }
        val repository = DefaultEmailConnectionsRepository(api, session)

        val failure = runCatching { repository.getConnections() }.exceptionOrNull()

        assertTrue(failure is EmailSessionExpiredException)
        assertEquals(null, session.token)
        assertEquals(null, session.user)
    }

    @Test
    fun `register sends backend contract and persists session`() = runTest {
        val api = FakeWalletApi()
        val session = FakeWalletSessionStore()
        val isolation = FakeLocalDataIsolation()
        val repository = WalletAuthRepository(api, session, "android-test", isolation)

        val result = repository.signUp("Ada Lovelace", "ada@example.com", "strong-pass")

        assertTrue(result is AuthResult.Success)
        assertEquals(
            RegisterRequest(
                name = "Ada Lovelace",
                email = "ada@example.com",
                password = "strong-pass",
                passwordConfirmation = "strong-pass",
                deviceName = "android-test"
            ),
            api.registerRequest
        )
        assertEquals("token-123", session.token)
        assertEquals("1", isolation.activeUserId)
        // El nombre viaja hasta la sesión: sin él el menú lateral seguiría diciendo "Mi Perfil".
        assertEquals(AuthUser("1", "ada@example.com", "Ada Lovelace"), session.user)
    }

    @Test
    fun `login normalizes email and persists session`() = runTest {
        val api = FakeWalletApi()
        val session = FakeWalletSessionStore()
        val repository = WalletAuthRepository(api, session, "android-test")

        val result = repository.signIn(" ADA@Example.COM ", "strong-pass")

        assertTrue(result is AuthResult.Success)
        assertEquals(LoginRequest("ada@example.com", "strong-pass", "android-test"), api.loginRequest)
        assertEquals("token-123", session.token)
    }

    @Test
    fun `login rolls back session when local owner activation fails`() = runTest {
        val session = FakeWalletSessionStore()
        val isolation = FakeLocalDataIsolation().apply { activationFailure = true }
        val repository = WalletAuthRepository(FakeWalletApi(), session, "android-test", isolation)

        val result = repository.signIn("ada@example.com", "strong-pass")

        assertTrue(result is AuthResult.Error)
        assertEquals(null, session.token)
        assertEquals(null, session.user)
    }

    @Test
    fun `logout revokes backend token and clears local session`() = runTest {
        val api = FakeWalletApi()
        val session = FakeWalletSessionStore().apply {
            save("token-123", AuthUser("1", "ada@example.com"))
        }
        val isolation = FakeLocalDataIsolation()
        val repository = WalletAuthRepository(api, session, "android-test", isolation)

        repository.signOut()

        assertTrue(api.logoutCalled)
        assertEquals(null, session.token)
        assertEquals(null, session.user)
        assertTrue(isolation.guestActivated)
    }

    @Test
    fun `logout clears local session without propagating remote failure`() = runTest {
        val api = FakeWalletApi().apply { logoutFailure = true }
        val session = FakeWalletSessionStore().apply {
            save("token-123", AuthUser("1", "ada@example.com"))
        }
        val repository = WalletAuthRepository(api, session, "android-test")

        repository.signOut()

        assertTrue(api.logoutCalled)
        assertEquals(null, session.token)
        assertEquals(null, session.user)
    }

    @Test
    fun `password recovery normalizes email and returns generic success`() = runTest {
        val api = FakeWalletApi()
        val repository = WalletAuthRepository(api, FakeWalletSessionStore(), "android-test")

        val result = repository.sendPasswordReset(" ADA@Example.COM ")

        assertTrue(result is AuthResult.Success)
        assertEquals(ForgotPasswordRequest("ada@example.com"), api.forgotPasswordRequest)
    }
}

private class FakeWalletApi : WalletApi {
    var registerRequest: RegisterRequest? = null
    var loginRequest: LoginRequest? = null
    var logoutCalled = false
    var logoutFailure = false
    var forgotPasswordRequest: ForgotPasswordRequest? = null
    var emailConnectionsFailure: Exception? = null

    override suspend fun register(request: RegisterRequest): ApiEnvelope<AuthPayload> {
        registerRequest = request
        return ApiEnvelope(
            AuthPayload(
                token = "token-123",
                user = com.bsolutions.wallet.core.network.AuthUserDto(
                    id = 1L,
                    name = "Ada Lovelace",
                    email = "ada@example.com"
                )
            )
        )
    }

    override suspend fun login(request: LoginRequest): ApiEnvelope<AuthPayload> {
        loginRequest = request
        return ApiEnvelope(
            AuthPayload(
                token = "token-123",
                user = com.bsolutions.wallet.core.network.AuthUserDto(1L, "Ada Lovelace", "ada@example.com")
            )
        )
    }

    override suspend fun logout(): MessageResponse {
        logoutCalled = true
        if (logoutFailure) error("network unavailable")
        return MessageResponse("Sesión cerrada.")
    }

    override suspend fun forgotPassword(request: ForgotPasswordRequest): MessageResponse {
        forgotPasswordRequest = request
        return MessageResponse("Si el correo está registrado, recibirás instrucciones.")
    }

    override suspend fun accounts(): ApiEnvelope<List<AccountDto>> = error("Not used")
    override suspend fun createAccount(request: com.bsolutions.wallet.core.network.CreateAccountRequest): ApiEnvelope<AccountDto> = error("Not used")
    override suspend fun createTransaction(request: com.bsolutions.wallet.core.network.CreateTransactionRequest): ApiEnvelope<com.bsolutions.wallet.core.network.TransactionDto> = error("Not used")
    override suspend fun updateTransaction(id: String, request: com.bsolutions.wallet.core.network.UpdateTransactionRequest): ApiEnvelope<com.bsolutions.wallet.core.network.TransactionDto> = error("Not used")
    override suspend fun deleteTransaction(id: String): retrofit2.Response<Unit> = error("Not used")
    override suspend fun createCategory(request: com.bsolutions.wallet.core.network.CreateCategoryRequest): ApiEnvelope<com.bsolutions.wallet.core.network.CategoryDto> = error("Not used")
    override suspend fun pullAccounts(updatedSince: String?, cursor: String?, perPage: Int): com.bsolutions.wallet.core.network.CursorPage<AccountDto> = error("Not used")
    override suspend fun pullTransactions(updatedSince: String?, cursor: String?, perPage: Int): com.bsolutions.wallet.core.network.CursorPage<com.bsolutions.wallet.core.network.TransactionDto> = error("Not used")
    override suspend fun pullCategories(updatedSince: String?, cursor: String?, perPage: Int): com.bsolutions.wallet.core.network.CursorPage<com.bsolutions.wallet.core.network.CategoryDto> = error("Not used")
    override suspend fun upsertBudget(request: com.bsolutions.wallet.core.network.BudgetSyncDto) = error("Not used")
    override suspend fun upsertGoal(request: com.bsolutions.wallet.core.network.GoalSyncDto) = error("Not used")
    override suspend fun upsertDebt(request: com.bsolutions.wallet.core.network.DebtSyncDto) = error("Not used")
    override suspend fun upsertPlannedPayment(request: com.bsolutions.wallet.core.network.PlannedPaymentSyncDto) = error("Not used")
    override suspend fun pullBudgets(updatedSince: String?, cursor: String?, perPage: Int) = error("Not used")
    override suspend fun pullGoals(updatedSince: String?, cursor: String?, perPage: Int) = error("Not used")
    override suspend fun pullDebts(updatedSince: String?, cursor: String?, perPage: Int) = error("Not used")
    override suspend fun pullPlannedPayments(updatedSince: String?, cursor: String?, perPage: Int) = error("Not used")
    override suspend fun emailConnections(): ApiEnvelope<List<EmailConnectionDto>> {
        emailConnectionsFailure?.let { throw it }
        return ApiEnvelope(emptyList())
    }
    override suspend fun emailAuthorizationUrl(provider: String): ApiEnvelope<EmailAuthorizationDto> = error("Not used")
    override suspend fun syncEmailConnection(provider: String): ApiEnvelope<EmailSyncDto> = error("Not used")
    override suspend fun emailSyncRun(provider: String, runId: Long): ApiEnvelope<EmailSyncDto> = error("Not used")
    override suspend fun emailCandidates(): ApiEnvelope<List<EmailCandidateDto>> = ApiEnvelope(emptyList())
    override suspend fun reviewEmailCandidate(
        candidateId: String,
        request: com.bsolutions.wallet.core.network.EmailCandidateReviewRequest
    ): ApiEnvelope<EmailCandidateDto> = error("Not used")
    override suspend fun deleteEmailConnection(provider: String) = Unit
}

private class FakeWalletSessionStore : WalletSessionStore {
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

private class FakeLocalDataIsolation : LocalDataIsolation {
    var activeUserId: String? = null
    var guestActivated = false
    var activationFailure = false

    override suspend fun activateUser(userId: String) {
        if (activationFailure) error("local isolation failed")
        activeUserId = userId
    }

    override suspend fun reconcileCurrentSession() = Unit

    override fun activateGuest() {
        guestActivated = true
    }
}
