package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.AccountEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isDeleted = 0")
    fun getAllAccounts(): Flow<List<AccountEntity>>
    
    @Query("SELECT * FROM accounts WHERE id = :id AND isDeleted = 0")
    suspend fun getAccountById(id: String): AccountEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)
    
    @Update
    suspend fun updateAccount(account: AccountEntity)
    
    @Query("UPDATE accounts SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteAccount(id: String)
}
