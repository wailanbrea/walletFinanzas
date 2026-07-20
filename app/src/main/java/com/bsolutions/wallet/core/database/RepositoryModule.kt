package com.bsolutions.wallet.core.database

import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.data.preferences.UserProfilePreferences
import com.bsolutions.wallet.data.repository.AccountRepositoryImpl
import com.bsolutions.wallet.data.repository.BudgetRepositoryImpl
import com.bsolutions.wallet.data.repository.CategoryRepositoryImpl
import com.bsolutions.wallet.data.repository.DebtRepositoryImpl
import com.bsolutions.wallet.data.repository.GoalRepositoryImpl
import com.bsolutions.wallet.data.repository.PlannedPaymentRepositoryImpl
import com.bsolutions.wallet.data.repository.TransactionRepositoryImpl
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.BudgetRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.repository.GoalRepository
import com.bsolutions.wallet.domain.repository.PlannedPaymentRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserProfilePreferences(
        impl: UserPreferencesRepository
    ): UserProfilePreferences

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: AccountRepositoryImpl
    ): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(
        impl: BudgetRepositoryImpl
    ): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(
        impl: GoalRepositoryImpl
    ): GoalRepository

    @Binds
    @Singleton
    abstract fun bindPlannedPaymentRepository(
        impl: PlannedPaymentRepositoryImpl
    ): PlannedPaymentRepository

    @Binds
    @Singleton
    abstract fun bindDebtRepository(
        impl: DebtRepositoryImpl
    ): DebtRepository
}
