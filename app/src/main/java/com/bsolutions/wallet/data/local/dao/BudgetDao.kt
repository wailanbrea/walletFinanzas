package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.BudgetEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE ownerId = :ownerId AND isDeleted = 0")
    fun getAllBudgets(ownerId: String): Flow<List<BudgetEntity>>
    
    @Query("SELECT * FROM budgets WHERE ownerId = :ownerId AND categoryId = :categoryId AND isDeleted = 0")
    suspend fun getBudgetByCategory(ownerId: String, categoryId: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE ownerId = :ownerId AND needsSync = 1 ORDER BY id")
    suspend fun getNeedingSync(ownerId: String): List<BudgetEntity>

    @Query("SELECT COUNT(*) FROM budgets WHERE ownerId = :ownerId AND needsSync = 1")
    fun countNeedingSync(ownerId: String): Flow<Int>

    @Query("UPDATE budgets SET needsSync = 0 WHERE ownerId = :ownerId AND id = :id")
    suspend fun markSynced(ownerId: String, id: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)
    
    @Update
    suspend fun updateBudget(budget: BudgetEntity)
    
    @Query("UPDATE budgets SET isDeleted = 1, needsSync = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeleteBudget(ownerId: String, id: String)
}
