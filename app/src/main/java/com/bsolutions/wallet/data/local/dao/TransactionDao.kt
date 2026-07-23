package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity;
import com.bsolutions.wallet.data.local.entity.TransactionEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 ORDER BY date DESC")
    fun getAllTransactions(ownerId: String): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND accountId = :accountId AND isDeleted = 0 ORDER BY date DESC")
    fun getTransactionsByAccount(ownerId: String, accountId: String): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND id = :id AND isDeleted = 0")
    suspend fun getTransactionById(ownerId: String, id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND id = :id")
    suspend fun getTransactionByIdIncludingDeleted(ownerId: String, id: String): TransactionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    /** Débito con guardia de fondos suficientes (para transferencias). */
    @Query("UPDATE accounts SET balance = balance - :amount WHERE ownerId = :ownerId AND id = :accountId AND isDeleted = 0 AND balance >= :amount")
    suspend fun debitAccount(ownerId: String, accountId: String, amount: Long): Int

    /** Débito sin guardia: los gastos pueden dejar la cuenta en negativo (tarjetas/sobregiro). */
    @Query("UPDATE accounts SET balance = balance - :amount WHERE ownerId = :ownerId AND id = :accountId AND isDeleted = 0")
    suspend fun subtractFromAccount(ownerId: String, accountId: String, amount: Long): Int

    @Query("UPDATE accounts SET balance = balance + :amount WHERE ownerId = :ownerId AND id = :accountId AND isDeleted = 0")
    suspend fun creditAccount(ownerId: String, accountId: String, amount: Long): Int

    /**
     * Registra un ingreso o gasto y ajusta el saldo de la cuenta en UNA sola transacción
     * SQLite (evita el descuadre de la escritura en dos pasos). TRANSFER se maneja aparte
     * con [executeTransfer].
     */
    @Transaction
    suspend fun insertWithBalance(transaction: TransactionEntity) {
        insertTransaction(transaction)
        when (transaction.type) {
            "INCOME" -> creditAccount(transaction.ownerId, transaction.accountId, transaction.amount)
            "EXPENSE" -> subtractFromAccount(transaction.ownerId, transaction.accountId, transaction.amount)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingOp(op: PendingOperationEntity)

    /**
     * Igual que [insertWithBalance] pero encolando además la operación de sync
     * en la MISMA transacción SQLite (el movimiento y su subida van juntos).
     */
    @Transaction
    suspend fun insertWithBalanceAndOp(transaction: TransactionEntity, op: PendingOperationEntity?) {
        insertWithBalance(transaction)
        op?.let { insertPendingOp(it) }
    }

    /** Actualiza ambos saldos y registra el movimiento en una sola transacción SQLite. */
    @Transaction
    suspend fun executeTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Long,
        transaction: TransactionEntity
    ): Boolean {
        if (debitAccount(transaction.ownerId, fromAccountId, amount) != 1) return false
        check(creditAccount(transaction.ownerId, toAccountId, amount) == 1) { "La cuenta de destino ya no existe" }
        insertTransaction(transaction)
        return true
    }
    
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    /**
     * Actualiza un movimiento ajustando el saldo por la diferencia de monto, atómicamente.
     * Asume que la cuenta y el tipo no cambian (solo monto/categoría/nota).
     */
    @Transaction
    suspend fun updateWithBalance(updated: TransactionEntity, oldAmount: Long) {
        val diff = updated.amount - oldAmount
        when (updated.type) {
            "INCOME" -> creditAccount(updated.ownerId, updated.accountId, diff)      // diff negativo resta
            "EXPENSE" -> subtractFromAccount(updated.ownerId, updated.accountId, diff)
        }
        updateTransaction(updated)
    }

    @Query("UPDATE transactions SET isDeleted = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeleteTransaction(ownerId: String, id: String)

    /** Revierte el efecto del movimiento en el saldo y lo borra (soft) en una sola transacción. */
    @Transaction
    suspend fun softDeleteWithBalance(transaction: TransactionEntity) {
        when (transaction.type) {
            "INCOME" -> subtractFromAccount(transaction.ownerId, transaction.accountId, transaction.amount)
            "EXPENSE" -> creditAccount(transaction.ownerId, transaction.accountId, transaction.amount)
        }
        softDeleteTransaction(transaction.ownerId, transaction.id)
    }
}
