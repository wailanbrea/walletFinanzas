package com.bsolutions.wallet.core.network

import com.bsolutions.wallet.data.repository.WalletSessionStore
import okhttp3.Interceptor
import okhttp3.Response

class WalletAuthInterceptor(
    private val session: WalletSessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Endpoints públicos: NUNCA deben llevar el token (un token viejo/ inválido
        // no debe interferir con el login) ni disparar "sesión venció" ante un 401/422.
        val isPublicAuth = PUBLIC_AUTH_PATHS.any { request.url.encodedPath.endsWith(it) }

        val requestBuilder = request.newBuilder().header("Accept", "application/json")
        val requestToken = session.token?.takeIf { it.isNotBlank() }
        if (!isPublicAuth) {
            requestToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        }

        return chain.proceed(requestBuilder.build()).also { response ->
            if (!isPublicAuth) handleWalletResponse(response.code, requestToken, session)
        }
    }

    private companion object {
        val PUBLIC_AUTH_PATHS = listOf("/auth/login", "/auth/register", "/auth/forgot-password")
    }
}

fun handleWalletResponse(statusCode: Int, session: WalletSessionStore) {
    handleWalletResponse(statusCode, session.token, session)
}

fun handleWalletResponse(
    statusCode: Int,
    rejectedToken: String?,
    session: WalletSessionStore
) {
    // A delayed 401 from an old request must not erase a token saved by a newer login.
    if (statusCode == 401 && rejectedToken != null && session.token == rejectedToken) {
        session.clear()
    }
}
