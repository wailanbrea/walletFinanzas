package com.bsolutions.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationDao {
    /** Cuentas primero (los movimientos dependen de ellas), luego por antigüedad. */
    @Query("SELECT * FROM pending_operations WHERE ownerId = :ownerId ORDER BY CASE entityType WHEN 'ACCOUNT' THEN 0 ELSE 1 END, createdAt")
    suspend fun getAll(ownerId: String): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operations WHERE ownerId = :ownerId")
    fun count(ownerId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: PendingOperationEntity)

    @Query("DELETE FROM pending_operations WHERE ownerId = :ownerId AND id = :id")
    suspend fun delete(ownerId: String, id: String)

    @Query("UPDATE pending_operations SET attempts = attempts + 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun bumpAttempts(ownerId: String, id: String)

    /** Descarta operaciones rechazadas repetidamente por el servidor (4xx persistente). */
    @Query("DELETE FROM pending_operations WHERE ownerId = :ownerId AND attempts >= :maxAttempts")
    suspend fun purgeFailed(ownerId: String, maxAttempts: Int)
}
