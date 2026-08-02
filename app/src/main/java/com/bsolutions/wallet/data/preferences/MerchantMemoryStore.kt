package com.bsolutions.wallet.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.merchantMemoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "merchant_memory")

@Singleton
class MerchantMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun learn(merchant: String, categoryId: String) {
        val norm = normalizeMerchant(merchant)
        if (norm.isEmpty() || categoryId.isEmpty()) return
        val key = stringPreferencesKey("m_$norm")
        context.merchantMemoryDataStore.edit { prefs ->
            prefs[key] = categoryId
        }
    }

    suspend fun suggestCategory(merchant: String): String? {
        val norm = normalizeMerchant(merchant)
        if (norm.isEmpty()) return null
        val key = stringPreferencesKey("m_$norm")
        return context.merchantMemoryDataStore.data.map { prefs ->
            prefs[key]
        }.first()
    }

    private fun normalizeMerchant(merchant: String): String {
        val nfd = Normalizer.normalize(merchant, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
    }
}
