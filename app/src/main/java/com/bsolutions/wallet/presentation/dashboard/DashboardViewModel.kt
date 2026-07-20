package com.bsolutions.wallet.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.AccountBalances
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.data.preferences.UserProfilePreferences
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class CategorySpend(
    val category: Category,
    val amount: Long,
    val percentage: Int
)

data class DashboardUiState(
    val totalBalance: Long = 0L,
    /** Subtotales en divisas distintas de RD$ (null si todas las cuentas son DOP). */
    val foreignBalancesSubtitle: String? = null,
    val monthlyIncome: Long = 0L,
    val monthlyExpenses: Long = 0L,
    /** Variación % del gasto de este mes vs el anterior; null si no hay base de comparación. */
    val expenseTrendPercent: Int? = null,
    val categorySpending: List<CategorySpend> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    /** Modo privacidad: si está activo, la UI ofusca los montos. */
    val balancesHidden: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserProfilePreferences
) : ViewModel() {

    fun toggleBalancesHidden() {
        viewModelScope.launch {
            val current = userPreferencesRepository.profile.first().balancesHidden
            userPreferencesRepository.setBalancesHidden(!current)
        }
    }

    init {
        viewModelScope.launch {
            // Seed solo en el primer arranque (chequeo único, no colector permanente:
            // si el usuario borra sus categorías no deben re-sembrarse en caliente)
            if (categoryRepository.getCategories().first().isEmpty()) {
                seedInitialData()
            }
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        accountRepository.getAccounts(),
        transactionRepository.getTransactions(),
        categoryRepository.getCategories(),
        userPreferencesRepository.profile
    ) { accounts, transactions, categories, profile ->
        // Balance principal solo en RD$; otras divisas van como subtotales aparte
        val balance = AccountBalances.primaryTotal(accounts)
        val foreignSubtitle = AccountBalances.foreignSubtitle(accounts)
        val categoryMap = categories.associateBy { it.id }

        // Solo el mes en curso (antes sumaba el histórico completo)
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)
        val prev = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }

        fun inMonth(dateMillis: Long, month: Int, year: Int): Boolean {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            return cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
        }

        // Ingresos/Gastos del mes: solo movimientos en RD$ (no se mezclan divisas).
        // Los importados en €, US$, etc. se excluyen de estos totales base.
        val thisMonthTx = transactions
            .filter { inMonth(it.date, currentMonth, currentYear) }
            .filter { it.currency == MoneyFormat.DEFAULT_CURRENCY }
        val income = thisMonthTx.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expenses = thisMonthTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }

        // Tendencia real: gasto de este mes vs el mes anterior
        val prevExpenses = transactions
            .filter { inMonth(it.date, prev.get(Calendar.MONTH), prev.get(Calendar.YEAR)) }
            .filter { it.currency == MoneyFormat.DEFAULT_CURRENCY }
            .filter { it.type == "EXPENSE" }
            .sumOf { it.amount }
        val trend = if (prevExpenses > 0) {
            (((expenses - prevExpenses).toDouble() / prevExpenses) * 100).toInt()
        } else null

        // Gasto del mes por categoría (para el donut del dashboard)
        val spending = thisMonthTx
            .filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .mapNotNull { (catId, txs) ->
                val cat = categoryMap[catId] ?: return@mapNotNull null
                val amount = txs.sumOf { it.amount }
                CategorySpend(
                    category = cat,
                    amount = amount,
                    percentage = if (expenses > 0) ((amount.toDouble() / expenses) * 100).toInt() else 0
                )
            }
            .sortedByDescending { it.amount }

        DashboardUiState(
            totalBalance = balance,
            foreignBalancesSubtitle = foreignSubtitle,
            monthlyIncome = income,
            monthlyExpenses = expenses,
            expenseTrendPercent = trend,
            categorySpending = spending,
            recentTransactions = transactions.sortedByDescending { it.date }.take(5),
            accounts = accounts.toList(),
            categories = categoryMap,
            balancesHidden = profile.balancesHidden
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun addTransaction(accountId: String, amount: Long, type: String, categoryId: String, note: String) {
        if (amount <= 0L || accountId.isBlank()) return
        viewModelScope.launch {
            transactionRepository.addTransaction(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    amount = amount,
                    type = type,
                    categoryId = categoryId.ifBlank { "" },
                    date = System.currentTimeMillis(),
                    note = note
                )
            )
            // Reflejar el movimiento en el balance de la cuenta
            accountRepository.getAccount(accountId)?.let { account ->
                val delta = if (type == "INCOME") amount else -amount
                accountRepository.updateAccount(account.copy(balance = account.balance + delta))
            }
        }
    }

    /** Mueve dinero entre dos cuentas registrando un movimiento TRANSFER en cada una. */
    fun transfer(fromAccountId: String, toAccountId: String, amount: Long, note: String) {
        if (amount <= 0L || fromAccountId.isBlank() || toAccountId.isBlank() || fromAccountId == toAccountId) return
        viewModelScope.launch {
            val from = accountRepository.getAccount(fromAccountId) ?: return@launch
            val to = accountRepository.getAccount(toAccountId) ?: return@launch
            val now = System.currentTimeMillis()
            val label = note.ifBlank { "Transferencia ${from.name} → ${to.name}" }

            transactionRepository.executeTransfer(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                transaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = fromAccountId,
                    amount = amount,
                    type = "TRANSFER",
                    categoryId = "",
                    date = now,
                    note = label
                )
            )
        }
    }

    private suspend fun seedInitialData() {
        val catViviendaId = UUID.randomUUID().toString()
        val catComidaId = UUID.randomUUID().toString()
        val catTransporteId = UUID.randomUUID().toString()

        categoryRepository.addCategory(Category(catViviendaId, "Vivienda", "home", "#000666"))
        categoryRepository.addCategory(Category(catComidaId, "Alimentación", "restaurant", "#1B6D24"))
        categoryRepository.addCategory(Category(catTransporteId, "Transporte", "directions_car", "#BA1A1A"))

        val accId = UUID.randomUUID().toString()
        accountRepository.addAccount(Account(accId, "Cuenta Principal", "BANK", 11090000L, "DOP"))

        // Add some transactions
        transactionRepository.addTransaction(
            Transaction(
                id = UUID.randomUUID().toString(),
                accountId = accId,
                amount = 8500000L,
                type = "INCOME",
                categoryId = "",
                date = System.currentTimeMillis() - 86400000, // Yesterday
                note = "Salario Mensual"
            )
        )
        transactionRepository.addTransaction(
            Transaction(
                id = UUID.randomUUID().toString(),
                accountId = accId,
                amount = 325000L,
                type = "EXPENSE",
                categoryId = catComidaId,
                date = System.currentTimeMillis(), // Today
                note = "Supermercado Nacional"
            )
        )
    }
}
