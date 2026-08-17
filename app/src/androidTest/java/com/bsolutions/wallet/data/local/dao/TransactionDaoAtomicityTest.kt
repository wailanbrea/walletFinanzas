package com.bsolutions.wallet.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bsolutions.wallet.core.database.WalletDatabase
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Prueba que insertAllWithBalanceAndOps es atómico con la DAO real de Room.
 * Usa el WalletDatabase compilado (KSP generado en compileDebugKotlin).
 *
 * Si @Transaction está presente, el rollback revierte tx1 y tx3 cuando tx2 falla.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoAtomicityTest {

    private lateinit var db: WalletDatabase
    private lateinit var txDao: TransactionDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            WalletDatabase::class.java
        ).build()
        txDao = db.transactionDao()
    }

    @After
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun insertAllWithBalanceAndOps_is_atomic_duplicate_rolls_back_all() = runTest {
        // 1. Crear cuenta con saldo suficiente
        db.accountDao().insertAccount(
            AccountEntity(
                id = "acc-1",
                name = "Prueba",
                type = "CASH",
                balance = 10_000L,
                currency = "DOP",
                ownerId = "test-owner"
            )
        )

        // 2. Insertar tx2 FUERA del batch (para que sea duplicado dentro del batch)
        val tx2 = TransactionEntity(
            id = "tx-2",
            accountId = "acc-1",
            amount = 2_000L,
            type = "EXPENSE",
            categoryId = "",
            date = System.currentTimeMillis(),
            ownerId = "test-owner",
            currency = "DOP"
        )
        val inserted = txDao.insertWithBalance(tx2) // Primera inserción: OK, retorna true
        assertEquals(true, inserted)

        // 3. Preparar batch con tx1, tx2 (duplicado), tx3
        val tx1 = TransactionEntity(
            id = "tx-1",
            accountId = "acc-1",
            amount = 1_000L,
            type = "EXPENSE",
            categoryId = "",
            date = System.currentTimeMillis(),
            ownerId = "test-owner",
            currency = "DOP"
        )
        val tx3 = TransactionEntity(
            id = "tx-3",
            accountId = "acc-1",
            amount = 3_000L,
            type = "EXPENSE",
            categoryId = "",
            date = System.currentTimeMillis(),
            ownerId = "test-owner",
            currency = "DOP"
        )

        val ops = listOf(
            PendingOperationEntity("op-1", "TRANSACTION", "tx-1", "{}", System.currentTimeMillis(), ownerId = "test-owner"),
            PendingOperationEntity("op-2", "TRANSACTION", "tx-2", "{}", System.currentTimeMillis(), ownerId = "test-owner"),
            PendingOperationEntity("op-3", "TRANSACTION", "tx-3", "{}", System.currentTimeMillis(), ownerId = "test-owner")
        )

        // 4. Ejecutar batch: tx2 es duplicado → insertWithBalance retorna false → se lanza IllegalStateException
        var threw = false
        try {
            txDao.insertAllWithBalanceAndOps(listOf(tx1, tx2, tx3), ops)
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertEquals("Debe lanzar IllegalStateException al encontrar duplicado", true, threw)

        // 5. VERIFICACIÓN: con @Transaction activo, tx1 y tx3 deben ser revertidos.
        // tx2 (insertado fuera del batch) debe existir.
        val allTxs = txDao.getAllTransactionsOnce("test-owner")
        assertEquals(
            "Con @Transaction activo, el rollback debe revertir tx1 y tx3. " +
                "Solo tx2 (insertado fuera del batch) debe existir.",
            1,
            allTxs.size
        )
        assertEquals("tx-2", allTxs[0].id)
    }

    @Test
    fun insertAllWithBalanceAndOps_succeeds_when_all_valid() = runTest {
        // 1. Crear cuenta
        db.accountDao().insertAccount(
            AccountEntity(
                id = "acc-1",
                name = "Prueba",
                type = "CASH",
                balance = 10_000L,
                currency = "DOP",
                ownerId = "test-owner"
            )
        )

        val tx1 = TransactionEntity(
            id = "tx-ok-1",
            accountId = "acc-1",
            amount = 1_000L,
            type = "EXPENSE",
            categoryId = "",
            date = System.currentTimeMillis(),
            ownerId = "test-owner",
            currency = "DOP"
        )
        val tx2 = TransactionEntity(
            id = "tx-ok-2",
            accountId = "acc-1",
            amount = 2_000L,
            type = "EXPENSE",
            categoryId = "",
            date = System.currentTimeMillis(),
            ownerId = "test-owner",
            currency = "DOP"
        )

        val ops = listOf(
            PendingOperationEntity("op-ok-1", "TRANSACTION", "tx-ok-1", "{}", System.currentTimeMillis(), ownerId = "test-owner"),
            PendingOperationEntity("op-ok-2", "TRANSACTION", "tx-ok-2", "{}", System.currentTimeMillis(), ownerId = "test-owner")
        )

        // 2. Ejecutar batch sin errores
        txDao.insertAllWithBalanceAndOps(listOf(tx1, tx2), ops)

        // 3. VERIFICACIÓN: ambas transacciones deben existir
        val allTxs = txDao.getAllTransactionsOnce("test-owner")
        assertEquals(2, allTxs.size)
        val ids = allTxs.map { it.id }.toSet()
        assertEquals(setOf("tx-ok-1", "tx-ok-2"), ids)
    }

    @Test
    fun insertWithBalance_returns_false_on_duplicate() = runTest {
        // 1. Crear cuenta
        db.accountDao().insertAccount(
            AccountEntity(
                id = "acc-1",
                name = "Prueba",
                type = "CASH",
                balance = 10_000L,
                currency = "DOP",
                ownerId = "test-owner"
            )
        )

        val tx = TransactionEntity(
            id = "tx-dup",
            accountId = "acc-1",
            amount = 1_000L,
            type = "EXPENSE",
            categoryId = "",
            date = System.currentTimeMillis(),
            ownerId = "test-owner",
            currency = "DOP"
        )

        // 2. Primera inserción: OK
        val result1 = txDao.insertWithBalance(tx)
        assertEquals(true, result1)

        // 3. Segunda inserción del mismo ID: duplicado, retorna false
        val result2 = txDao.insertWithBalance(tx)
        assertEquals(false, result2)

        // 4. VERIFICACIÓN: solo una copia debe existir
        val allTxs = txDao.getAllTransactionsOnce("test-owner")
        assertEquals(1, allTxs.size)
        assertEquals("tx-dup", allTxs[0].id)
    }
}
