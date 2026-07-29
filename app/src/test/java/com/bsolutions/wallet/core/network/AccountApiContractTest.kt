package com.bsolutions.wallet.core.network

import com.bsolutions.wallet.data.repository.toAccountEntity
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
    fun `account pull survives a backend that predates the type column`() = runBlocking {
        // El VPS sin la migracion no manda type ni credit_limit. Gson no aplica los
        // valores por defecto de Kotlin (instancia con Unsafe), asi que un campo
        // no-nulo llegaria como null y reventaria al construir AccountEntity.
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"id":"acc-1","name":"Cuenta","balance":1000,"currency":"DOP","institution_name":null,"country_code":"DO","card_last_four":null,"is_active":true}]}"""
        ))

        val account = api.pullAccounts(null, null).data.single()

        assertNull(account.type)
        assertNull(account.creditLimit)
        assertEquals("BANK", account.toAccountEntity("owner-1").type)
    }

    @Test
    fun `a remote credit limit identifies a card even without the type field`() = runBlocking {
        // Traer limite de credito solo tiene sentido en una tarjeta. Si se tomara por
        // cuenta bancaria, su saldo sumaria al Balance Total como dinero propio.
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"id":"acc-2","name":"Visa Gold","balance":0,"currency":"DOP","institution_name":"Banco Popular Dominicano","country_code":"DO","card_last_four":null,"credit_limit":6600000,"is_active":true}]}"""
        ))

        val entity = api.pullAccounts(null, null).data.single().toAccountEntity("owner-1")

        assertEquals("CREDIT_CARD", entity.type)
        assertEquals(6_600_000L, entity.creditLimit)
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
