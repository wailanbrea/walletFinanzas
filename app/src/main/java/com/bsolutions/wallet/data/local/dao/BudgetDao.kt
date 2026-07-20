package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.BudgetEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE isDeleted = 0")
    fun getAllBudgets(): Flow<List<BudgetEntity>>
    
    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND isDeleted = 0")
    suspend fun getBudgetByCategory(categoryId: String): BudgetEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)
    
    @Update
    suspend fun updateBudget(budget: BudgetEntity)
    
    @Query("UPDATE budgets SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteBudget(id: String)
}
