package com.bsolutions.wallet.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.BankConnectionEntity
import com.bsolutions.wallet.data.local.entity.BudgetEntity
import com.bsolutions.wallet.data.local.entity.CategoryEntity
import com.bsolutions.wallet.data.local.entity.DebtEntity
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import com.bsolutions.wallet.data.local.entity.GoalEntity
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity
import com.bsolutions.wallet.data.local.entity.PlannedPaymentEntity
import com.bsolutions.wallet.data.local.entity.NotificationSourceEntity
import com.bsolutions.wallet.data.local.entity.RawBankNoticeEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import com.bsolutions.wallet.data.local.entity.WALLET_GUEST_OWNER_ID
import com.bsolutions.wallet.data.repository.WalletSessionStore
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.data.preferences.CategoryRuleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val USER_OWNER_PREFIX = "user:"

@Singleton
class WalletOwnerScope @Inject constructor(session: WalletSessionStore) {
    private val mutableOwnerId = MutableStateFlow(ownerIdFor(session.user?.uid))
    val ownerId: StateFlow<String> = mutableOwnerId.asStateFlow()

    fun currentOwnerId(): String = mutableOwnerId.value

    internal fun activateUser(userId: String) {
        require(userId.isNotBlank()) { "El identificador de usuario no puede estar vacio." }
        mutableOwnerId.value = ownerIdFor(userId)
    }

    internal fun activateGuest() {
        mutableOwnerId.value = WALLET_GUEST_OWNER_ID
    }

    companion object {
        fun ownerIdFor(userId: String?): String =
            userId?.takeIf(String::isNotBlank)?.let { "$USER_OWNER_PREFIX$it" }
                ?: WALLET_GUEST_OWNER_ID
    }
}

interface LocalDataIsolation {
    suspend fun activateUser(userId: String)
    suspend fun reconcileCurrentSession()
    fun activateGuest()
}

object NoOpLocalDataIsolation : LocalDataIsolation {
    override suspend fun activateUser(userId: String) = Unit
    override suspend fun reconcileCurrentSession() = Unit
    override fun activateGuest() = Unit
}

@Singleton
class RoomLocalDataIsolation @Inject constructor(
    private val database: WalletDatabase,
    private val ownerScope: WalletOwnerScope,
    private val userPreferences: UserPreferencesRepository,
    private val categoryRules: CategoryRuleStore
) : LocalDataIsolation {
    override suspend fun activateUser(userId: String) {
        val targetOwnerId = WalletOwnerScope.ownerIdFor(userId)
        if (ownerScope.currentOwnerId() == WALLET_GUEST_OWNER_ID) {
            database.ownerIsolationDao().mergeOwner(WALLET_GUEST_OWNER_ID, targetOwnerId)
            userPreferences.mergeGuestInto(targetOwnerId)
            categoryRules.mergeGuestInto(targetOwnerId)
        }
        ownerScope.activateUser(userId)
    }

    override suspend fun reconcileCurrentSession() {
        val targetOwnerId = ownerScope.currentOwnerId()
        if (targetOwnerId != WALLET_GUEST_OWNER_ID) {
            database.ownerIsolationDao().mergeOwner(WALLET_GUEST_OWNER_ID, targetOwnerId)
            userPreferences.mergeGuestInto(targetOwnerId)
            categoryRules.mergeGuestInto(targetOwnerId)
        }
    }

    override fun activateGuest() = ownerScope.activateGuest()
}

