package com.bsolutions.wallet.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bsolutions.wallet.core.database.WalletDatabase
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTransferTest {
    // Las dos pruebas de executeTransfer se retiraron con la funcion: una transferencia
    // son ahora dos movimientos normales, cada uno por insertWithBalance, porque el
    // servidor no conoce el concepto de transferencia. Lo que probaban —que el saldo se
    // mueve atomicamente y que un fallo no deja rastro— lo cubren las de aqui abajo.

    private fun newDb(): WalletDatabase = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        WalletDatabase::class.java
    ).allowMainThreadQueries().build()

    @Test
    fun insertWithBalance_expense_subtractsAtomically() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("a", "Efectivo", "CASH", 10_000L, "DOP"))

            transactions.insertWithBalance(TransactionEntity("t1", "a", 2_500L, "EXPENSE", "", 1L, "Compra"))

            assertEquals(7_500L, accounts.getAccountById("guest", "a")!!.balance)
            assertEquals("t1", transactions.getTransactionById("guest", "t1")!!.id)
        } finally {
            database.close()
        }
    }

    @Test
    fun insertWithBalance_sameIdDoesNotApplyBalanceTwice() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("a", "Efectivo", "CASH", 10_000L, "DOP"))
            val transaction = TransactionEntity("email_candidate", "a", 2_500L, "EXPENSE", "", 1L, "Compra")

            transactions.insertWithBalance(transaction)
            transactions.insertWithBalance(transaction)

            assertEquals(7_500L, accounts.getAccountById("guest", "a")!!.balance)
            transactions.softDeleteWithBalance(transaction)
            val retry = runCatching { transactions.insertWithBalance(transaction) }

            assertTrue(retry.isFailure)
            assertEquals(10_000L, accounts.getAccountById("guest", "a")!!.balance)
        } finally {
            database.close()
        }
    }

    @Test
    fun insertWithBalance_missingAccountRollsBackLedgerEntry() = runBlocking {
        val database = newDb()
        try {
            val transactions = database.transactionDao()
            val transaction = TransactionEntity("orphan", "missing", 2_500L, "EXPENSE", "", 1L, "Compra")

            val result = runCatching { transactions.insertWithBalance(transaction) }

            assertTrue(result.isFailure)
            assertEquals(null, transactions.getTransactionByIdIncludingDeleted("guest", "orphan"))
        } finally {
            database.close()
        }
    }

    @Test
    fun insertWithBalance_overflowRollsBackLedgerEntry() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("a", "Cuenta", "BANK", Long.MAX_VALUE, "DOP"))
            val transaction = TransactionEntity("overflow", "a", 1L, "INCOME", "", 1L, "Abono")

            val result = runCatching { transactions.insertWithBalance(transaction) }

            assertTrue(result.isFailure)
            assertEquals(Long.MAX_VALUE, accounts.getAccountById("guest", "a")!!.balance)
            assertEquals(null, transactions.getTransactionByIdIncludingDeleted("guest", "overflow"))
        } finally {
            database.close()
        }
    }

    @Test
    fun insertWithBalance_income_addsAtomically_andExpenseMayGoNegative() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("a", "Tarjeta", "CREDIT_CARD", 0L, "DOP"))

            transactions.insertWithBalance(TransactionEntity("i", "a", 1_000L, "INCOME", "", 1L, "Abono"))
            transactions.insertWithBalance(TransactionEntity("e", "a", 3_000L, "EXPENSE", "", 2L, "Gasto"))

            // 0 + 1000 - 3000 = -2000 (los gastos pueden dejar la tarjeta en negativo)
            assertEquals(-2_000L, accounts.getAccountById("guest", "a")!!.balance)
        } finally {
            database.close()
        }
    }

    @Test
    fun updateWithBalance_appliesOnlyNetDifference() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("a", "Efectivo", "CASH", 10_000L, "DOP"))
            transactions.insertWithBalance(TransactionEntity("t", "a", 2_500L, "EXPENSE", "", 1L, "Compra"))
            // saldo = 7_500

            transactions.updateWithBalance(TransactionEntity("t", "a", 4_000L, "EXPENSE", "", 1L, "Taxi"), oldAmount = 2_500L)

            // 7_500 - (4_000 - 2_500) = 6_000
            assertEquals(6_000L, accounts.getAccountById("guest", "a")!!.balance)
        } finally {
            database.close()
        }
    }

    @Test
    fun softDeleteWithBalance_revertsEffect_keepingBalanceEqualToLedger() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("a", "Efectivo", "CASH", 10_000L, "DOP"))
            transactions.insertWithBalance(TransactionEntity("t", "a", 2_500L, "EXPENSE", "", 1L, "Compra"))

            transactions.softDeleteWithBalance(TransactionEntity("t", "a", 2_500L, "EXPENSE", "", 1L, "Compra"))

            // El gasto revertido devuelve el saldo original; el movimiento queda soft-deleted.
            assertEquals(10_000L, accounts.getAccountById("guest", "a")!!.balance)
            assertEquals(null, transactions.getTransactionById("guest", "t"))
        } finally {
            database.close()
        }
    }
}
