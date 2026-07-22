package com.bsolutions.wallet.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.CategoryPlaceholders
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class CategoryReportItem(
    val category: Category,
    val amount: Long,
    val percentage: Int
)

data class MonthlyBarData(
    val monthName: String,
    val incomeAmount: Long,
    val expenseAmount: Long
)

data class ReportsUiState(
    val totalExpense: Long = 0L,
    val categoryItems: List<CategoryReportItem> = emptyList(),
    val monthlyDataList: List<MonthlyBarData> = emptyList()
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<ReportsUiState> = combine(
        transactionRepository.getTransactions(),
        categoryRepository.getCategories()
    ) { transactions, categories ->
        val categoryMap = categories.associateBy { it.id }

        // Filter out expenses
        val expenses = transactions.filter { it.type == "EXPENSE" }
        val totalExp = expenses.sumOf { it.amount }

        // Spent per category
        val spentMap = expenses
            .groupBy { CategoryPlaceholders.aggregateId(it.categoryId, categoryMap) }
            .mapValues { it.value.sumOf { transaction -> transaction.amount } }

        val categoryReportList = spentMap.map { (catId, amount) ->
            val cat = categoryMap[catId] ?: CategoryPlaceholders.uncategorized()
            val pct = if (totalExp > 0) ((amount.toDouble() / totalExp.toDouble()) * 100).toInt() else 0
            CategoryReportItem(cat, amount, pct)
        }.sortedByDescending { it.amount }

        // Últimos 6 meses reales (mes + año, no solo mes)
        val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        val now = Calendar.getInstance()

        val monthlyTotals = List(6) { index ->
            val target = (now.clone() as Calendar).apply { add(Calendar.MONTH, -(5 - index)) }
            val targetMonth = target.get(Calendar.MONTH)
            val targetYear = target.get(Calendar.YEAR)

            val monthTransactions = transactions.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear
            }

            MonthlyBarData(
                monthName = monthNames[targetMonth],
                incomeAmount = monthTransactions.filter { it.type == "INCOME" }.sumOf { it.amount },
                expenseAmount = monthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            )
        }

        ReportsUiState(
            totalExpense = totalExp,
            categoryItems = categoryReportList,
            monthlyDataList = monthlyTotals
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState()
    )
}
