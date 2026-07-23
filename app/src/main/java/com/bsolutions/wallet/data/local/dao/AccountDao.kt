package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.AccountEntity;
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND isDeleted = 0")
    fun getAllAccounts(ownerId: String): Flow<List<AccountEntity>>
    
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND id = :id AND isDeleted = 0")
    suspend fun getAccountById(ownerId: String, id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND id = :id")
    suspend fun getAccountByIdIncludingDeleted(ownerId: String, id: String): AccountEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingOp(op: PendingOperationEntity)

    /** Inserta la cuenta y encola su subida al backend en una sola transacción SQLite. */
    @Transaction
    suspend fun insertWithOp(account: AccountEntity, op: PendingOperationEntity?) {
        insertAccount(account)
        op?.let { insertPendingOp(it) }
    }
    
    @Update
    suspend fun updateAccount(account: AccountEntity)
    
    @Query("UPDATE accounts SET isDeleted = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeleteAccount(ownerId: String, id: String)
}
