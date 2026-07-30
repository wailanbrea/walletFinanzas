package com.bsolutions.wallet.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

data class ApiEnvelope<T>(val data: T)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    @SerializedName("password_confirmation") val passwordConfirmation: String,
    @SerializedName("device_name") val deviceName: String
)

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("device_name") val deviceName: String
)

data class ForgotPasswordRequest(val email: String)

data class AuthPayload(
    val token: String,
    val user: AuthUserDto
)

data class AuthUserDto(
    val id: Long,
    val name: String,
    val email: String
)

data class AccountDto(
    val id: String,
    val name: String,
    val balance: Long,
    val currency: String,
    @SerializedName("institution_name") val institutionName: String?,
    @SerializedName("country_code") val countryCode: String,
    @SerializedName("card_last_four") val cardLastFour: String?,
    @SerializedName("is_active") val isActive: Boolean,
    // Nullable a proposito: un backend anterior a la migracion de type/credit_limit no
    // envia estas claves. Gson no aplica los valores por defecto de Kotlin (instancia
    // con Unsafe, saltandose el constructor), asi que un `= "BANK"` aqui llegaria como
    // null y reventaria al construir AccountEntity. El defecto se aplica al mapear.
    val type: String? = null,
    @SerializedName("credit_limit") val creditLimit: Long? = null
)

data class EmailConnectionDto(
    val provider: String,
    @SerializedName("display_name") val displayName: String,
    val status: String,
    val email: String?,
    @SerializedName("configuration_ready") val configurationReady: Boolean,
    @SerializedName("connected_at") val connectedAt: String?,
    @SerializedName("expires_at") val expiresAt: String?
)

data class EmailAuthorizationDto(
    @SerializedName("authorization_url") val authorizationUrl: String
)

data class EmailSyncDto(
    @SerializedName("sync_run_id") val syncRunId: Long,
    // El sync es asíncrono: 'queued' | 'running' | 'completed' | 'failed'.
    @SerializedName("status") val status: String? = null,
    @SerializedName("messages_discovered") val messagesDiscovered: Int = 0,
    @SerializedName("messages_created") val messagesCreated: Int = 0,
    @SerializedName("candidates_created") val candidatesCreated: Int = 0,
    @SerializedName("error_code") val errorCode: String? = null,
    @SerializedName("conversions_backfilled") val conversionsBackfilled: Int = 0
)

data class EmailCandidateDto(
    val id: String,
    val provider: String,
    val merchant: String?,
    @SerializedName("card_last_four") val cardLastFour: String? = null,
    val amount: Long,
    val currency: String,
    val direction: String,
    @SerializedName("category_suggestion") val categorySuggestion: String?,
    @SerializedName("occurred_at") val occurredAt: String,
    val confidence: Int,
    val status: String,
    val subject: String?,
    @SerializedName("duplicate_of_id") val duplicateOfId: String? = null,
    @SerializedName("converted_amount") val convertedAmount: Long? = null,
    @SerializedName("converted_currency") val convertedCurrency: String? = null,
    @SerializedName("exchange_rate_micros") val exchangeRateMicros: Long? = null,
    @SerializedName("exchange_rate_at") val exchangeRateAt: String? = null,
    @SerializedName("exchange_rate_source") val exchangeRateSource: String? = null,
    @SerializedName("conversion_kind") val conversionKind: String? = null,
    @SerializedName("conversion_status") val conversionStatus: String? = null
)

data class EmailCandidateReviewRequest(
    val action: String,
    val category: String? = null,
    val learn: Boolean = true,
    /** Cual es el candidato bueno cuando se marca este como duplicado. */
    @SerializedName("duplicate_of_id") val duplicateOfId: String? = null
)

data class MessageResponse(val message: String)

// ---- Sync (push): el cliente genera los ids/idempotency-key ----

