package com.bsolutions.wallet.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedWalletSessionStore(context: Context) : WalletSessionStore {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override val token: String?
        get() = preferences.getString(KEY_TOKEN, null)

    override val user: AuthUser?
        get() {
            val id = preferences.getString(KEY_USER_ID, null) ?: return null
            val email = preferences.getString(KEY_USER_EMAIL, null) ?: return null
            return AuthUser(uid = id, email = email)
        }

    override fun save(token: String, user: AuthUser) {
        preferences.edit {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, user.uid)
            putString(KEY_USER_EMAIL, user.email)
        }
    }

    override fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val FILE_NAME = "wallet_api_session"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_EMAIL = "user_email"
    }
}
