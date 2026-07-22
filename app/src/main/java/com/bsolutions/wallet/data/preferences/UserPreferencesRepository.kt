@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.bsolutions.wallet.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.data.local.entity.WALLET_GUEST_OWNER_ID
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

val DEFAULT_DASHBOARD_CARD_IDS: Set<String> = setOf(
    "total_balance",
    "cash_flow",
    "expense_structure",
    "recent_transactions"
)

data class UserProfilePrefs(
    val userName: String = "Mi Perfil",
    val email: String = "",
    val walletName: String = "Mi Wallet",
    val biometricLockEnabled: Boolean = false,
    val screenCaptureProtectionEnabled: Boolean = false,
    val financialCountryCode: String = "DO",
    /** Modo privacidad: oculta/ofusca los montos (Balance Total, saldos, dígitos de tarjeta). */
    val balancesHidden: Boolean = false,
    val dashboardCardIds: Set<String> = DEFAULT_DASHBOARD_CARD_IDS
)

/** Contrato mínimo de preferencias que necesita el dashboard. */
interface UserProfilePreferences {
    val profile: Flow<UserProfilePrefs>
    suspend fun setBalancesHidden(hidden: Boolean)
    suspend fun setDashboardCardIds(cardIds: Set<String>)
}

/** Perfil local del usuario (sin backend), persistido en DataStore. */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ownerScope: WalletOwnerScope
) : UserProfilePreferences {
    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val EMAIL = stringPreferencesKey("email")
        val WALLET_NAME = stringPreferencesKey("wallet_name")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val SCREEN_CAPTURE_PROTECTION = booleanPreferencesKey("screen_capture_protection_enabled")
        val FINANCIAL_COUNTRY = stringPreferencesKey("financial_country")
        val BALANCES_HIDDEN = booleanPreferencesKey("balances_hidden")
        val DASHBOARD_CARD_IDS = stringSetPreferencesKey("dashboard_card_ids")
    }

    private data class ScopedKeys(
        val userName: Preferences.Key<String>,
        val email: Preferences.Key<String>,
        val walletName: Preferences.Key<String>,
        val biometricLock: Preferences.Key<Boolean>,
        val screenCaptureProtection: Preferences.Key<Boolean>,
        val financialCountry: Preferences.Key<String>,
        val balancesHidden: Preferences.Key<Boolean>,
        val dashboardCardIds: Preferences.Key<Set<String>>,
        val saltEdgeCustomer: Preferences.Key<String>
    )

    private fun scopedKeys(ownerId: String): ScopedKeys {
        val suffix = ownerId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return ScopedKeys(
            userName = stringPreferencesKey("user_name_$suffix"),
            email = stringPreferencesKey("email_$suffix"),
            walletName = stringPreferencesKey("wallet_name_$suffix"),
            biometricLock = booleanPreferencesKey("biometric_lock_enabled_$suffix"),
            screenCaptureProtection = booleanPreferencesKey("screen_capture_protection_enabled_$suffix"),
            financialCountry = stringPreferencesKey("financial_country_$suffix"),
            balancesHidden = booleanPreferencesKey("balances_hidden_$suffix"),
            dashboardCardIds = stringSetPreferencesKey("dashboard_card_ids_$suffix"),
            saltEdgeCustomer = stringPreferencesKey("saltedge_customer_id_$suffix")
        )
    }

    override val profile: Flow<UserProfilePrefs> = ownerScope.ownerId.flatMapLatest { ownerId ->
        context.userPrefsDataStore.data.map { prefs ->
            val keys = scopedKeys(ownerId)
            val legacy = ownerId == WALLET_GUEST_OWNER_ID
            UserProfilePrefs(
                userName = prefs[keys.userName] ?: prefs[Keys.USER_NAME].takeIf { legacy } ?: "Mi Perfil",
                email = prefs[keys.email] ?: prefs[Keys.EMAIL].takeIf { legacy } ?: "",
                walletName = prefs[keys.walletName] ?: prefs[Keys.WALLET_NAME].takeIf { legacy } ?: "Mi Wallet",
                biometricLockEnabled = prefs[keys.biometricLock] ?: prefs[Keys.BIOMETRIC_LOCK].takeIf { legacy } ?: false,
                screenCaptureProtectionEnabled = prefs[keys.screenCaptureProtection]
                    ?: prefs[Keys.SCREEN_CAPTURE_PROTECTION].takeIf { legacy }
                    ?: false,
                financialCountryCode = prefs[keys.financialCountry]
                    ?: prefs[Keys.FINANCIAL_COUNTRY].takeIf { legacy }
                    ?: "DO",
                balancesHidden = prefs[keys.balancesHidden] ?: prefs[Keys.BALANCES_HIDDEN].takeIf { legacy } ?: false,
                dashboardCardIds = prefs[keys.dashboardCardIds]?.toSet()
                    ?: prefs[Keys.DASHBOARD_CARD_IDS].takeIf { legacy }?.toSet()
                    ?: DEFAULT_DASHBOARD_CARD_IDS
            )
        }
    }

    suspend fun saveProfile(userName: String, email: String, walletName: String, financialCountryCode: String = "DO") {
        context.userPrefsDataStore.edit { prefs ->
            val keys = scopedKeys(ownerScope.currentOwnerId())
            prefs[keys.userName] = userName.trim()
            prefs[keys.email] = email.trim()
            prefs[keys.walletName] = walletName.trim()
            prefs[keys.financialCountry] = financialCountryCode
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[scopedKeys(ownerScope.currentOwnerId()).biometricLock] = enabled
        }
    }

    suspend fun setScreenCaptureProtectionEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[scopedKeys(ownerScope.currentOwnerId()).screenCaptureProtection] = enabled
        }
    }

    /** Alterna el modo privacidad (ocultar montos). */
    override suspend fun setBalancesHidden(hidden: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[scopedKeys(ownerScope.currentOwnerId()).balancesHidden] = hidden
        }
    }

    override suspend fun setDashboardCardIds(cardIds: Set<String>) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[scopedKeys(ownerScope.currentOwnerId()).dashboardCardIds] = cardIds
        }
    }

    // --- Salt Edge (sandbox): id del customer creado en su API ---
    private val SALTEDGE_CUSTOMER = stringPreferencesKey("saltedge_customer_id")

    suspend fun getSaltEdgeCustomerId(): String? =
        context.userPrefsDataStore.data.map { prefs ->
            val ownerId = ownerScope.currentOwnerId()
            prefs[scopedKeys(ownerId).saltEdgeCustomer]
                ?: prefs[SALTEDGE_CUSTOMER].takeIf { ownerId == WALLET_GUEST_OWNER_ID }
        }.first()

    suspend fun setSaltEdgeCustomerId(id: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[scopedKeys(ownerScope.currentOwnerId()).saltEdgeCustomer] = id
        }
    }

    suspend fun mergeGuestInto(targetOwnerId: String) {
        val guest = scopedKeys(WALLET_GUEST_OWNER_ID)
        val target = scopedKeys(targetOwnerId)
        context.userPrefsDataStore.edit { prefs ->
            fun <T> copyIfMissing(targetKey: Preferences.Key<T>, guestKey: Preferences.Key<T>, legacyKey: Preferences.Key<T>) {
                if (prefs[targetKey] == null) prefs[guestKey]?.let { prefs[targetKey] = it }
                    ?: prefs[legacyKey]?.let { prefs[targetKey] = it }
                prefs.remove(guestKey)
                prefs.remove(legacyKey)
            }

            copyIfMissing(target.userName, guest.userName, Keys.USER_NAME)
            copyIfMissing(target.email, guest.email, Keys.EMAIL)
            copyIfMissing(target.walletName, guest.walletName, Keys.WALLET_NAME)
            copyIfMissing(target.biometricLock, guest.biometricLock, Keys.BIOMETRIC_LOCK)
            copyIfMissing(target.screenCaptureProtection, guest.screenCaptureProtection, Keys.SCREEN_CAPTURE_PROTECTION)
            copyIfMissing(target.financialCountry, guest.financialCountry, Keys.FINANCIAL_COUNTRY)
            copyIfMissing(target.balancesHidden, guest.balancesHidden, Keys.BALANCES_HIDDEN)
            copyIfMissing(target.dashboardCardIds, guest.dashboardCardIds, Keys.DASHBOARD_CARD_IDS)
            copyIfMissing(target.saltEdgeCustomer, guest.saltEdgeCustomer, SALTEDGE_CUSTOMER)
        }
    }
}
