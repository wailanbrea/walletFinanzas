package com.bsolutions.wallet.data.local.dao

import androidx.room.*
import com.bsolutions.wallet.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE ownerId = :ownerId AND isDeleted = 0 ORDER BY isClosed ASC, dueDate ASC")
    fun getAllDebts(ownerId: String): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE ownerId = :ownerId AND id = :id AND isDeleted = 0")
    suspend fun getDebtById(ownerId: String, id: String): DebtEntity?

    @Query("SELECT * FROM debts WHERE ownerId = :ownerId AND needsSync = 1 ORDER BY id")
    suspend fun getNeedingSync(ownerId: String): List<DebtEntity>

    @Query("SELECT COUNT(*) FROM debts WHERE ownerId = :ownerId AND needsSync = 1")
    fun countNeedingSync(ownerId: String): Flow<Int>

    @Query("UPDATE debts SET needsSync = 0 WHERE ownerId = :ownerId AND id = :id")
    suspend fun markSynced(ownerId: String, id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity)

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Query("UPDATE debts SET isDeleted = 1, needsSync = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeleteDebt(ownerId: String, id: String)
}
