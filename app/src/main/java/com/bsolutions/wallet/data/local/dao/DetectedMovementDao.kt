package com.bsolutions.wallet.data.local.dao

import androidx.room.*
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedMovementDao {
    @Query("SELECT * FROM detected_movements WHERE ownerId = :ownerId AND status = 'PENDING' ORDER BY detectedAt DESC")
    fun getPendingMovements(ownerId: String): Flow<List<DetectedMovementEntity>>

    @Query("SELECT * FROM detected_movements WHERE ownerId = :ownerId AND id = :id")
    suspend fun getMovementById(ownerId: String, id: String): DetectedMovementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: DetectedMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movements: List<DetectedMovementEntity>)

    @Query("UPDATE detected_movements SET status = :status, needsSync = :needsSync WHERE ownerId = :ownerId AND id = :id")
    suspend fun updateStatus(ownerId: String, id: String, status: String, needsSync: Boolean = true)

    @Query("DELETE FROM detected_movements WHERE ownerId = :ownerId AND id = :id")
    suspend fun deleteMovement(ownerId: String, id: String)

    @Query("DELETE FROM detected_movements WHERE ownerId = :ownerId AND status != 'PENDING'")
    suspend fun purgeProcessedMovements(ownerId: String)
}
