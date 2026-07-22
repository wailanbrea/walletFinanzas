package com.bsolutions.wallet.core.database

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Passphrase de la base de datos cifrada (SQLCipher).
 *
 * Se genera una clave aleatoria de 256 bits la primera vez y se guarda en
 * EncryptedSharedPreferences (respaldada por Android Keystore). La clave nunca
 * sale del dispositivo y no es derivable del usuario.
 */
object DatabaseKeyProvider {

    private const val PREFS_FILE = "wallet_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"
    private const val KEY_DB_ENCRYPTED = "db_encrypted"

    private fun securePrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = securePrefs(context)
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) return existing.toByteArray(Charsets.ISO_8859_1)

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val encoded = String(bytes, Charsets.ISO_8859_1)
        prefs.edit { putString(KEY_DB_PASSPHRASE, encoded) }
        return bytes
    }

    /**
     * Migración única (fase beta): si la BD existente se creó sin cifrar,
     * SQLCipher no puede abrirla, así que se elimina y se recrea cifrada.
     * Los datos de prueba se pierden una sola vez; documentado en el plan.
     */
    fun ensureEncryptedDatabase(context: Context, databaseName: String) {
        val prefs = securePrefs(context)
        if (!prefs.getBoolean(KEY_DB_ENCRYPTED, false)) {
            context.deleteDatabase(databaseName)
            prefs.edit { putBoolean(KEY_DB_ENCRYPTED, true) }
        }
    }
}
