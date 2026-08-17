package com.bsolutions.wallet.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private var inMemoryToken: String? = null
    private var inMemoryUser: AuthUser? = null

    private fun readStoredUser(): AuthUser? {
        val rem = preferences.getBoolean(KEY_REMEMBER_SESSION, true)
        if (!rem) return null
        val id = preferences.getString(KEY_USER_ID, null) ?: return null
        val email = preferences.getString(KEY_USER_EMAIL, null) ?: return null
        return AuthUser(
            uid = id,
            email = email,
            name = preferences.getString(KEY_USER_NAME, null).orEmpty()
        )
    }

    private val _userFlow = MutableStateFlow(readStoredUser())
    override val userFlow: StateFlow<AuthUser?> = _userFlow.asStateFlow()

    override val rememberSession: Boolean
        get() = preferences.getBoolean(KEY_REMEMBER_SESSION, true)

    override val token: String?
        get() = inMemoryToken ?: if (rememberSession) preferences.getString(KEY_TOKEN, null) else null

    override val user: AuthUser?
        get() = inMemoryUser ?: readStoredUser()

    override fun save(token: String, user: AuthUser, rememberSession: Boolean) {
        inMemoryToken = token
        inMemoryUser = user
        preferences.edit(commit = true) {
            putBoolean(KEY_REMEMBER_SESSION, rememberSession)
            if (rememberSession) {
                putString(KEY_TOKEN, token)
                putString(KEY_USER_ID, user.uid)
                putString(KEY_USER_EMAIL, user.email)
                putString(KEY_USER_NAME, user.name)
            } else {
                remove(KEY_TOKEN)
                remove(KEY_USER_ID)
                remove(KEY_USER_EMAIL)
                remove(KEY_USER_NAME)
            }
        }
        _userFlow.value = user
    }

    override fun clear() {
        inMemoryToken = null
        inMemoryUser = null
        preferences.edit(commit = true) { clear() }
        _userFlow.value = null
    }

    private companion object {
        const val FILE_NAME = "wallet_api_session"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_NAME = "user_name"
        const val KEY_REMEMBER_SESSION = "remember_session"
    }
}
