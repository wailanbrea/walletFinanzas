package com.bsolutions.wallet.core.database

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.bsolutions.wallet.data.local.dao.AccountDao
import com.bsolutions.wallet.data.local.dao.BudgetDao
import com.bsolutions.wallet.data.local.dao.CategoryDao
import com.bsolutions.wallet.data.local.dao.DebtDao
import com.bsolutions.wallet.data.local.dao.GoalDao
import com.bsolutions.wallet.data.local.dao.PlannedPaymentDao
import com.bsolutions.wallet.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "wallet_database"
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): WalletDatabase {
        // BD cifrada con SQLCipher (AES-256); clave aleatoria en EncryptedSharedPreferences
        System.loadLibrary("sqlcipher")
        DatabaseKeyProvider.ensureEncryptedDatabase(context, DATABASE_NAME)
        val passphrase = DatabaseKeyProvider.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            context,
            WalletDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(factory)
            .addMigrations(
                WalletDatabaseMigrations.MIGRATION_1_4,
                WalletDatabaseMigrations.MIGRATION_2_3,
                WalletDatabaseMigrations.MIGRATION_2_4,
                WalletDatabaseMigrations.MIGRATION_3_4,
                WalletDatabaseMigrations.MIGRATION_4_5,
                WalletDatabaseMigrations.MIGRATION_5_6,
                WalletDatabaseMigrations.MIGRATION_6_7,
                WalletDatabaseMigrations.MIGRATION_7_8,
                WalletDatabaseMigrations.MIGRATION_8_9,
                WalletDatabaseMigrations.MIGRATION_9_10,
                WalletDatabaseMigrations.MIGRATION_10_11,
                WalletDatabaseMigrations.MIGRATION_11_12,
                WalletDatabaseMigrations.MIGRATION_12_13
            )
            // Nunca borrar datos financieros ante un upgrade no cubierto: si faltara una
            // migración preferimos fallar (y verlo) a perder el dinero del usuario en silencio.
            // Solo el downgrade (dev que instala una build vieja) recrea la BD.
            .build()
    }

    @Provides
    fun provideAccountDao(database: WalletDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    fun provideTransactionDao(database: WalletDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideCategoryDao(database: WalletDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideBudgetDao(database: WalletDatabase): BudgetDao {
        return database.budgetDao()
    }

    @Provides
    fun provideGoalDao(database: WalletDatabase): GoalDao {
        return database.goalDao()
    }

    @Provides
    fun providePlannedPaymentDao(database: WalletDatabase): PlannedPaymentDao {
        return database.plannedPaymentDao()
    }

    @Provides
    fun provideDebtDao(database: WalletDatabase): DebtDao {
        return database.debtDao()
    }

    @Provides
    fun provideBankConnectionDao(database: WalletDatabase): com.bsolutions.wallet.data.local.dao.BankConnectionDao {
        return database.bankConnectionDao()
    }

    @Provides
    fun providePendingOperationDao(database: WalletDatabase): com.bsolutions.wallet.data.local.dao.PendingOperationDao {
        return database.pendingOperationDao()
    }

    @Provides
    fun provideDetectedMovementDao(database: WalletDatabase): com.bsolutions.wallet.data.local.dao.DetectedMovementDao {
        return database.detectedMovementDao()
    }

    @Provides
    @Singleton
    fun provideLocalDataIsolation(implementation: RoomLocalDataIsolation): LocalDataIsolation = implementation
}
