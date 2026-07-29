package com.bsolutions.wallet.core.network

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AccountApiContractTest {
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

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `account pull decodes backend type and nullable credit limit`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"id":"credit-1","name":"Tarjeta","type":"CREDIT_CARD","balance":-2500,"currency":"DOP","institution_name":"Banco","country_code":"DO","card_last_four":"1234","credit_limit":150000,"is_active":true}]}"""
        ))

        val account = api.pullAccounts(null, null).data.single()

        assertEquals("CREDIT_CARD", account.type)
        assertEquals(150_000L, account.creditLimit)
    }

    @Test
    fun `account push serializes type and nullable credit limit`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"data":{"id":"credit-1","name":"Tarjeta","type":"CREDIT_CARD","balance":0,"currency":"DOP","institution_name":null,"country_code":"DO","card_last_four":null,"credit_limit":null,"is_active":true}}"""
        ))

        api.createAccount(
            CreateAccountRequest(
                id = "credit-1",
                name = "Tarjeta",
                balance = 0,
                currency = "DOP",
                institutionName = null,
                countryCode = "DO",
                cardLastFour = null,
                type = "CREDIT_CARD",
                creditLimit = null
            )
        )
        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject

        assertEquals("CREDIT_CARD", body.get("type").asString)
        assertNull(body.get("credit_limit"))
    }
}
