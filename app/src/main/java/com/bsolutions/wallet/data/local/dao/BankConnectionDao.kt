package com.bsolutions.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bsolutions.wallet.data.local.entity.BankConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankConnectionDao {
    @Query("SELECT * FROM bank_connections WHERE ownerId = :ownerId ORDER BY providerName")
    fun getAll(ownerId: String): Flow<List<BankConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(connections: List<BankConnectionEntity>)

    @Query("DELETE FROM bank_connections WHERE ownerId = :ownerId AND id = :id")
    suspend fun delete(ownerId: String, id: String)
}
