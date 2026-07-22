package com.bsolutions.wallet.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CategoryApiContractTest {
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
    fun `push serializes stable id style and tombstone`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":{"id":"cat_transporte","name":"Movilidad","icon":"directions_car","color_hex":"#64B5F6","is_deleted":true,"updated_at":"2026-07-22T10:00:00Z"}}"""
        ))

        val result = api.createCategory(
            CreateCategoryRequest(
                id = "cat_transporte",
                name = "Movilidad",
                icon = "directions_car",
                colorHex = "#64B5F6",
                isDeleted = true
            )
        ).data
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("POST", request.method)
        assertEquals("/api/v1/categories", request.path)
        assertTrue(body.contains("\"id\":\"cat_transporte\""))
        assertTrue(body.contains("\"is_deleted\":true"))
        assertTrue(result.isDeleted)
    }

    @Test
    fun `pull decodes cursor page and active category`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"id":"custom-1","name":"Negocio","icon":"work","color_hex":"#123456","is_deleted":false,"updated_at":"2026-07-22T10:00:00Z"}],"meta":{"next_cursor":"next-token"}}"""
        ))

        val page = api.pullCategories(null, null, 200)
        val request = server.takeRequest()

        assertEquals("/api/v1/categories?per_page=200", request.path)
        assertEquals("next-token", page.meta?.nextCursor)
        assertEquals("custom-1", page.data.single().id)
        assertFalse(page.data.single().isDeleted)
    }
}
