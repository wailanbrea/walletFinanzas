package com.bsolutions.wallet.presentation.emailconnections

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object EmailOAuthReturnContract {
    const val SCHEME = "walletfinanzas"
    const val HOST = "email-oauth"

    private val providers = setOf("gmail", "microsoft")

    fun matches(rawUri: String?): Boolean = runCatching {
        val uri = URI(rawUri ?: return false)
        val query = uri.rawQuery
            ?.split('&')
            .orEmpty()
            .mapNotNull { item ->
                val parts = item.split('=', limit = 2)
                if (parts.size != 2) null else {
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name()) to
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                }
            }
            .toMap()

        uri.scheme == SCHEME &&
            uri.host == HOST &&
            query["provider"] in providers &&
            query["status"] == "connected"
    }.getOrDefault(false)
}