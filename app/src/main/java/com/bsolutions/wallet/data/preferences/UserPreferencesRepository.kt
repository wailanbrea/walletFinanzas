package com.bsolutions.wallet.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserProfilePrefs(
    val userName: String = "Mi Perfil",
    val email: String = "",
    val walletName: String = "Mi Wallet",
    val biometricLockEnabled: Boolean = false,
    val screenCaptureProtectionEnabled: Boolean = false,
    val financialCountryCode: String = "DO",
    /** Modo privacidad: oculta/ofusca los montos (Balance Total, saldos, dígitos de tarjeta). */
    val balancesHidden: Boolean = false
)

/** Contrato mínimo de preferencias que necesita el dashboard. */
interface UserProfilePreferences {
    val profile: Flow<UserProfilePrefs>
    suspend fun setBalancesHidden(hidden: Boolean)
}

/** Perfil local del usuario (sin backend), persistido en DataStore. */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UserProfilePreferences {
    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val EMAIL = stringPreferencesKey("email")
        val WALLET_NAME = stringPreferencesKey("wallet_name")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val SCREEN_CAPTURE_PROTECTION = booleanPreferencesKey("screen_capture_protection_enabled")
        val FINANCIAL_COUNTRY = stringPreferencesKey("financial_country")
        val BALANCES_HIDDEN = booleanPreferencesKey("balances_hidden")
    }

    override val profile: Flow<UserProfilePrefs> = context.userPrefsDataStore.data.map { prefs ->
        UserProfilePrefs(
            userName = prefs[Keys.USER_NAME] ?: "Mi Perfil",
            email = prefs[Keys.EMAIL] ?: "",
            walletName = prefs[Keys.WALLET_NAME] ?: "Mi Wallet",
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            screenCaptureProtectionEnabled = prefs[Keys.SCREEN_CAPTURE_PROTECTION] ?: false,
            financialCountryCode = prefs[Keys.FINANCIAL_COUNTRY] ?: "DO",
            balancesHidden = prefs[Keys.BALANCES_HIDDEN] ?: false
        )
    }

    suspend fun saveProfile(userName: String, email: String, walletName: String, financialCountryCode: String = "DO") {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = userName.trim()
            prefs[Keys.EMAIL] = email.trim()
            prefs[Keys.WALLET_NAME] = walletName.trim()
            prefs[Keys.FINANCIAL_COUNTRY] = financialCountryCode
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_LOCK] = enabled
        }
    }

    suspend fun setScreenCaptureProtectionEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.SCREEN_CAPTURE_PROTECTION] = enabled
        }
    }

    /** Alterna el modo privacidad (ocultar montos). */
    override suspend fun setBalancesHidden(hidden: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.BALANCES_HIDDEN] = hidden
        }
    }

    // --- Salt Edge (sandbox): id del customer creado en su API ---
    private val SALTEDGE_CUSTOMER = stringPreferencesKey("saltedge_customer_id")

    suspend fun getSaltEdgeCustomerId(): String? =
        context.userPrefsDataStore.data.map { it[SALTEDGE_CUSTOMER] }.first()

    suspend fun setSaltEdgeCustomerId(id: String) {
        context.userPrefsDataStore.edit { prefs -> prefs[SALTEDGE_CUSTOMER] = id }
    }
}
