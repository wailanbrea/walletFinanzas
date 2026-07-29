package com.bsolutions.wallet.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bsolutions.wallet.data.local.dao.AccountDao
import com.bsolutions.wallet.data.local.dao.BankConnectionDao
import com.bsolutions.wallet.data.local.dao.BudgetDao
import com.bsolutions.wallet.data.local.dao.CategoryDao
import com.bsolutions.wallet.data.local.dao.DebtDao
import com.bsolutions.wallet.data.local.dao.GoalDao
import com.bsolutions.wallet.data.local.dao.PendingOperationDao
import com.bsolutions.wallet.data.local.dao.PlannedPaymentDao
import com.bsolutions.wallet.data.local.dao.TransactionDao
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.BankConnectionEntity
import com.bsolutions.wallet.data.local.entity.BudgetEntity
import com.bsolutions.wallet.data.local.entity.CategoryEntity
import com.bsolutions.wallet.data.local.entity.DebtEntity
import com.bsolutions.wallet.data.local.entity.GoalEntity
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity
import com.bsolutions.wallet.data.local.entity.PlannedPaymentEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        PlannedPaymentEntity::class,
        DebtEntity::class,
        BankConnectionEntity::class,
        PendingOperationEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun plannedPaymentDao(): PlannedPaymentDao
    abstract fun debtDao(): DebtDao
    abstract fun bankConnectionDao(): BankConnectionDao
    abstract fun pendingOperationDao(): PendingOperationDao
    abstract fun ownerIsolationDao(): OwnerIsolationDao
}
