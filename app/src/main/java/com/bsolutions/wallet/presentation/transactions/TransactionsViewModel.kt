package com.bsolutions.wallet.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.core.common.CategoryRuleRepository
import com.bsolutions.wallet.core.common.EmptyCategoryRules
import com.bsolutions.wallet.core.common.ExpenseCategorizer
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import com.bsolutions.wallet.domain.usecase.DEBT_OWED_TO_ME
import com.bsolutions.wallet.domain.usecase.DebtLedger
import com.bsolutions.wallet.domain.usecase.LOAN_CATEGORY_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Todas las deudas por cobrar, abiertas y cerradas. */
    val receivables: List<Debt> = emptyList(),
    val searchQuery: String = ""
) {
    /** Solo a una deuda abierta tiene sentido aplicarle un abono. */
    val openReceivables: List<Debt> get() = receivables.filterNot { it.isClosed }
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val debtRepository: DebtRepository,
    private val debtLedger: DebtLedger,
    // Default para tests: Hilt inyecta la implementación real de todos modos.
    private val categoryRules: CategoryRuleRepository = EmptyCategoryRules
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.getTransactions(),
        accountRepository.getAccounts(),
        categoryRepository.getCategories(),
        debtRepository.getDebts(),
        searchQuery
    ) { txs, accounts, categories, debts, query ->
        val filteredTxs = if (query.isEmpty()) {
            txs
        } else {
            txs.filter { it.note.contains(query, ignoreCase = true) }
        }

        TransactionsUiState(
            transactions = filteredTxs.sortedByDescending { it.date },
            accounts = accounts,
            categories = categories,
            receivables = debts.filter { it.direction == DEBT_OWED_TO_ME },
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

    /**
     * Convierte un gasto en un prestamo a [personName] y abre la deuda por cobrar.
     * El monto y la cuenta no se tocan: el dinero ya salio de donde salio.
     */
    fun markAsLoan(transaction: Transaction, personName: String, description: String = "") {
        if (personName.isBlank()) return
        viewModelScope.launch { debtLedger.lend(transaction, personName, description) }
    }

    /** Aplica un ingreso ya registrado a una deuda por cobrar, como abono. */
    fun applyToDebt(transaction: Transaction, debtId: String) {
        if (debtId.isBlank()) return
        viewModelScope.launch { debtLedger.applyExistingTransaction(transaction, debtId) }
    }

    /** Desata el movimiento de su deuda y vuelve a cuadrar lo cobrado. */
    fun unlinkFromDebt(transaction: Transaction) {
        viewModelScope.launch { debtLedger.unlink(transaction) }
    }

    fun addTransaction(
        accountId: String,
        amount: Long,
        type: String,
        categoryId: String,
        note: String
    ) {
        viewModelScope.launch {
            // El movimiento hereda la divisa de su cuenta; saldo y movimiento se
            // escriben atómicamente para evitar descuadres.
            val account = accountRepository.getAccount(accountId) ?: return@launch
            // Sin categoría elegida: se infiere de la nota (reglas del usuario primero).
            val finalCategoryId = categoryId.ifBlank {
                ExpenseCategorizer.categoryIdFor(
                    text = note,
                    categories = categoryRepository.getCategories().first(),
                    customRules = categoryRules.rules.first()
                ).orEmpty()
            }
            transactionRepository.addTransactionWithBalance(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    amount = amount,
                    type = type,
                    categoryId = finalCategoryId,
                    date = System.currentTimeMillis(),
                    note = note,
                    currency = account.currency
                )
            )
        }
    }

    fun updateTransaction(original: Transaction, newAmount: Long, newCategoryId: String, newNote: String) {
        if (newAmount <= 0L) return
        viewModelScope.launch {
            val categories = categoryRepository.getCategories().first()
            val validSelectedCategoryId = newCategoryId.takeIf { selectedId ->
                selectedId.isNotBlank() && categories.any { it.id == selectedId }
            }
            // Un movimiento atado a una deuda conserva la categoria de prestamos: es lo
            // que hace que la ida y la vuelta se neteen en vez de contarse por separado.
            val finalCategoryId = if (original.debtId != null) {
                LOAN_CATEGORY_ID
            } else {
                validSelectedCategoryId ?: ExpenseCategorizer.categoryIdFor(
                    text = newNote,
                    categories = categories,
                    customRules = categoryRules.rules.first()
                ).orEmpty()
            }
            // Ajuste de saldo por la diferencia + actualización del movimiento, atómico.
            // (Las transferencias se editan por otra vía; aquí solo income/gasto.)
            if (original.type == "TRANSFER") {
                transactionRepository.updateTransaction(
                    original.copy(amount = newAmount, categoryId = finalCategoryId, note = newNote)
                )
                return@launch
            }
            transactionRepository.updateTransactionWithBalance(
                original.copy(amount = newAmount, categoryId = finalCategoryId, note = newNote),
                oldAmount = original.amount
            )
            // Cambiar el monto de un movimiento atado mueve la deuda por la diferencia.
            debtLedger.onAmountEdited(
                original.copy(amount = newAmount, categoryId = finalCategoryId, note = newNote),
                oldAmount = original.amount
            )
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            // Revierte el efecto en el saldo y borra, atómico. Los TRANSFER no ajustan
            // saldo por esta vía (su reverso requeriría tocar ambas cuentas).
            if (transaction.type == "TRANSFER") {
                transactionRepository.deleteTransaction(transaction.id)
            } else {
                transactionRepository.deleteTransactionWithBalance(transaction)
            }
            // Borrar un movimiento atado deshace su efecto en la deuda.
            debtLedger.onTransactionDeleted(transaction)
        }
    }
}
