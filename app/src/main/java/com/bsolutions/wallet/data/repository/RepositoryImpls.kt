package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.data.local.dao.AccountDao
import com.bsolutions.wallet.data.local.dao.BudgetDao
import com.bsolutions.wallet.data.local.dao.CategoryDao
import com.bsolutions.wallet.data.local.dao.DebtDao
import com.bsolutions.wallet.data.local.dao.GoalDao
import com.bsolutions.wallet.data.local.dao.PlannedPaymentDao
import com.bsolutions.wallet.data.local.dao.TransactionDao
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.BudgetEntity
import com.bsolutions.wallet.data.local.entity.CategoryEntity
import com.bsolutions.wallet.data.local.entity.DebtEntity
import com.bsolutions.wallet.data.local.entity.GoalEntity
import com.bsolutions.wallet.data.local.entity.PlannedPaymentEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Budget
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.Goal
import com.bsolutions.wallet.domain.model.PlannedPayment
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.BudgetRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.repository.GoalRepository
import com.bsolutions.wallet.domain.repository.PlannedPaymentRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Mappers
fun AccountEntity.toDomain() = Account(id, name, type, balance, currency, countryCode, institutionName, cardLastFour)
fun Account.toEntity() = AccountEntity(id, name, type, balance, currency, countryCode, institutionName, cardLastFour)

fun TransactionEntity.toDomain() = Transaction(id, accountId, amount, type, categoryId, date, note, currency)
fun Transaction.toEntity() = TransactionEntity(id, accountId, amount, type, categoryId, date, note, currency)

fun CategoryEntity.toDomain() = Category(id, name, icon, colorHex)
fun Category.toEntity() = CategoryEntity(id, name, icon, colorHex)

fun BudgetEntity.toDomain() = Budget(id, categoryId, limitAmount, spentAmount, period)
fun Budget.toEntity() = BudgetEntity(id, categoryId, limitAmount, spentAmount, period)

fun GoalEntity.toDomain() = Goal(id, name, icon, targetAmount, savedAmount, targetDate, isCompleted)
fun Goal.toEntity() = GoalEntity(id, name, icon, targetAmount, savedAmount, targetDate, isCompleted)

fun PlannedPaymentEntity.toDomain() =
    PlannedPayment(id, name, accountId, categoryId, amount, type, frequency, nextDueDate, isActive)
fun PlannedPayment.toEntity() =
    PlannedPaymentEntity(id, name, accountId, categoryId, amount, type, frequency, nextDueDate, isActive)

fun DebtEntity.toDomain() = Debt(id, name, description, direction, totalAmount, paidAmount, dueDate, isClosed)
fun Debt.toEntity() = DebtEntity(id, name, description, direction, totalAmount, paidAmount, dueDate, isClosed)


class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao
) : AccountRepository {
    override fun getAccounts(): Flow<List<Account>> =
        dao.getAllAccounts().map { list -> list.map { it.toDomain() } }

    override suspend fun getAccount(id: String): Account? =
        dao.getAccountById(id)?.toDomain()

    override suspend fun addAccount(account: Account) =
        dao.insertAccount(account.toEntity())

    override suspend fun updateAccount(account: Account) =
        dao.updateAccount(account.toEntity())

    override suspend fun deleteAccount(id: String) =
        dao.softDeleteAccount(id)
}

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao
) : TransactionRepository {
    override fun getTransactions(): Flow<List<Transaction>> =
        dao.getAllTransactions().map { list -> list.map { it.toDomain() } }

    override fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>> =
        dao.getTransactionsByAccount(accountId).map { list -> list.map { it.toDomain() } }

    override suspend fun getTransaction(id: String): Transaction? =
        dao.getTransactionById(id)?.toDomain()

    override suspend fun addTransaction(transaction: Transaction) =
        dao.insertTransaction(transaction.toEntity())

    override suspend fun executeTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Long,
        transaction: Transaction
    ): Boolean = dao.executeTransfer(fromAccountId, toAccountId, amount, transaction.toEntity())

    override suspend fun updateTransaction(transaction: Transaction) =
        dao.updateTransaction(transaction.toEntity())

    override suspend fun deleteTransaction(id: String) =
        dao.softDeleteTransaction(id)
}

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao
) : CategoryRepository {
    override fun getCategories(): Flow<List<Category>> =
        dao.getAllCategories().map { list -> list.map { it.toDomain() } }

    override suspend fun getCategory(id: String): Category? =
        dao.getCategoryById(id)?.toDomain()

    override suspend fun addCategory(category: Category) =
        dao.insertCategory(category.toEntity())

    override suspend fun deleteCategory(id: String) =
        dao.softDeleteCategory(id)
}

class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao
) : BudgetRepository {
    override fun getBudgets(): Flow<List<Budget>> =
        dao.getAllBudgets().map { list -> list.map { it.toDomain() } }

    override suspend fun getBudgetByCategory(categoryId: String): Budget? =
        dao.getBudgetByCategory(categoryId)?.toDomain()

    override suspend fun addBudget(budget: Budget) =
        dao.insertBudget(budget.toEntity())

    override suspend fun updateBudget(budget: Budget) =
        dao.updateBudget(budget.toEntity())

    override suspend fun deleteBudget(id: String) =
        dao.softDeleteBudget(id)
}

class GoalRepositoryImpl @Inject constructor(
    private val dao: GoalDao
) : GoalRepository {
    override fun getGoals(): Flow<List<Goal>> =
        dao.getAllGoals().map { list -> list.map { it.toDomain() } }

    override suspend fun getGoal(id: String): Goal? =
        dao.getGoalById(id)?.toDomain()

    override suspend fun addGoal(goal: Goal) =
        dao.insertGoal(goal.toEntity())

    override suspend fun updateGoal(goal: Goal) =
        dao.updateGoal(goal.toEntity())

    override suspend fun deleteGoal(id: String) =
        dao.softDeleteGoal(id)
}

class PlannedPaymentRepositoryImpl @Inject constructor(
    private val dao: PlannedPaymentDao
) : PlannedPaymentRepository {
    override fun getPlannedPayments(): Flow<List<PlannedPayment>> =
        dao.getAllPlannedPayments().map { list -> list.map { it.toDomain() } }

    override suspend fun getPlannedPayment(id: String): PlannedPayment? =
        dao.getPlannedPaymentById(id)?.toDomain()

    override suspend fun addPlannedPayment(payment: PlannedPayment) =
        dao.insertPlannedPayment(payment.toEntity())

    override suspend fun updatePlannedPayment(payment: PlannedPayment) =
        dao.updatePlannedPayment(payment.toEntity())

    override suspend fun deletePlannedPayment(id: String) =
        dao.softDeletePlannedPayment(id)
}

class DebtRepositoryImpl @Inject constructor(
    private val dao: DebtDao
) : DebtRepository {
    override fun getDebts(): Flow<List<Debt>> =
        dao.getAllDebts().map { list -> list.map { it.toDomain() } }

    override suspend fun getDebt(id: String): Debt? =
        dao.getDebtById(id)?.toDomain()

    override suspend fun addDebt(debt: Debt) =
        dao.insertDebt(debt.toEntity())

    override suspend fun updateDebt(debt: Debt) =
        dao.updateDebt(debt.toEntity())

    override suspend fun deleteDebt(id: String) =
        dao.softDeleteDebt(id)
}
