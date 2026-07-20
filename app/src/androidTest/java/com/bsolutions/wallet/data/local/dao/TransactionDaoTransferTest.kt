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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTransferTest {
    @Test
    fun executeTransfer_updatesBothBalances_andStoresMovement() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            WalletDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("from", "Origen", "BANK", 1_000L, "DOP"))
            accounts.insertAccount(AccountEntity("to", "Destino", "BANK", 100L, "DOP"))

            val committed = transactions.executeTransfer(
                "from", "to", 250L,
                TransactionEntity("tx", "from", 250L, "TRANSFER", "", 1L, "Transferencia")
            )

            assertEquals(750L, accounts.getAccountById("from")!!.balance)
            assertEquals(350L, accounts.getAccountById("to")!!.balance)
            assertEquals("tx", transactions.getTransactionById("tx")!!.id)
            assertEquals(true, committed)
        } finally {
            database.close()
        }
    }

    @Test
    fun executeTransfer_rejectsInsufficientFunds_withoutChangingDestination() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            WalletDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("from", "Origen", "BANK", 100L, "DOP"))
            accounts.insertAccount(AccountEntity("to", "Destino", "BANK", 100L, "DOP"))

            val committed = transactions.executeTransfer(
                "from", "to", 250L,
                TransactionEntity("tx", "from", 250L, "TRANSFER", "", 1L, "Transferencia")
            )

            assertFalse(committed)
            assertEquals(100L, accounts.getAccountById("from")!!.balance)
            assertEquals(100L, accounts.getAccountById("to")!!.balance)
            assertEquals(null, transactions.getTransactionById("tx"))
        } finally {
            database.close()
        }
    }
}
