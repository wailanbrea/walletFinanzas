package com.bsolutions.wallet.domain.repository

import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Budget
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.Goal
import com.bsolutions.wallet.domain.model.PlannedPayment
import com.bsolutions.wallet.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccounts(): Flow<List<Account>>
    suspend fun getAccount(id: String): Account?
    suspend fun addAccount(account: Account)
    suspend fun updateAccount(account: Account)
    suspend fun deleteAccount(id: String)
}

interface TransactionRepository {
    fun getTransactions(): Flow<List<Transaction>>
    fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>>
    suspend fun getTransaction(id: String): Transaction?
    suspend fun addTransaction(transaction: Transaction)
    suspend fun executeTransfer(fromAccountId: String, toAccountId: String, amount: Long, transaction: Transaction): Boolean
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: String)
}

interface CategoryRepository {
    fun getCategories(): Flow<List<Category>>
    suspend fun getCategory(id: String): Category?
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
