@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

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
import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.core.sync.SyncScheduler
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
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

// Mappers
fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    type = type,
    balance = balance,
    currency = currency,
    countryCode = countryCode,
    institutionName = institutionName,
    cardLastFour = cardLastFour,
    creditLimit = creditLimit
)

fun Account.toEntity(ownerId: String) = AccountEntity(
    id = id,
    name = name,
    type = type,
    balance = balance,
    currency = currency,
    countryCode = countryCode,
    institutionName = institutionName,
    cardLastFour = cardLastFour,
    ownerId = ownerId,
    creditLimit = creditLimit
)

fun TransactionEntity.toDomain() =
    Transaction(id, accountId, amount, type, categoryId, date, note, currency, debtId)
fun Transaction.toEntity(ownerId: String) =
    TransactionEntity(id, accountId, amount, type, categoryId, date, note, currency, debtId, ownerId = ownerId)

fun CategoryEntity.toDomain() = Category(id, name, icon, colorHex, type)
fun Category.toEntity(ownerId: String) = CategoryEntity(id, name, icon, colorHex, type, ownerId = ownerId)

fun BudgetEntity.toDomain() = Budget(id, categoryId, limitAmount, spentAmount, period)
fun Budget.toEntity(ownerId: String) = BudgetEntity(id, categoryId, limitAmount, spentAmount, period, ownerId = ownerId)

fun GoalEntity.toDomain() = Goal(id, name, icon, targetAmount, savedAmount, targetDate, isCompleted)
fun Goal.toEntity(ownerId: String) =
    GoalEntity(id, name, icon, targetAmount, savedAmount, targetDate, isCompleted, ownerId = ownerId)

fun PlannedPaymentEntity.toDomain() =
    PlannedPayment(id, name, accountId, categoryId, amount, type, frequency, nextDueDate, isActive)
fun PlannedPayment.toEntity(ownerId: String) =
    PlannedPaymentEntity(id, name, accountId, categoryId, amount, type, frequency, nextDueDate, isActive, ownerId = ownerId)

fun DebtEntity.toDomain() = Debt(id, name, description, direction, totalAmount, paidAmount, dueDate, isClosed)
fun Debt.toEntity(ownerId: String) =
    DebtEntity(id, name, description, direction, totalAmount, paidAmount, dueDate, isClosed, ownerId = ownerId)


class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val gson: Gson,
    private val ownerScope: WalletOwnerScope,
    // Cada cambio pide subir de inmediato: sin esto habia que esperar al ciclo
    // periodico de 30 minutos para que el otro telefono se enterara.
    private val syncScheduler: SyncScheduler
) : AccountRepository {
    override fun getAccounts(): Flow<List<Account>> =
        ownerScope.ownerId.flatMapLatest { dao.getAllAccounts(it) }.map { list -> list.map { it.toDomain() } }

    override suspend fun getAccount(id: String): Account? =
        dao.getAccountById(ownerScope.currentOwnerId(), id)?.toDomain()

    override suspend fun addAccount(account: Account) {
        // Inserta la cuenta y encola su subida al backend en la misma transacción.
        val entity = account.toEntity(ownerScope.currentOwnerId())
        dao.insertWithOp(entity, SyncRepository.accountOp(gson, entity))
        syncScheduler.requestSyncNow()
    }

    // Editar y borrar tambien se encolan: antes solo subian las creaciones, asi que
    // renombrar o eliminar una cuenta se quedaba en este telefono y los demas seguian
    // viendo la version vieja.
    override suspend fun updateAccount(account: Account) {
        val entity = account.toEntity(ownerScope.currentOwnerId())
        dao.updateWithOp(entity, SyncRepository.accountOp(gson, entity))
        syncScheduler.requestSyncNow()
    }

    override suspend fun deleteAccount(id: String) {
        dao.softDeleteWithOp(ownerScope.currentOwnerId(), id) { deleted ->
            SyncRepository.accountOp(gson, deleted)
        }
        syncScheduler.requestSyncNow()
    }
}

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val gson: Gson,
    private val ownerScope: WalletOwnerScope,
    private val syncScheduler: SyncScheduler
) : TransactionRepository {
    override fun getTransactions(): Flow<List<Transaction>> =
        ownerScope.ownerId.flatMapLatest { dao.getAllTransactions(it) }.map { list -> list.map { it.toDomain() } }

    override fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>> =
        ownerScope.ownerId.flatMapLatest { dao.getTransactionsByAccount(it, accountId) }
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getTransaction(id: String): Transaction? =
        dao.getTransactionById(ownerScope.currentOwnerId(), id)?.toDomain()

    override suspend fun getTransactionsForDebt(debtId: String): List<Transaction> =
        dao.getTransactionsForDebt(ownerScope.currentOwnerId(), debtId).map { it.toDomain() }

    // Las importaciones de proveedores bancarios permanecen solo locales.
    override suspend fun addTransaction(transaction: Transaction) =
        dao.insertTransaction(transaction.toEntity(ownerScope.currentOwnerId()))

    override suspend fun addTransactionWithBalance(transaction: Transaction) {
        // Inserta movimiento + ajusta saldo + encola la subida, todo atómico.
        val entity = transaction.toEntity(ownerScope.currentOwnerId())
        dao.insertWithBalanceAndOp(entity, SyncRepository.transactionOp(gson, entity))
        syncScheduler.requestSyncNow()
    }

    override suspend fun executeTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Long,
        transaction: Transaction
    ): Boolean = dao.executeTransfer(
        fromAccountId,
        toAccountId,
        amount,
        transaction.toEntity(ownerScope.currentOwnerId())
    )

    override suspend fun updateTransaction(transaction: Transaction) {
        dao.updateTransaction(transaction.toEntity(ownerScope.currentOwnerId()))
    }

    // Corregir y borrar tambien se encolan: antes se quedaban en este telefono.
    override suspend fun updateTransactionWithBalance(transaction: Transaction, oldAmount: Long) {
        val entity = transaction.toEntity(ownerScope.currentOwnerId())
        dao.updateWithBalanceAndOp(entity, oldAmount, SyncRepository.transactionOp(gson, entity))
        syncScheduler.requestSyncNow()
    }

    override suspend fun deleteTransaction(id: String) {
        dao.softDeleteTransaction(ownerScope.currentOwnerId(), id)
    }

    override suspend fun deleteTransactionWithBalance(transaction: Transaction) {
        // La lapida lleva isDeleted = 1 para que el push mande el DELETE al servidor.
        val entity = transaction.toEntity(ownerScope.currentOwnerId()).copy(isDeleted = true)
        dao.softDeleteWithBalanceAndOp(entity, SyncRepository.transactionOp(gson, entity))
        syncScheduler.requestSyncNow()
    }
}

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val ownerScope: WalletOwnerScope
) : CategoryRepository {
    override fun getCategories(): Flow<List<Category>> =
        ownerScope.ownerId.flatMapLatest { dao.getAllCategories(it) }.map { list -> list.map { it.toDomain() } }

    override suspend fun getCategory(id: String): Category? =
        dao.getCategoryById(ownerScope.currentOwnerId(), id)?.toDomain()

    override suspend fun getAllCategoryIdsIncludingDeleted(): Set<String> =
        dao.getAllCategoryIdsIncludingDeleted(ownerScope.currentOwnerId()).toSet()

    override suspend fun addCategory(category: Category) =
        dao.insertCategory(category.toEntity(ownerScope.currentOwnerId()))

    override suspend fun deleteCategory(id: String) =
        dao.softDeleteCategory(ownerScope.currentOwnerId(), id)
}

