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
    fun `never sends a stale token to public auth endpoints`() {
        val session = TestSessionStore("stale-token")
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        OkHttpClient.Builder()
            .addInterceptor(WalletAuthInterceptor(session))
            .build()
            .newCall(Request.Builder().url(server.url("/api/v1/auth/login")).build())
            .execute()
            .close()

        // Un token viejo no debe interferir con el login (evita el falso "sesión venció").
        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals("stale-token", session.token)
    }

    @Test
    fun `delayed unauthorized response does not clear a newer token`() {
        val session = TestSessionStore("new-token")

        handleWalletResponse(401, "old-token", session)

        assertEquals("new-token", session.token)
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
    override fun save(token: String, user: AuthUser) {
        this.token = token
        this.user = user
    }

    override fun clear() {
        token = null
        user = null
    }
}