data class CreateAccountRequest(
    val id: String,
    val name: String,
    val balance: Long,
    val currency: String,
    @SerializedName("institution_name") val institutionName: String?,
    @SerializedName("country_code") val countryCode: String,
    @SerializedName("card_last_four") val cardLastFour: String?,
    val type: String = "BANK",
    @SerializedName("credit_limit") val creditLimit: Long? = null,
    // Una cuenta borrada se sube como inactiva: el backend hace updateOrCreate por id,
    // asi que esto es la lapida que replica el borrado en los demas dispositivos.
    @SerializedName("is_active") val isActive: Boolean = true
)

data class UpdateTransactionRequest(
    val amount: Long,
    val description: String?,
    @SerializedName("category_id") val categoryId: String?,
    val timestamp: String
)

data class CreateTransactionRequest(
    @SerializedName("idempotency_key") val idempotencyKey: String,
    @SerializedName("account_id") val accountId: String,
    val amount: Long, // con signo (income +, gasto -)
    val currency: String,
    val description: String?,
    @SerializedName("category_id") val categoryId: String?,
    val timestamp: String, // ISO-8601 UTC
    val status: String = "completed"
)

data class CreateCategoryRequest(
    val id: String,
    val name: String,
    val icon: String,
    @SerializedName("color_hex") val colorHex: String,
    @SerializedName("is_deleted") val isDeleted: Boolean
)

data class CategoryDto(
    val id: String,
    val name: String,
    val icon: String,
    @SerializedName("color_hex") val colorHex: String,
    @SerializedName("is_deleted") val isDeleted: Boolean,
    @SerializedName("updated_at") val updatedAt: String?
)

