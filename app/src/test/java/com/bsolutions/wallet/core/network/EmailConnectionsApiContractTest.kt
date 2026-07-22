package com.bsolutions.wallet.core.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EmailConnectionsApiContractTest {
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
    fun `list decodes nullable fields and snake case contract`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"data":[{"provider":"gmail","display_name":"Gmail","status":"connected","email":"ana@example.com","configuration_ready":true,"connected_at":"2026-07-20T10:15:00Z","expires_at":null}]}
        """.trimIndent()))

        val connection = api.emailConnections().data.single()
        val request = server.takeRequest()

        assertEquals("/api/v1/email-connections", request.path)
        assertEquals("Gmail", connection.displayName)
        assertEquals(true, connection.configurationReady)
        assertNull(connection.expiresAt)
    }

    @Test
    fun `authorization and delete use provider endpoint`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"authorization_url":"https://login.example/oauth"}}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        val url = api.emailAuthorizationUrl("microsoft").data.authorizationUrl
        api.deleteEmailConnection("microsoft")
        val authorizationRequest = server.takeRequest()
        val deleteRequest = server.takeRequest()

        assertEquals("https://login.example/oauth", url)
        assertEquals("POST", authorizationRequest.method)
        assertEquals("/api/v1/email-connections/microsoft/authorization-url", authorizationRequest.path)
        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/api/v1/email-connections/microsoft", deleteRequest.path)
    }

    @Test
    fun `sync and candidates use backend contract`() = kotlinx.coroutines.runBlocking {
        // El sync ahora se encola (202) y devuelve el estado del run.
        server.enqueue(MockResponse().setResponseCode(202).setBody("""
            {"data":{"sync_run_id":7,"status":"completed","messages_discovered":2,"messages_created":1,"candidates_created":1}}
        """.trimIndent()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"data":[{"id":"9","provider":"gmail","merchant":"Banco","amount":-1999,"currency":"USD","converted_amount":-122408,"converted_currency":"DOP","exchange_rate_micros":61234567,"exchange_rate_at":"2026-07-20T00:00:00Z","exchange_rate_source":"fawaz-exchange-api-historical","conversion_kind":"historical_estimate","conversion_status":"available","direction":"expense","category_suggestion":"Alimentación","occurred_at":"2026-07-20T14:30:00Z","confidence":90,"status":"pending","subject":"Compra aprobada"}]}
        """.trimIndent()))

        val sync = api.syncEmailConnection("gmail").data
        val candidate = api.emailCandidates().data.single()
        val syncRequest = server.takeRequest()
        val candidatesRequest = server.takeRequest()

        assertEquals("POST", syncRequest.method)
        assertEquals("/api/v1/email-connections/gmail/sync", syncRequest.path)
        assertEquals("completed", sync.status)
        assertEquals(1, sync.candidatesCreated)
        assertEquals("GET", candidatesRequest.method)
        assertEquals("/api/v1/email-candidates", candidatesRequest.path)
        assertEquals(-1999L, candidate.amount)
        assertEquals(-122408L, candidate.convertedAmount)
        assertEquals(61234567L, candidate.exchangeRateMicros)
        assertEquals("historical_estimate", candidate.conversionKind)
        assertEquals("available", candidate.conversionStatus)
        assertEquals("Alimentación", candidate.categorySuggestion)
    }

    @Test
    fun `review candidate uses patch contract and decodes classified result`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"data":{"id":"9","provider":"gmail","merchant":"Amazon","amount":-1999,"currency":"USD","converted_amount":-122408,"converted_currency":"DOP","exchange_rate_micros":61234567,"exchange_rate_at":"2026-07-21T00:02:32Z","exchange_rate_source":"exchangerate-api-open","direction":"expense","category_suggestion":"Compras en línea","occurred_at":"2026-07-20T14:30:00Z","confidence":90,"status":"classified","subject":"Pago aprobado"}}
        """.trimIndent()))

        val candidate = api.reviewEmailCandidate(
            "9",
            EmailCandidateReviewRequest(action = "categorize", category = "Compras en línea", learn = true)
        ).data
        val request = server.takeRequest()

        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/email-candidates/9", request.path)
        assertEquals("classified", candidate.status)
        assertTrue(request.body.readUtf8().contains("\"action\":\"categorize\""))
    }
}
