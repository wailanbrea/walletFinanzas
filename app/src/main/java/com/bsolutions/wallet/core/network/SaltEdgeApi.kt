package com.bsolutions.wallet.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Cliente de la API v6 de Salt Edge (Open Banking Gateway) — solo AIS (lectura).
 * En estado Pending/Test la autenticación es por headers App-id/Secret
 * (la firma RSA solo se exige en Live).
 */
interface SaltEdgeApi {

    @POST("customers")
    suspend fun createCustomer(@Body body: CreateCustomerRequest): DataWrapper<CustomerDto>

    @POST("connections/connect")
    suspend fun createConnectSession(@Body body: ConnectSessionRequest): DataWrapper<ConnectSessionDto>

    @GET("providers")
    suspend fun listProviders(
        @Query("country_code") countryCode: String,
        @Query("per_page") perPage: Int = 200
    ): DataWrapper<List<ProviderDto>>

    @GET("connections")
    suspend fun listConnections(@Query("customer_id") customerId: String): DataWrapper<List<ConnectionDto>>

    @GET("accounts")
    suspend fun listAccounts(
        @Query("connection_id") connectionId: String,
        @Query("per_page") perPage: Int = 100
    ): DataWrapper<List<SaltEdgeAccountDto>>

    // En v6 account_id es obligatorio: los movimientos se listan por cuenta
    @GET("transactions")
    suspend fun listTransactions(
        @Query("connection_id") connectionId: String,
        @Query("account_id") accountId: String,
        @Query("per_page") perPage: Int = 500
    ): DataWrapper<List<SaltEdgeTransactionDto>>
}

// ---------- Envelopes ----------

data class DataWrapper<T>(val data: T)

// ---------- Requests ----------

data class CreateCustomerRequest(val data: CustomerIdentifier)
data class CustomerIdentifier(val identifier: String)

data class ConnectSessionRequest(val data: ConnectSessionData)
data class ConnectSessionData(
    @SerializedName("customer_id") val customerId: String,
    val consent: ConsentData,
    val attempt: AttemptData,
    // Preselecciona el banco: el widget salta directo al login del proveedor
    val provider: ProviderRef? = null
)

data class ProviderRef(val code: String)

// El scope "accounts" ya incluye los saldos en v6
data class ConsentData(val scopes: List<String> = listOf("accounts", "transactions"))
data class AttemptData(
    @SerializedName("return_to") val returnTo: String,
    val locale: String = "es"
)

// ---------- Responses ----------

data class CustomerDto(
    // v6 devuelve "customer_id", no "id"
    @SerializedName("customer_id") val id: String,
    val identifier: String? = null
)

data class ConnectSessionDto(
    @SerializedName("connect_url") val connectUrl: String,
    @SerializedName("expires_at") val expiresAt: String? = null
)

data class ConnectionDto(
    val id: String,
    @SerializedName("provider_code") val providerCode: String? = null,
    @SerializedName("provider_name") val providerName: String? = null,
    @SerializedName("country_code") val countryCode: String? = null,
    val status: String? = null,
    @SerializedName("last_success_at") val lastSuccessAt: String? = null
)

data class SaltEdgeAccountDto(
    val id: String,
    val name: String? = null,
    val nature: String? = null, // account, card, credit_card, savings…
    val balance: Double? = null,
    @SerializedName("currency_code") val currencyCode: String? = null
)

data class ProviderDto(
    val code: String,
    val name: String,
    @SerializedName("country_code") val countryCode: String? = null,
    val mode: String? = null, // oauth | web | api
    val status: String? = null, // active | inactive | disabled
    @SerializedName("logo_url") val logoUrl: String? = null
)

data class SaltEdgeTransactionDto(
    val id: String,
    @SerializedName("account_id") val accountId: String? = null,
    val amount: Double? = null, // negativo = gasto
    @SerializedName("currency_code") val currencyCode: String? = null,
    val description: String? = null,
    @SerializedName("made_on") val madeOn: String? = null, // yyyy-MM-dd
    val category: String? = null
)