@Dao
interface OwnerIsolationDao {
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId") suspend fun accounts(ownerId: String): List<AccountEntity>
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId") suspend fun transactions(ownerId: String): List<TransactionEntity>
    @Query("SELECT * FROM categories WHERE ownerId = :ownerId") suspend fun categories(ownerId: String): List<CategoryEntity>
    @Query("SELECT * FROM budgets WHERE ownerId = :ownerId") suspend fun budgets(ownerId: String): List<BudgetEntity>
    @Query("SELECT * FROM goals WHERE ownerId = :ownerId") suspend fun goals(ownerId: String): List<GoalEntity>
    @Query("SELECT * FROM planned_payments WHERE ownerId = :ownerId") suspend fun plannedPayments(ownerId: String): List<PlannedPaymentEntity>
    @Query("SELECT * FROM debts WHERE ownerId = :ownerId") suspend fun debts(ownerId: String): List<DebtEntity>
    @Query("SELECT * FROM bank_connections WHERE ownerId = :ownerId") suspend fun bankConnections(ownerId: String): List<BankConnectionEntity>
    @Query("SELECT * FROM pending_operations WHERE ownerId = :ownerId") suspend fun pendingOperations(ownerId: String): List<PendingOperationEntity>
    @Query("SELECT * FROM detected_movements WHERE ownerId = :ownerId") suspend fun detectedMovements(ownerId: String): List<DetectedMovementEntity>
    @Query("SELECT * FROM notification_sources WHERE ownerId = :ownerId") suspend fun notificationSources(ownerId: String): List<NotificationSourceEntity>
    @Query("SELECT * FROM raw_bank_notices WHERE ownerId = :ownerId") suspend fun rawBankNotices(ownerId: String): List<RawBankNoticeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAccounts(values: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTransactions(values: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(values: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBudgets(values: List<BudgetEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGoals(values: List<GoalEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPlannedPayments(values: List<PlannedPaymentEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDebts(values: List<DebtEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBankConnections(values: List<BankConnectionEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPendingOperations(values: List<PendingOperationEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDetectedMovements(values: List<DetectedMovementEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertNotificationSources(values: List<NotificationSourceEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRawBankNotices(values: List<RawBankNoticeEntity>)

    @Query("DELETE FROM accounts WHERE ownerId = :ownerId") suspend fun deleteAccounts(ownerId: String)
    @Query("DELETE FROM transactions WHERE ownerId = :ownerId") suspend fun deleteTransactions(ownerId: String)
    @Query("DELETE FROM categories WHERE ownerId = :ownerId") suspend fun deleteCategories(ownerId: String)
    @Query("DELETE FROM budgets WHERE ownerId = :ownerId") suspend fun deleteBudgets(ownerId: String)
    @Query("DELETE FROM goals WHERE ownerId = :ownerId") suspend fun deleteGoals(ownerId: String)
    @Query("DELETE FROM planned_payments WHERE ownerId = :ownerId") suspend fun deletePlannedPayments(ownerId: String)
    @Query("DELETE FROM debts WHERE ownerId = :ownerId") suspend fun deleteDebts(ownerId: String)
    @Query("DELETE FROM bank_connections WHERE ownerId = :ownerId") suspend fun deleteBankConnections(ownerId: String)
    @Query("DELETE FROM pending_operations WHERE ownerId = :ownerId") suspend fun deletePendingOperations(ownerId: String)
    @Query("DELETE FROM detected_movements WHERE ownerId = :ownerId") suspend fun deleteDetectedMovements(ownerId: String)
    @Query("DELETE FROM notification_sources WHERE ownerId = :ownerId") suspend fun deleteNotificationSources(ownerId: String)
    @Query("DELETE FROM raw_bank_notices WHERE ownerId = :ownerId") suspend fun deleteRawBankNotices(ownerId: String)

    @Transaction
    suspend fun mergeOwner(sourceOwnerId: String, targetOwnerId: String) {
        if (sourceOwnerId == targetOwnerId) return

        insertAccounts(accounts(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertCategories(categories(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertTransactions(transactions(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertBudgets(budgets(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertGoals(goals(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertPlannedPayments(plannedPayments(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertDebts(debts(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertBankConnections(bankConnections(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertPendingOperations(pendingOperations(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertDetectedMovements(detectedMovements(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertNotificationSources(notificationSources(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })
        insertRawBankNotices(rawBankNotices(sourceOwnerId).map { it.copy(ownerId = targetOwnerId) })

        deleteRawBankNotices(sourceOwnerId)
        deleteNotificationSources(sourceOwnerId)
        deleteTransactions(sourceOwnerId)
        deleteBudgets(sourceOwnerId)
        deletePlannedPayments(sourceOwnerId)
        deleteDebts(sourceOwnerId)
        deleteGoals(sourceOwnerId)
        deleteBankConnections(sourceOwnerId)
        deletePendingOperations(sourceOwnerId)
        deleteDetectedMovements(sourceOwnerId)
        deleteCategories(sourceOwnerId)
        deleteAccounts(sourceOwnerId)
    }
}
