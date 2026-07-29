package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.AccountEntity;
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND isDeleted = 0")
    fun getAllAccounts(ownerId: String): Flow<List<AccountEntity>>
    
    /** Lectura puntual para el respaldo de sincronización (no reactiva). */
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND isDeleted = 0")
    suspend fun getAllAccountsOnce(ownerId: String): List<AccountEntity>

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

    /** Edita la cuenta y encola su subida: sin esto el cambio se queda en el telefono. */
    @Transaction
    suspend fun updateWithOp(account: AccountEntity, op: PendingOperationEntity?) {
        updateAccount(account)
        op?.let { insertPendingOp(it) }
    }

    @Query("UPDATE accounts SET isDeleted = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeleteAccount(ownerId: String, id: String)

    /**
     * Borra y encola la lapida. La cuenta ya borrada se lee de nuevo para que la
     * operacion encolada lleve isDeleted = 1 y el backend la marque inactiva.
     */
    @Transaction
    suspend fun softDeleteWithOp(ownerId: String, id: String, op: (AccountEntity) -> PendingOperationEntity) {
        softDeleteAccount(ownerId, id)
        getAccountByIdIncludingDeleted(ownerId, id)?.let { insertPendingOp(op(it)) }
    }
}
