package com.bsolutions.wallet.data.local.dao

import androidx.room.*
import com.bsolutions.wallet.data.local.entity.PlannedPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedPaymentDao {
    @Query("SELECT * FROM planned_payments WHERE isDeleted = 0 ORDER BY nextDueDate ASC")
    fun getAllPlannedPayments(): Flow<List<PlannedPaymentEntity>>

    @Query("SELECT * FROM planned_payments WHERE id = :id AND isDeleted = 0")
    suspend fun getPlannedPaymentById(id: String): PlannedPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedPayment(payment: PlannedPaymentEntity)

    @Update
    suspend fun updatePlannedPayment(payment: PlannedPaymentEntity)

    @Query("UPDATE planned_payments SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeletePlannedPayment(id: String)
}
