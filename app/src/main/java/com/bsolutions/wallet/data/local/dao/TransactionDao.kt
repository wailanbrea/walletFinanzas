package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity;
import com.bsolutions.wallet.data.local.entity.TransactionEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 ORDER BY date DESC")
    fun getAllTransactions(ownerId: String): Flow<List<TransactionEntity>>
    
    /** Lectura puntual para el respaldo de sincronización (no reactiva). */
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0")
    suspend fun getAllTransactionsOnce(ownerId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND accountId = :accountId AND isDeleted = 0 ORDER BY date DESC")
    fun getTransactionsByAccount(ownerId: String, accountId: String): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND id = :id AND isDeleted = 0")
    suspend fun getTransactionById(ownerId: String, id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND id = :id")
    suspend fun getTransactionByIdIncludingDeleted(ownerId: String, id: String): TransactionEntity?

    @Query("SELECT currency FROM accounts WHERE ownerId = :ownerId AND id = :accountId AND isDeleted = 0")
    suspend fun getAccountCurrency(ownerId: String, accountId: String): String?

    @Query("SELECT balance FROM accounts WHERE ownerId = :ownerId AND id = :accountId AND isDeleted = 0")
    suspend fun getAccountBalance(ownerId: String, accountId: String): Long?

    @Query("SELECT COUNT(*) > 0 FROM categories WHERE ownerId = :ownerId AND id = :categoryId AND isDeleted = 0")
    suspend fun categoryExists(ownerId: String, categoryId: String): Boolean
    
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
    suspend fun insertWithBalance(transaction: TransactionEntity): Boolean {
        require(transaction.amount > 0L) { "El monto debe ser mayor que cero" }
        require(transaction.type == "INCOME" || transaction.type == "EXPENSE") { "Tipo de movimiento inválido" }
        val existing = getTransactionByIdIncludingDeleted(transaction.ownerId, transaction.id)
        check(existing?.isDeleted != true) { "El movimiento ya fue eliminado" }
        if (existing != null) return false
        check(getAccountCurrency(transaction.ownerId, transaction.accountId) == transaction.currency) {
            "La cuenta ya no existe o cambió de moneda"
        }
        if (transaction.categoryId.isNotBlank()) {
            check(categoryExists(transaction.ownerId, transaction.categoryId)) { "La categoría ya no existe" }
        }
        val balance = checkNotNull(getAccountBalance(transaction.ownerId, transaction.accountId))
        when (transaction.type) {
            "INCOME" -> Math.addExact(balance, transaction.amount)
            "EXPENSE" -> Math.subtractExact(balance, transaction.amount)
        }
        insertTransaction(transaction)
        val updated = when (transaction.type) {
            "INCOME" -> creditAccount(transaction.ownerId, transaction.accountId, transaction.amount)
            "EXPENSE" -> subtractFromAccount(transaction.ownerId, transaction.accountId, transaction.amount)
            else -> 0
        }
        check(updated == 1) { "No se pudo actualizar el saldo de la cuenta" }
        return true
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingOp(op: PendingOperationEntity)

    /**
     * Igual que [insertWithBalance] pero encolando además la operación de sync
     * en la MISMA transacción SQLite (el movimiento y su subida van juntos).
     */
    @Transaction
    suspend fun insertWithBalanceAndOp(transaction: TransactionEntity, op: PendingOperationEntity?) {
        if (insertWithBalance(transaction)) op?.let { insertPendingOp(it) }
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
    suspend fun updateTransaction(transaction: TransactionEntity): Int

    /**
     * Actualiza un movimiento ajustando el saldo por la diferencia de monto, atómicamente.
     * Asume que la cuenta y el tipo no cambian (solo monto/categoría/nota).
     */
    @Transaction
    suspend fun updateWithBalance(updated: TransactionEntity, oldAmount: Long) {
        require(updated.amount > 0L) { "El monto debe ser mayor que cero" }
        val original = checkNotNull(getTransactionById(updated.ownerId, updated.id))
        check(original.amount == oldAmount) { "El movimiento cambió durante la edición" }
        check(original.accountId == updated.accountId && original.type == updated.type) {
            "No se puede cambiar la cuenta o el tipo del movimiento"
        }
        check(getAccountCurrency(updated.ownerId, updated.accountId) == updated.currency) {
            "La cuenta ya no existe o cambió de moneda"
        }
        if (updated.categoryId.isNotBlank()) {
            check(categoryExists(updated.ownerId, updated.categoryId)) { "La categoría ya no existe" }
        }
        val balance = checkNotNull(getAccountBalance(updated.ownerId, updated.accountId))
        val diff = Math.subtractExact(updated.amount, original.amount)
        when (updated.type) {
            "INCOME" -> Math.addExact(balance, diff)
            "EXPENSE" -> Math.subtractExact(balance, diff)
            else -> error("Tipo de movimiento inválido")
        }
        val balanceUpdated = when (updated.type) {
            "INCOME" -> creditAccount(updated.ownerId, updated.accountId, diff)
            "EXPENSE" -> subtractFromAccount(updated.ownerId, updated.accountId, diff)
            else -> 0
        }
        check(balanceUpdated == 1) { "No se pudo actualizar el saldo de la cuenta" }
        check(updateTransaction(updated) == 1) { "No se pudo actualizar el movimiento" }
    }

    @Query("UPDATE transactions SET isDeleted = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeleteTransaction(ownerId: String, id: String): Int

    /** Revierte el efecto del movimiento en el saldo y lo borra (soft) en una sola transacción. */
    @Transaction
    suspend fun softDeleteWithBalance(transaction: TransactionEntity) {
        val current = getTransactionById(transaction.ownerId, transaction.id) ?: return
        check(getAccountCurrency(current.ownerId, current.accountId) == current.currency) {
            "La cuenta ya no existe o cambió de moneda"
        }
        val balance = checkNotNull(getAccountBalance(current.ownerId, current.accountId))
        when (current.type) {
            "INCOME" -> Math.subtractExact(balance, current.amount)
            "EXPENSE" -> Math.addExact(balance, current.amount)
            else -> error("Tipo de movimiento inválido")
        }
        val balanceUpdated = when (current.type) {
            "INCOME" -> subtractFromAccount(current.ownerId, current.accountId, current.amount)
            "EXPENSE" -> creditAccount(current.ownerId, current.accountId, current.amount)
            else -> 0
        }
        check(balanceUpdated == 1) { "No se pudo revertir el saldo de la cuenta" }
        check(softDeleteTransaction(current.ownerId, current.id) == 1) { "No se pudo eliminar el movimiento" }
    }
}
