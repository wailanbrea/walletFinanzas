package com.bsolutions.wallet.core.network

import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Verifica el contrato de red del sync app↔backend (push serializa en snake_case con
 * id de cliente y monto con signo; pull decodifica la página con cursor). Es el punto
 * que se rompe en silencio si las dos capas se desincronizan.
 */
class WalletSyncContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: WalletApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WalletApi::class.java)
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `createAccount sends client id and snake_case fields`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""
            {"data":{"id":"acc-1","name":"Efectivo","balance":10000,"currency":"DOP","institution_name":null,"country_code":"DO","card_last_four":null,"is_active":true}}
        """.trimIndent()))

        val created = api.createAccount(
            CreateAccountRequest(
                id = "acc-1",
                name = "Efectivo",
                balance = 10_000,
                currency = "DOP",
                institutionName = null,
                countryCode = "DO",
                cardLastFour = null
            )
        ).data
        val request = server.takeRequest()
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertEquals("POST", request.method)
        assertEquals("/api/v1/accounts", request.path)
        assertEquals("acc-1", body.get("id").asString)
        assertEquals(10_000L, body.get("balance").asLong)
        assertEquals("DO", body.get("country_code").asString)
        assertEquals("acc-1", created.id)
        assertEquals(10_000L, created.balance)
    }

    @Test
    fun `createTransaction sends idempotency key and signed amount`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""
            {"data":{"id":"srv-9","idempotency_key":"tx-1","account_id":"acc-1","amount":-2500,"currency":"DOP","description":"Compra","category_id":"cat_alimentacion","timestamp":"2026-07-22T14:30:00.000000Z","status":"completed","updated_at":"2026-07-22T14:30:01Z"}}
        """.trimIndent()))

        val created = api.createTransaction(
            CreateTransactionRequest(
                idempotencyKey = "tx-1",
                accountId = "acc-1",
                amount = -2500, // gasto = negativo
                currency = "DOP",
                description = "Compra",
                categoryId = "cat_alimentacion",
                timestamp = "2026-07-22T14:30:00Z"
            )
        ).data
        val request = server.takeRequest()
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertEquals("/api/v1/transactions", request.path)
        assertEquals("tx-1", body.get("idempotency_key").asString)
        assertEquals("acc-1", body.get("account_id").asString)
        assertEquals(-2500L, body.get("amount").asLong)
        assertEquals("cat_alimentacion", body.get("category_id").asString)
        assertEquals("completed", body.get("status").asString)
        assertEquals("tx-1", created.idempotencyKey)
        assertEquals(-2500L, created.amount)
    }

    @Test
    fun `pullTransactions decodes cursor page and next cursor`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"data":[{"id":"srv-9","idempotency_key":"tx-1","account_id":"acc-1","amount":-2500,"currency":"DOP","description":"Compra","category_id":"cat_alimentacion","timestamp":"2026-07-22T14:30:00Z","status":"completed","updated_at":"2026-07-22T14:30:01Z"}],"meta":{"next_cursor":"eyJpZCI6OX0"}}
        """.trimIndent()))

        val page = api.pullTransactions(updatedSince = null, cursor = null)
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("eyJpZCI6OX0", page.meta?.nextCursor)
        assertEquals(1, page.data.size)
        assertEquals(-2500L, page.data.single().amount)
        assertEquals("acc-1", page.data.single().accountId)
    }

    @Test
    fun `pullAccounts tolerates empty page with null cursor`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"data":[],"meta":{"next_cursor":null}}
        """.trimIndent()))

        val page = api.pullAccounts(updatedSince = "2026-07-22T00:00:00Z", cursor = null)
        val request = server.takeRequest()

        // El filtro incremental viaja como query param.
        assertEquals("2026-07-22T00:00:00Z", request.requestUrl?.queryParameter("updated_since"))
        assertEquals(0, page.data.size)
        assertNull(page.meta?.nextCursor)
    }

    @Test
    fun `editing uses PATCH with the client key and deleting uses DELETE`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"data":{"id":"srv-1","account_id":"acc-1","amount":-3000,"currency":"DOP","description":null,"category_id":null,"timestamp":"2026-07-29T00:00:00Z","status":"completed","idempotency_key":"tx-local-1"}}
        """.trimIndent()))

        api.updateTransaction(
            id = "tx-local-1",
            request = UpdateTransactionRequest(
                amount = -3000,
                description = "Reloj",
                categoryId = "cat_compras",
                timestamp = "2026-07-29T00:00:00Z"
            )
        )
        val patch = server.takeRequest()

        assertEquals("PATCH", patch.method)
        // Se direcciona por la clave que genero la app: el id de la fila lo pone el servidor.
        assertEquals("/api/v1/transactions/tx-local-1", patch.path)
        val body = JsonParser.parseString(patch.body.readUtf8()).asJsonObject
        assertEquals(-3000L, body.get("amount").asLong)
        assertEquals("cat_compras", body.get("category_id").asString)

        server.enqueue(MockResponse().setResponseCode(204))
        val deleted = api.deleteTransaction("tx-local-1")
        val delete = server.takeRequest()

        assertEquals(204, deleted.code())
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/transactions/tx-local-1", delete.path)
    }
}
