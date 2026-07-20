package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.TransactionEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND isDeleted = 0 ORDER BY date DESC")
    fun getTransactionsByAccount(accountId: String): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE id = :id AND isDeleted = 0")
    suspend fun getTransactionById(id: String): TransactionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("UPDATE accounts SET balance = balance - :amount WHERE id = :accountId AND isDeleted = 0 AND balance >= :amount")
    suspend fun debitAccount(accountId: String, amount: Long): Int

    @Query("UPDATE accounts SET balance = balance + :amount WHERE id = :accountId AND isDeleted = 0")
    suspend fun creditAccount(accountId: String, amount: Long): Int

    /** Actualiza ambos saldos y registra el movimiento en una sola transacción SQLite. */
    @Transaction
    suspend fun executeTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Long,
        transaction: TransactionEntity
    ): Boolean {
        if (debitAccount(fromAccountId, amount) != 1) return false
        check(creditAccount(toAccountId, amount) == 1) { "La cuenta de destino ya no existe" }
        insertTransaction(transaction)
        return true
    }
    
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
    
    @Query("UPDATE transactions SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteTransaction(id: String)
}
