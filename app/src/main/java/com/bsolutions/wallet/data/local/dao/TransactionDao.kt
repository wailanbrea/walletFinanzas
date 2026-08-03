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

    @Query(
        "SELECT * FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND type = :type AND currency = :currency AND date BETWEEN :fromInclusive AND :toInclusive " +
            "ORDER BY date DESC"
    )
    suspend fun findRecentPotentialDuplicates(
        ownerId: String,
        type: String,
        currency: String,
        fromInclusive: Long,
        toInclusive: Long
    ): List<TransactionEntity>

    /** Like [findRecentPotentialDuplicates] but without currency filter – caller does normalised comparison. */
    @Query(
        "SELECT * FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND type = :type AND date BETWEEN :fromInclusive AND :toInclusive " +
            "ORDER BY date DESC"
    )
    suspend fun findRecentByTypeAndDate(
        ownerId: String,
        type: String,
        fromInclusive: Long,
        toInclusive: Long
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND accountId = :accountId AND isDeleted = 0 ORDER BY date DESC")
    fun getTransactionsByAccount(ownerId: String, accountId: String): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND id = :id AND isDeleted = 0")
    suspend fun getTransactionById(ownerId: String, id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND id = :id")
    suspend fun getTransactionByIdIncludingDeleted(ownerId: String, id: String): TransactionEntity?

    /**
     * Movimientos de una deuda: el gasto que la origino y los abonos recibidos.
     * Es la fuente de verdad de cuanto se ha cobrado.
     */
    @Query(
        "SELECT * FROM transactions WHERE ownerId = :ownerId AND debtId = :debtId " +
            "AND isDeleted = 0 ORDER BY date ASC"
    )
    suspend fun getTransactionsForDebt(ownerId: String, debtId: String): List<TransactionEntity>

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
     * SQLite (evita el descuadre de la escritura en dos pasos). Una transferencia son dos
     * movimientos, uno en cada cuenta, y cada uno pasa por aqui.
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

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity): Int

    /**
     * Actualiza un movimiento y encola su subida, sin tocar el saldo.
     *
     * Es el caso de atar o desatar un movimiento de una deuda: cambia a qué pertenece y
     * su categoría, pero el dinero ya se movió cuando se creó. Sin encolar aquí, el
     * cambio se quedaba en el teléfono donde se hizo y el otro seguía contando el
     * préstamo como gasto propio.
     */
    @Transaction
    suspend fun updateWithOp(updated: TransactionEntity, op: PendingOperationEntity?) {
        updateTransaction(updated)
        op?.let { insertPendingOp(it) }
    }

    /**
     * Actualiza un movimiento ajustando el saldo por la diferencia de monto, atómicamente.
     * Asume que la cuenta y el tipo no cambian (solo monto/categoría/nota).
     */
    @Transaction
    suspend fun updateWithBalanceAndOp(
        updated: TransactionEntity,
        oldAmount: Long,
        op: PendingOperationEntity?
    ) {
        updateWithBalance(updated, oldAmount)
        op?.let { insertPendingOp(it) }
    }

    /**
     * Borra un movimiento y encola la lapida, sin tocar el saldo.
     *
     * Es el caso de los movimientos que no movieron saldo por esta via, como los TRANSFER
     * antiguos. Sin encolar aqui el borrado se quedaba en el telefono y el movimiento
     * volvia del servidor en la siguiente sincronizacion.
     */
    @Transaction
    suspend fun softDeleteWithOp(transaction: TransactionEntity, op: PendingOperationEntity?) {
        softDeleteTransaction(transaction.ownerId, transaction.id)
        op?.let { insertPendingOp(it) }
    }

    @Transaction
    suspend fun softDeleteWithBalanceAndOp(transaction: TransactionEntity, op: PendingOperationEntity?) {
        softDeleteWithBalance(transaction)
        op?.let { insertPendingOp(it) }
    }

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
