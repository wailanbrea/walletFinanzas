@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.bsolutions.wallet.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    "recent_transactions",
    "account_balances"
)

/**
 * Versión del respaldo de sincronización. Subirla hace que se repita una vez.
 *
 * v2: la primera versión no encolaba las cuentas borradas, así que sus lápidas nunca
 * llegaban al servidor y un borrado hecho en un teléfono no se replicaba.
 *
 * v3: los movimientos atados a una deuda se subieron cuando el servidor todavía no
 * guardaba ese vínculo, así que allá quedaron sueltos y ya no había operación pendiente
 * que los reenviara. Sin repetir el respaldo, el enlace se quedaría para siempre en el
 * teléfono donde se creó.
 *
 * No se sube para reenviar un cambio: el respaldo encola TODOS los movimientos del
 * teléfono y el push va antes que el pull, así que un teléfono con datos viejos pisaría
 * en el servidor lo que otro acaba de corregir. Sirve para lo que nunca se subió, no
 * para repartir una corrección; para eso está la cola normal.
 */
const val SYNC_BACKFILL_VERSION = 3

/** Nombre mostrado mientras el perfil no traiga uno real del backend. */
const val DEFAULT_USER_NAME = "Mi Perfil"

data class UserProfilePrefs(
    val userName: String = DEFAULT_USER_NAME,
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
        val saltEdgeCustomer: Preferences.Key<String>,
        val syncBackfillVersion: Preferences.Key<Int>
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
            saltEdgeCustomer = stringPreferencesKey("saltedge_customer_id_$suffix"),
            syncBackfillVersion = intPreferencesKey("sync_backfill_version_$suffix")
        )
    }

    override val profile: Flow<UserProfilePrefs> = ownerScope.ownerId.flatMapLatest { ownerId ->
        context.userPrefsDataStore.data.map { prefs ->
            val keys = scopedKeys(ownerId)
            val legacy = ownerId == WALLET_GUEST_OWNER_ID
            UserProfilePrefs(
                userName = prefs[keys.userName] ?: prefs[Keys.USER_NAME].takeIf { legacy } ?: DEFAULT_USER_NAME,
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

    /**
     * Marca si ya se encolaron para subida las cuentas y movimientos que existían antes
     * de que hubiera cola de sincronización. Se guarda por propietario porque cada cuenta
     * de usuario tiene su propio espacio de datos.
     */
    suspend fun isSyncBackfillDone(): Boolean =
        context.userPrefsDataStore.data.first()[scopedKeys(ownerScope.currentOwnerId()).syncBackfillVersion]
            ?.let { it >= SYNC_BACKFILL_VERSION } ?: false

    suspend fun markSyncBackfillDone() {
        context.userPrefsDataStore.edit { prefs ->
            prefs[scopedKeys(ownerScope.currentOwnerId()).syncBackfillVersion] = SYNC_BACKFILL_VERSION
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

    // --- Recordar sesión / credenciales de Login ---
    private val KEY_REMEMBER_SESSION = booleanPreferencesKey("auth_remember_session")
    private val KEY_REMEMBERED_EMAIL = stringPreferencesKey("auth_remembered_email")

    val rememberSession: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_REMEMBER_SESSION] ?: true
    }

    val rememberedEmail: Flow<String> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_REMEMBERED_EMAIL].orEmpty()
    }

    suspend fun setRememberSession(remember: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_REMEMBER_SESSION] = remember
        }
    }

    suspend fun setRememberedEmail(email: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_REMEMBERED_EMAIL] = email
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
