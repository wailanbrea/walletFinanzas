package com.bsolutions.wallet.core.network

import com.bsolutions.wallet.data.repository.AuthUser
import com.bsolutions.wallet.data.repository.WalletSessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

class WalletApiIntegrationTest {

    @Test
    fun `android client completes backend auth lifecycle`() = runBlocking {
        val configuredUrl = System.getenv("WALLET_API_INTEGRATION_URL")
        assumeTrue("WALLET_API_INTEGRATION_URL is required", !configuredUrl.isNullOrBlank())
        val baseUrl = configuredUrl!!.trimEnd('/') + "/"
        val session = IntegrationSessionStore()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(WalletAuthInterceptor(session))
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WalletApi::class.java)

        val email = "android-functional-${System.currentTimeMillis()}@example.com"
        val password = "Functional123!"
        val registered = api.register(
            RegisterRequest(
                name = "Android Functional",
                email = email,
                password = password,
                passwordConfirmation = password,
                deviceName = "jvm-functional-test"
            )
        ).data

        assertTrue(registered.token.isNotBlank())
        assertEquals(email, registered.user.email)
        session.save(
            registered.token,
            AuthUser(registered.user.id.toString(), registered.user.email)
        )
        assertTrue(api.accounts().data.isEmpty())

        val categoryId = "integration_${UUID.randomUUID()}"
        val category = api.createCategory(
            CreateCategoryRequest(
                id = categoryId,
                name = "Integración",
                icon = "category",
                colorHex = "#90A4AE",
                type = "EXPENSE", isDeleted = false
            )
        ).data
        assertEquals(categoryId, category.id)
        assertFalse(category.isDeleted)

        val accountId = UUID.randomUUID().toString()
        api.createAccount(
            CreateAccountRequest(
                id = accountId,
                name = "Cuenta integración",
                balance = 10_000,
                currency = "DOP",
                institutionName = null,
                countryCode = "DO",
                cardLastFour = null
            )
        )
        val transaction = api.createTransaction(
            CreateTransactionRequest(
                idempotencyKey = UUID.randomUUID().toString(),
                accountId = accountId,
                amount = -500,
                currency = "DOP",
                description = "Prueba de categoría",
                categoryId = categoryId,
                timestamp = "2026-07-22T10:00:00Z"
            )
        ).data
        assertEquals(categoryId, transaction.categoryId)
        assertTrue(api.pullCategories(null, null).data.any { it.id == categoryId })

        val budget = BudgetSyncDto(
            id = UUID.randomUUID().toString(),
            categoryId = categoryId,
            limitAmount = 25_000,
            spentAmount = 500,
            period = "MONTHLY",
            isDeleted = false
        )
        assertEquals(budget.id, api.upsertBudget(budget).data.id)
        assertTrue(api.pullBudgets(null, null).data.any { it.id == budget.id })

        val goal = GoalSyncDto(
            id = UUID.randomUUID().toString(),
            name = "Meta integracion",
            icon = "savings",
            targetAmount = 100_000,
            savedAmount = 10_000,
            targetDate = null,
            isCompleted = false,
            isDeleted = false
        )
        assertEquals(goal.id, api.upsertGoal(goal).data.id)
        assertTrue(api.pullGoals(null, null).data.any { it.id == goal.id })

        val debt = DebtSyncDto(
            id = UUID.randomUUID().toString(),
            name = "Deuda integracion",
            description = "",
            direction = "I_OWE",
            totalAmount = 50_000,
            paidAmount = 5_000,
            dueDate = null,
            isClosed = false,
            isDeleted = false
        )
        assertEquals(debt.id, api.upsertDebt(debt).data.id)
        assertTrue(api.pullDebts(null, null).data.any { it.id == debt.id })

        val plannedPayment = PlannedPaymentSyncDto(
            id = UUID.randomUUID().toString(),
            name = "Internet integracion",
            accountId = accountId,
            categoryId = categoryId,
            amount = 3_500,
            type = "EXPENSE",
            frequency = "MONTHLY",
            nextDueDate = 1_800_000_000_000,
            isActive = true,
            isDeleted = false
        )
        assertEquals(plannedPayment.id, api.upsertPlannedPayment(plannedPayment).data.id)
        assertTrue(api.pullPlannedPayments(null, null).data.any { it.id == plannedPayment.id })

        assertTrue(api.upsertBudget(budget.copy(isDeleted = true)).data.isDeleted)
        assertTrue(api.upsertGoal(goal.copy(isDeleted = true)).data.isDeleted)
        assertTrue(api.upsertDebt(debt.copy(isDeleted = true)).data.isDeleted)
        assertTrue(api.upsertPlannedPayment(plannedPayment.copy(isDeleted = true)).data.isDeleted)

        val tombstone = api.createCategory(
            CreateCategoryRequest(
                id = categoryId,
                name = "Integración",
                icon = "category",
                colorHex = "#90A4AE",
                type = "EXPENSE", isDeleted = true
            )
        ).data
        assertTrue(tombstone.isDeleted)

        api.logout()
        session.clear()

        val loggedIn = api.login(LoginRequest(email, password, "jvm-functional-test")).data
        assertTrue(loggedIn.token.isNotBlank())
        assertEquals(registered.user.id, loggedIn.user.id)

        val recovery = api.forgotPassword(ForgotPasswordRequest(email))
        assertTrue(recovery.message.startsWith("Si el correo está registrado"))
    }
}

private class IntegrationSessionStore : WalletSessionStore {
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
