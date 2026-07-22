package com.bsolutions.wallet.core.network

import com.bsolutions.wallet.data.repository.WalletSessionStore
import okhttp3.Interceptor
import okhttp3.Response

class WalletAuthInterceptor(
    private val session: WalletSessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
            .header("Accept", "application/json")

        session.token
            ?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("Authorization", "Bearer $it") }

        return chain.proceed(requestBuilder.build()).also { response ->
            handleWalletResponse(response.code, session)
        }
    }
}

fun handleWalletResponse(statusCode: Int, session: WalletSessionStore) {
    if (statusCode == 401) session.clear()
}
