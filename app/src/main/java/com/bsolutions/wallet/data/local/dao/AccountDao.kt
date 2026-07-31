package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.AccountEntity;
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity;
import com.bsolutions.wallet.data.local.entity.TransactionEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND isDeleted = 0")
    fun getAllAccounts(ownerId: String): Flow<List<AccountEntity>>
    
    /** Lectura puntual para el respaldo de sincronización (no reactiva). */
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND isDeleted = 0")
    suspend fun getAllAccountsOnce(ownerId: String): List<AccountEntity>

    /**
     * Incluye las borradas: su lápida también tiene que subir, o el borrado se queda
     * en este teléfono y los demás siguen viendo la cuenta.
     */
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId")
    suspend fun getAllAccountsIncludingDeletedOnce(ownerId: String): List<AccountEntity>

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

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND accountId = :accountId AND isDeleted = 0")
    suspend fun transactionsOfAccount(ownerId: String, accountId: String): List<TransactionEntity>

    @Query("UPDATE transactions SET isDeleted = 1 WHERE ownerId = :ownerId AND accountId = :accountId AND isDeleted = 0")
    suspend fun softDeleteTransactionsOfAccount(ownerId: String, accountId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionPendingOp(op: PendingOperationEntity)

    /**
     * Borra la cuenta arrastrando sus movimientos, todo en una sola transaccion SQLite.
     *
     * Se borran de verdad y no se dejan huerfanos: un movimiento cuya cuenta ya no existe
     * no se puede ver, ni editar, ni deshacer, pero seguia contando en los totales del
     * panel. La cuenta desaparecia y el gasto se quedaba.
     *
     * No se toca ningun saldo. El de la cuenta borrada da igual, y ningun otro se mueve
     * porque un movimiento solo afecta al de su propia cuenta.
     *
     * Devuelve lo que arrastro para que quien llame pueda deshacer su efecto en las
     * deudas: eso vive en el dominio y no puede resolverse desde aqui.
     */
    @Transaction
    suspend fun softDeleteWithTransactionsAndOps(
        ownerId: String,
        id: String,
        accountOp: (AccountEntity) -> PendingOperationEntity,
        transactionOp: (TransactionEntity) -> PendingOperationEntity
    ): List<TransactionEntity> {
        val dragged = transactionsOfAccount(ownerId, id)
        softDeleteTransactionsOfAccount(ownerId, id)
        // La lapida lleva isDeleted = 1 para que el push mande el DELETE al servidor; sin
        // ella el movimiento volveria en la siguiente sincronizacion, ya sin cuenta.
        dragged.forEach { insertTransactionPendingOp(transactionOp(it.copy(isDeleted = true))) }
        softDeleteAccount(ownerId, id)
        getAccountByIdIncludingDeleted(ownerId, id)?.let { insertPendingOp(accountOp(it)) }

        return dragged
    }
}
