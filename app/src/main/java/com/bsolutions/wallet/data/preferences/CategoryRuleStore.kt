@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.bsolutions.wallet.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bsolutions.wallet.core.common.CategoryRuleRepository
import com.bsolutions.wallet.core.common.CustomCategoryRule
import com.bsolutions.wallet.core.common.ExpenseCategorizer
import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.data.local.entity.WALLET_GUEST_OWNER_ID
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.categoryRulesDataStore: DataStore<Preferences> by preferencesDataStore(name = "category_rules")

/**
 * Reglas de categorización del usuario, persistidas en DataStore como
 * "palabra||categoryId". La palabra se guarda normalizada (minúsculas, sin acentos)
 * para que la comparación del categorizador sea directa.
 */
class CategoryRuleStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ownerScope: WalletOwnerScope
) : CategoryRuleRepository {

    override val rules: Flow<List<CustomCategoryRule>> =
        ownerScope.ownerId.flatMapLatest { ownerId ->
            context.categoryRulesDataStore.data.map { prefs ->
                val current = prefs[keyFor(ownerId)]
                    ?: prefs[KEY_RULES].takeIf { ownerId == WALLET_GUEST_OWNER_ID }
                    ?: emptySet()
                current.mapNotNull { raw ->
                        val parts = raw.split(SEPARATOR, limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) {
                            CustomCategoryRule(keyword = parts[0], categoryId = parts[1])
                        } else null
                    }
                    .sortedBy { it.keyword }
            }
        }

    override suspend fun add(keyword: String, categoryId: String) {
        val normalized = sanitize(keyword)
        if (normalized.isBlank() || categoryId.isBlank()) return
        context.categoryRulesDataStore.edit { prefs ->
            val key = keyFor(ownerScope.currentOwnerId())
            val current = prefs[key] ?: emptySet()
            // Una regla por palabra: reemplaza si ya existía.
            prefs[key] = current
                .filterNot { it.substringBefore(SEPARATOR) == normalized }
                .toSet() + "$normalized$SEPARATOR$categoryId"
        }
    }

    override suspend fun remove(keyword: String) {
        val normalized = sanitize(keyword)
        context.categoryRulesDataStore.edit { prefs ->
            val key = keyFor(ownerScope.currentOwnerId())
            val current = prefs[key] ?: emptySet()
            prefs[key] = current.filterNot { it.substringBefore(SEPARATOR) == normalized }.toSet()
        }
    }

    override suspend fun removeByCategory(categoryId: String) {
        if (categoryId.isBlank()) return
        context.categoryRulesDataStore.edit { prefs ->
            val key = keyFor(ownerScope.currentOwnerId())
            val current = prefs[key] ?: emptySet()
            prefs[key] = current.filterNot { raw ->
                raw.substringAfter(SEPARATOR, missingDelimiterValue = "") == categoryId
            }.toSet()
        }
    }

    private fun sanitize(keyword: String): String =
        ExpenseCategorizer.normalizeText(keyword).replace(SEPARATOR, " ").trim()

    suspend fun mergeGuestInto(targetOwnerId: String) {
        context.categoryRulesDataStore.edit { prefs ->
            val guestKey = keyFor(WALLET_GUEST_OWNER_ID)
            val targetKey = keyFor(targetOwnerId)
            if (prefs[targetKey] == null) {
                prefs[targetKey] = prefs[guestKey] ?: prefs[KEY_RULES] ?: emptySet()
            }
            prefs.remove(guestKey)
            prefs.remove(KEY_RULES)
        }
    }

    private fun keyFor(ownerId: String) = stringSetPreferencesKey(
        "rules_v1_${ownerId.replace(Regex("[^A-Za-z0-9_-]"), "_")}"
    )

    private companion object {
        val KEY_RULES = stringSetPreferencesKey("rules_v1")
        const val SEPARATOR = "||"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CategoryRulesModule {
    @Provides
    @Singleton
    fun provideCategoryRules(store: CategoryRuleStore): CategoryRuleRepository = store
}
