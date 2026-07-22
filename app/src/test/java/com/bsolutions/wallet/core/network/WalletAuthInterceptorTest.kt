package com.bsolutions.wallet.core.network

import com.bsolutions.wallet.data.repository.AuthUser
import com.bsolutions.wallet.data.repository.WalletSessionStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test

class WalletAuthInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds bearer token when session exists`() {
        val session = TestSessionStore("secret-token")
        server.enqueue(MockResponse().setBody("{}"))

        OkHttpClient.Builder()
            .addInterceptor(WalletAuthInterceptor(session))
            .build()
            .newCall(Request.Builder().url(server.url("/accounts")).build())
            .execute()
            .close()

        val request = server.takeRequest()
        assertEquals("Bearer secret-token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `does not add authorization header without session`() {
        server.enqueue(MockResponse().setBody("{}"))

        OkHttpClient.Builder()
            .addInterceptor(WalletAuthInterceptor(TestSessionStore(null)))
            .build()
            .newCall(Request.Builder().url(server.url("/health")).build())
            .execute()
            .close()

        assertNull(server.takeRequest().headers["Authorization"])
    }
}

private class TestSessionStore(override var token: String?) : WalletSessionStore {
    override var user: AuthUser? = null
    override fun save(token: String, user: AuthUser) = Unit
    override fun clear() = Unit
}