data class BudgetSyncDto(
    val id: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("limit_amount") val limitAmount: Long,
    @SerializedName("spent_amount") val spentAmount: Long,
    val period: String,
    @SerializedName("is_deleted") val isDeleted: Boolean,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class GoalSyncDto(
    val id: String,
    val name: String,
    val icon: String,
    @SerializedName("target_amount") val targetAmount: Long,
    @SerializedName("saved_amount") val savedAmount: Long,
    @SerializedName("target_date") val targetDate: Long?,
    @SerializedName("is_completed") val isCompleted: Boolean,
    @SerializedName("is_deleted") val isDeleted: Boolean,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class DebtSyncDto(
    val id: String,
    val name: String,
    val description: String,
    val direction: String,
    @SerializedName("total_amount") val totalAmount: Long,
    @SerializedName("paid_amount") val paidAmount: Long,
    @SerializedName("due_date") val dueDate: Long?,
    @SerializedName("is_closed") val isClosed: Boolean,
    @SerializedName("is_deleted") val isDeleted: Boolean,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class PlannedPaymentSyncDto(
    val id: String,
    val name: String,
    @SerializedName("account_id") val accountId: String,
    @SerializedName("category_id") val categoryId: String,
    val amount: Long,
    val type: String,
    val frequency: String,
    @SerializedName("next_due_date") val nextDueDate: Long,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_deleted") val isDeleted: Boolean,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class TransactionDto(
    val id: String,
    @SerializedName("idempotency_key") val idempotencyKey: String?,
    @SerializedName("account_id") val accountId: String,
    val amount: Long,
    val currency: String,
    val description: String?,
    @SerializedName("category_id") val categoryId: String?,
    val timestamp: String?,
    val status: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

/** Página con cursor (Laravel cursorPaginate): data + meta.next_cursor. */
data class CursorPage<T>(
    val data: List<T>,
    val meta: CursorMeta? = null
)

data class CursorMeta(
    @SerializedName("next_cursor") val nextCursor: String? = null
)

interface WalletApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiEnvelope<AuthPayload>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<AuthPayload>

    @POST("auth/logout")
    suspend fun logout(): MessageResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): MessageResponse

    @GET("accounts")
    suspend fun accounts(): ApiEnvelope<List<AccountDto>>

    // --- Sync push ---
    @POST("accounts")
    suspend fun createAccount(@Body request: CreateAccountRequest): ApiEnvelope<AccountDto>

    @POST("transactions")
    suspend fun createTransaction(@Body request: CreateTransactionRequest): ApiEnvelope<TransactionDto>

    // Corregir y borrar necesitan su propia puerta: createTransaction es inmutable y
    // responde 409 si la misma clave de idempotencia vuelve con otros valores.
    @PATCH("transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: String,
        @Body request: UpdateTransactionRequest
    ): ApiEnvelope<TransactionDto>

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: String): retrofit2.Response<Unit>

    @POST("categories")
    suspend fun createCategory(@Body request: CreateCategoryRequest): ApiEnvelope<CategoryDto>

    @POST("budgets")
    suspend fun upsertBudget(@Body request: BudgetSyncDto): ApiEnvelope<BudgetSyncDto>

    @POST("goals")
    suspend fun upsertGoal(@Body request: GoalSyncDto): ApiEnvelope<GoalSyncDto>

    @POST("debts")
    suspend fun upsertDebt(@Body request: DebtSyncDto): ApiEnvelope<DebtSyncDto>

    @POST("planned-payments")
    suspend fun upsertPlannedPayment(
        @Body request: PlannedPaymentSyncDto
    ): ApiEnvelope<PlannedPaymentSyncDto>

    // --- Sync pull (delta por cursor) ---
    @GET("accounts")
    suspend fun pullAccounts(
        @Query("updated_since") updatedSince: String?,
        @Query("cursor") cursor: String?,
        @Query("per_page") perPage: Int = 200
    ): CursorPage<AccountDto>

    @GET("transactions")
    suspend fun pullTransactions(
        @Query("updated_since") updatedSince: String?,
        @Query("cursor") cursor: String?,
        @Query("per_page") perPage: Int = 200
    ): CursorPage<TransactionDto>

    @GET("categories")
    suspend fun pullCategories(
        @Query("updated_since") updatedSince: String?,
        @Query("cursor") cursor: String?,
        @Query("per_page") perPage: Int = 200
    ): CursorPage<CategoryDto>

    @GET("budgets")
    suspend fun pullBudgets(
        @Query("updated_since") updatedSince: String?,
        @Query("cursor") cursor: String?,
        @Query("per_page") perPage: Int = 200
    ): CursorPage<BudgetSyncDto>

    @GET("goals")
    suspend fun pullGoals(
        @Query("updated_since") updatedSince: String?,
        @Query("cursor") cursor: String?,
        @Query("per_page") perPage: Int = 200
    ): CursorPage<GoalSyncDto>

    @GET("debts")
    suspend fun pullDebts(
        @Query("updated_since") updatedSince: String?,
        @Query("cursor") cursor: String?,
        @Query("per_page") perPage: Int = 200
    ): CursorPage<DebtSyncDto>

    @GET("planned-payments")
    suspend fun pullPlannedPayments(
        @Query("updated_since") updatedSince: String?,
        @Query("cursor") cursor: String?,
        @Query("per_page") perPage: Int = 200
    ): CursorPage<PlannedPaymentSyncDto>

    @GET("email-connections")
    suspend fun emailConnections(): ApiEnvelope<List<EmailConnectionDto>>

    @POST("email-connections/{provider}/authorization-url")
    suspend fun emailAuthorizationUrl(
        @Path("provider") provider: String
    ): ApiEnvelope<EmailAuthorizationDto>

    @POST("email-connections/{provider}/sync")
    suspend fun syncEmailConnection(
        @Path("provider") provider: String
    ): ApiEnvelope<EmailSyncDto>

    @GET("email-connections/{provider}/sync-runs/{run}")
    suspend fun emailSyncRun(
        @Path("provider") provider: String,
        @Path("run") runId: Long
    ): ApiEnvelope<EmailSyncDto>

    @GET("email-candidates")
    suspend fun emailCandidates(): ApiEnvelope<List<EmailCandidateDto>>

    @PATCH("email-candidates/{candidate}")
    suspend fun reviewEmailCandidate(
        @Path("candidate") candidateId: String,
        @Body request: EmailCandidateReviewRequest
    ): ApiEnvelope<EmailCandidateDto>

    @DELETE("email-connections/{provider}")
    suspend fun deleteEmailConnection(@Path("provider") provider: String)
}
