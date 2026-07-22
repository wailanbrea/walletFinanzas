package com.bsolutions.wallet.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Regla creada por el usuario: si la nota contiene [keyword], asignar [categoryId]. */
data class CustomCategoryRule(
    val keyword: String,
    val categoryId: String
)

/** Reglas de categorización definidas por el usuario (tienen prioridad sobre las integradas). */
interface CategoryRuleRepository {
    val rules: Flow<List<CustomCategoryRule>>
    suspend fun add(keyword: String, categoryId: String)
    suspend fun remove(keyword: String)
    suspend fun removeByCategory(categoryId: String)
}

/** Implementación nula para tests y como default de constructores. */
object EmptyCategoryRules : CategoryRuleRepository {
    override val rules: Flow<List<CustomCategoryRule>> = flowOf(emptyList())
    override suspend fun add(keyword: String, categoryId: String) = Unit
    override suspend fun remove(keyword: String) = Unit
    override suspend fun removeByCategory(categoryId: String) = Unit
}