class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
    private val ownerScope: WalletOwnerScope
) : BudgetRepository {
    override fun getBudgets(): Flow<List<Budget>> =
        ownerScope.ownerId.flatMapLatest { dao.getAllBudgets(it) }.map { list -> list.map { it.toDomain() } }

    override suspend fun getBudgetByCategory(categoryId: String): Budget? =
        dao.getBudgetByCategory(ownerScope.currentOwnerId(), categoryId)?.toDomain()

    override suspend fun addBudget(budget: Budget) =
        dao.insertBudget(budget.toEntity(ownerScope.currentOwnerId()))

    override suspend fun updateBudget(budget: Budget) =
        dao.updateBudget(budget.toEntity(ownerScope.currentOwnerId()))

    override suspend fun deleteBudget(id: String) =
        dao.softDeleteBudget(ownerScope.currentOwnerId(), id)
}

class GoalRepositoryImpl @Inject constructor(
    private val dao: GoalDao,
    private val ownerScope: WalletOwnerScope
) : GoalRepository {
    override fun getGoals(): Flow<List<Goal>> =
        ownerScope.ownerId.flatMapLatest { dao.getAllGoals(it) }.map { list -> list.map { it.toDomain() } }

    override suspend fun getGoal(id: String): Goal? =
        dao.getGoalById(ownerScope.currentOwnerId(), id)?.toDomain()

    override suspend fun addGoal(goal: Goal) =
        dao.insertGoal(goal.toEntity(ownerScope.currentOwnerId()))

    override suspend fun updateGoal(goal: Goal) =
        dao.updateGoal(goal.toEntity(ownerScope.currentOwnerId()))

    override suspend fun deleteGoal(id: String) =
        dao.softDeleteGoal(ownerScope.currentOwnerId(), id)
}

class PlannedPaymentRepositoryImpl @Inject constructor(
    private val dao: PlannedPaymentDao,
    private val ownerScope: WalletOwnerScope
) : PlannedPaymentRepository {
    override fun getPlannedPayments(): Flow<List<PlannedPayment>> =
        ownerScope.ownerId.flatMapLatest { dao.getAllPlannedPayments(it) }.map { list -> list.map { it.toDomain() } }

    override suspend fun getPlannedPayment(id: String): PlannedPayment? =
        dao.getPlannedPaymentById(ownerScope.currentOwnerId(), id)?.toDomain()

    override suspend fun addPlannedPayment(payment: PlannedPayment) =
        dao.insertPlannedPayment(payment.toEntity(ownerScope.currentOwnerId()))

    override suspend fun updatePlannedPayment(payment: PlannedPayment) =
        dao.updatePlannedPayment(payment.toEntity(ownerScope.currentOwnerId()))

    override suspend fun deletePlannedPayment(id: String) =
        dao.softDeletePlannedPayment(ownerScope.currentOwnerId(), id)
}

class DebtRepositoryImpl @Inject constructor(
    private val dao: DebtDao,
    private val ownerScope: WalletOwnerScope
) : DebtRepository {
    override fun getDebts(): Flow<List<Debt>> =
        ownerScope.ownerId.flatMapLatest { dao.getAllDebts(it) }.map { list -> list.map { it.toDomain() } }

    override suspend fun getDebt(id: String): Debt? =
        dao.getDebtById(ownerScope.currentOwnerId(), id)?.toDomain()

    override suspend fun addDebt(debt: Debt) =
        dao.insertDebt(debt.toEntity(ownerScope.currentOwnerId()))

    override suspend fun updateDebt(debt: Debt) =
        dao.updateDebt(debt.toEntity(ownerScope.currentOwnerId()))

    override suspend fun deleteDebt(id: String) =
        dao.softDeleteDebt(ownerScope.currentOwnerId(), id)
}
