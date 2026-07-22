package com.bsolutions.wallet.data.local.dao

import androidx.room.*
import com.bsolutions.wallet.data.local.entity.PlannedPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedPaymentDao {
    @Query("SELECT * FROM planned_payments WHERE ownerId = :ownerId AND isDeleted = 0 ORDER BY nextDueDate ASC")
    fun getAllPlannedPayments(ownerId: String): Flow<List<PlannedPaymentEntity>>

    @Query("SELECT * FROM planned_payments WHERE ownerId = :ownerId AND id = :id AND isDeleted = 0")
    suspend fun getPlannedPaymentById(ownerId: String, id: String): PlannedPaymentEntity?

    @Query("SELECT * FROM planned_payments WHERE ownerId = :ownerId AND needsSync = 1 ORDER BY id")
    suspend fun getNeedingSync(ownerId: String): List<PlannedPaymentEntity>

    @Query("SELECT COUNT(*) FROM planned_payments WHERE ownerId = :ownerId AND needsSync = 1")
    fun countNeedingSync(ownerId: String): Flow<Int>

    @Query("UPDATE planned_payments SET needsSync = 0 WHERE ownerId = :ownerId AND id = :id")
    suspend fun markSynced(ownerId: String, id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedPayment(payment: PlannedPaymentEntity)

    @Update
    suspend fun updatePlannedPayment(payment: PlannedPaymentEntity)

    @Query("UPDATE planned_payments SET isDeleted = 1, needsSync = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeletePlannedPayment(ownerId: String, id: String)
}
