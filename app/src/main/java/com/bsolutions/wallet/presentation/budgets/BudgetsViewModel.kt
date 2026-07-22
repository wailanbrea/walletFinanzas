package com.bsolutions.wallet.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.CategoryPlaceholders
import com.bsolutions.wallet.domain.model.Budget
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.repository.BudgetRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class BudgetCategoryItem(
    val budgetId: String,
    val category: Category,
    val limitAmount: Long,
    val spentAmount: Long
)

data class BudgetsUiState(
    val totalLimit: Long = 0L,
    val totalSpent: Long = 0L,
    val budgetItems: List<BudgetCategoryItem> = emptyList(),
    /** Categorías sin presupuesto asignado, disponibles para crear uno nuevo. */
    val availableCategories: List<Category> = emptyList()
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    // Sin seed de presupuestos de ejemplo: la pantalla tiene empty state con
    // "Crear Presupuesto". El seed anterior re-sembraba datos demo cada vez que
    // el usuario borraba su último presupuesto (bug detectado por test).

    val uiState: StateFlow<BudgetsUiState> = combine(
        budgetRepository.getBudgets(),
        categoryRepository.getCategories(),
        transactionRepository.getTransactions()
    ) { budgets, categories, transactions ->
        val categoryMap = categories.associateBy { it.id }

        // Gasto por categoría del mes en curso (los presupuestos son mensuales)
        val now = Calendar.getInstance()
        val spentByCategory = transactions
            .filter { it.type == "EXPENSE" }
            .filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                cal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                    cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val items = budgets.map { budget ->
            val cat = categoryMap[budget.categoryId]
                ?: CategoryPlaceholders.deleted(budget.categoryId)
            val spent = spentByCategory[budget.categoryId] ?: 0L
            BudgetCategoryItem(
                budgetId = budget.id,
                category = cat,
                limitAmount = budget.limitAmount,
                spentAmount = spent
            )
        }

        val totalLimit = budgets.sumOf { it.limitAmount }
        val totalSpent = items.sumOf { it.spentAmount }
        val budgetedCategoryIds = budgets.map { it.categoryId }.toSet()

        BudgetsUiState(
            totalLimit = totalLimit,
            totalSpent = totalSpent,
            budgetItems = items,
            availableCategories = categories.filter { it.id !in budgetedCategoryIds }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState()
    )

    fun addBudget(categoryId: String, limitAmount: Long) {
        viewModelScope.launch {
            budgetRepository.addBudget(
                Budget(
                    id = UUID.randomUUID().toString(),
                    categoryId = categoryId,
                    limitAmount = limitAmount,
                    spentAmount = 0L,
                    period = "MONTHLY"
                )
            )
        }
    }

    fun updateBudgetLimit(budgetId: String, categoryId: String, newLimit: Long) {
        if (newLimit <= 0L) return
        viewModelScope.launch {
            budgetRepository.updateBudget(
                Budget(
                    id = budgetId,
                    categoryId = categoryId,
                    limitAmount = newLimit,
                    spentAmount = 0L,
                    period = "MONTHLY"
                )
            )
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch { budgetRepository.deleteBudget(budgetId) }
    }
}
