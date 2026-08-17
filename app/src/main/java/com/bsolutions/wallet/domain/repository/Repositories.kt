package com.bsolutions.wallet.domain.repository

import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Budget
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.Goal
import com.bsolutions.wallet.domain.model.PlannedPayment
import com.bsolutions.wallet.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState

interface AccountRepository {
    fun getAccounts(): Flow<List<Account>>
    suspend fun getAccount(id: String): Account?
    suspend fun addAccount(account: Account)
    suspend fun updateAccount(account: Account)
    /**
     * Borra la cuenta y sus movimientos, y devuelve los que arrastro.
     *
     * Quien llame tiene que deshacer el efecto de los que estuvieran atados a una deuda:
     * eso no puede resolverlo el repositorio de cuentas.
     */
    suspend fun deleteAccount(id: String): List<Transaction>
}

interface TransactionRepository {
    fun getTransactions(): Flow<List<Transaction>>
    fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>>
    /** Transacciones paginadas de una cuenta. */
    fun getTransactionsPaging(ownerId: String, accountId: String): PagingSource<Int, Transaction>
    suspend fun getTransaction(id: String): Transaction?
    /** Movimientos de una deuda: el gasto que la originó y los abonos recibidos. */
    suspend fun getTransactionsForDebt(debtId: String): List<Transaction>
    suspend fun addTransaction(transaction: Transaction)
    /** Inserta el movimiento y ajusta el saldo de la cuenta atómicamente (income/gasto). */
    suspend fun addTransactionWithBalance(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    /** Actualiza el movimiento ajustando el saldo por la diferencia de monto, atómicamente. */
    suspend fun updateTransactionWithBalance(transaction: Transaction, oldAmount: Long)
    suspend fun deleteTransaction(id: String)
    /** Revierte el efecto del movimiento en el saldo y lo borra, atómicamente. */
    suspend fun deleteTransactionWithBalance(transaction: Transaction)
    /**
     * Inserta varias transacciones y ajusta los saldos en una sola transacción
     * atómica. Si alguna falla, se revierte todo el lote.
     */
    suspend fun addTransactionsWithBalance(transactions: List<Transaction>)
}

interface CategoryRepository {
    fun getCategories(): Flow<List<Category>>
    suspend fun getCategory(id: String): Category?
    suspend fun getAllCategoryIdsIncludingDeleted(): Set<String>
    suspend fun addCategory(category: Category)
    suspend fun deleteCategory(id: String)
}

interface BudgetRepository {
    fun getBudgets(): Flow<List<Budget>>
    suspend fun getBudgetByCategory(categoryId: String): Budget?
    suspend fun addBudget(budget: Budget)
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(id: String)
}

interface GoalRepository {
    fun getGoals(): Flow<List<Goal>>
    suspend fun getGoal(id: String): Goal?
    suspend fun addGoal(goal: Goal)
    suspend fun updateGoal(goal: Goal)
    suspend fun deleteGoal(id: String)
}

interface PlannedPaymentRepository {
    fun getPlannedPayments(): Flow<List<PlannedPayment>>
    suspend fun getPlannedPayment(id: String): PlannedPayment?
    suspend fun addPlannedPayment(payment: PlannedPayment)
    suspend fun updatePlannedPayment(payment: PlannedPayment)
    suspend fun deletePlannedPayment(id: String)
}

interface DebtRepository {
    fun getDebts(): Flow<List<Debt>>
    suspend fun getDebt(id: String): Debt?
    suspend fun addDebt(debt: Debt)
    suspend fun updateDebt(debt: Debt)
    suspend fun deleteDebt(id: String)
}
