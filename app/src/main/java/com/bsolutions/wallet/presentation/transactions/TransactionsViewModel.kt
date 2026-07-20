package com.bsolutions.wallet.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = ""
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.getTransactions(),
        accountRepository.getAccounts(),
        categoryRepository.getCategories(),
        searchQuery
    ) { txs, accounts, categories, query ->
        val filteredTxs = if (query.isEmpty()) {
            txs
        } else {
            txs.filter { it.note.contains(query, ignoreCase = true) }
        }

        TransactionsUiState(
            transactions = filteredTxs.sortedByDescending { it.date },
            accounts = accounts,
            categories = categories,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addTransaction(
        accountId: String,
        amount: Long,
        type: String,
        categoryId: String,
        note: String
    ) {
        viewModelScope.launch {
            // Update account balance (decrease if Expense, increase if Income)
            val account = accountRepository.getAccount(accountId)
            if (account != null) {
                val newBalance = if (type == "INCOME") {
                    account.balance + amount
                } else {
                    account.balance - amount
                }
                accountRepository.updateAccount(account.copy(balance = newBalance))
            }

            // Save transaction
            transactionRepository.addTransaction(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    date = System.currentTimeMillis(),
                    note = note
                )
            )
        }
    }

    /** Ajusta el balance de una cuenta según el tipo de movimiento y su signo. */
    private suspend fun applyBalanceDelta(accountId: String, amount: Long, type: String, revert: Boolean) {
        if (type == "TRANSFER") return // las transferencias se manejan aparte
        val account = accountRepository.getAccount(accountId) ?: return
        val sign = if (type == "INCOME") 1 else -1
        val delta = amount * sign * (if (revert) -1 else 1)
        accountRepository.updateAccount(account.copy(balance = account.balance + delta))
    }

    fun updateTransaction(original: Transaction, newAmount: Long, newCategoryId: String, newNote: String) {
        if (newAmount <= 0L) return
        viewModelScope.launch {
            // Revertir el efecto original y aplicar el nuevo monto
            applyBalanceDelta(original.accountId, original.amount, original.type, revert = true)
            applyBalanceDelta(original.accountId, newAmount, original.type, revert = false)
            transactionRepository.updateTransaction(
                original.copy(amount = newAmount, categoryId = newCategoryId, note = newNote)
            )
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            applyBalanceDelta(transaction.accountId, transaction.amount, transaction.type, revert = true)
            transactionRepository.deleteTransaction(transaction.id)
        }
    }
}
