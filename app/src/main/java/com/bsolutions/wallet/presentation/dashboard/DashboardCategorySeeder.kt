package com.bsolutions.wallet.presentation.dashboard

import com.bsolutions.wallet.core.common.CategoryRuleRepository
import com.bsolutions.wallet.core.common.DefaultCategories
import com.bsolutions.wallet.core.common.ExpenseCategorizer
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

/**
 * Siembra categorías por defecto y migra el grupo "supermercado" a la categoría
 * canonical del set de datos.
 *
 * El seed es aditivo: solo inserta categorías cuyo id no exista ya (incluye
 * tombstones para que una categoría eliminada no reaparezca).
 *
 * La migración de "supermercado" normaliza alias como "super mercado" hacia
 * "cat_supermercado" y actualiza transacciones y reglas asociadas sin tocar
 * montos ni saldos históricos.
 */
object DashboardCategorySeeder {

    suspend fun seed(
        categoryRepository: CategoryRepository,
        transactionRepository: TransactionRepository,
        categoryRules: CategoryRuleRepository
    ) {
        // Seed de categorías base (aditivo por id, respeta tombstones)
        seedDefaultCategories(categoryRepository)
        // Migración de alias del grupo supermercado
        migrateFoodCategories(categoryRepository, transactionRepository, categoryRules)
    }

    private suspend fun seedDefaultCategories(categoryRepository: CategoryRepository) {
        val existingIds = categoryRepository.getAllCategoryIdsIncludingDeleted()
        DefaultCategories.asCategories()
            .filter { it.id !in existingIds }
            .forEach { categoryRepository.addCategory(it) }
    }

    /**
     * Normaliza el grupo de alimentos "supermercado" sin tocar montos ni saldos históricos.
     *
     * 1) Reagrupa todos los alias de "supermercado" en "cat_supermercado".
     * 2) Reasigna transacciones de alimentación con nota de supermercado.
     * 3) Borra las categorías alias.
     * 4) Actualiza las reglas de categorización para apuntar al id canonical.
     */
    private suspend fun migrateFoodCategories(
        categoryRepository: CategoryRepository,
        transactionRepository: TransactionRepository,
        categoryRules: CategoryRuleRepository
    ) {
        val categories = categoryRepository.getCategories().first()
        val supermarket = categories.firstOrNull { it.id == "cat_supermercado" } ?: return
        val aliases = categories.filter { category ->
            category.id != supermarket.id && ExpenseCategorizer.normalizeText(category.name) in setOf("super mercado", "supermercado")
        }
        val transactions = transactionRepository.getTransactions().first()
        val supermarketIds = aliases.mapTo(mutableSetOf()) { it.id } + supermarket.id

        // Reasignar transacciones de alias al supermercado canonical
        val txsToMigrate = transactions.filter { transaction ->
            transaction.categoryId in aliases.map { it.id } ||
                (transaction.type == "EXPENSE" && transaction.categoryId == "cat_alimentacion" &&
                    ExpenseCategorizer.inferCategoryId(transaction.note) == "cat_supermercado")
        }
        for (transaction in txsToMigrate) {
            if (transaction.categoryId != supermarket.id) {
                transactionRepository.updateTransaction(transaction.copy(categoryId = supermarket.id))
            }
        }

        // Borrar categorías alias
        for (alias in aliases) {
            categoryRepository.deleteCategory(alias.id)
        }

        // Actualizar reglas de categorización para apuntar al id canonical
        val supermarketWords = setOf("supermerc", "colmado", "nacional", "jumbo", "sirena", "pola", "bravo", "market", "grocer")
        val rulesToMigrate = categoryRules.rules.first().filter { rule ->
            rule.categoryId in supermarketIds && supermarketWords.any { word -> ExpenseCategorizer.normalizeText(rule.keyword).contains(word) }
        }
        for (rule in rulesToMigrate) {
            categoryRules.remove(rule.keyword)
            categoryRules.add(rule.keyword, supermarket.id)
        }
    }
}
