package com.bsolutions.wallet.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bsolutions.wallet.core.database.WalletDatabase
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Borrar una cuenta se lleva sus movimientos.
 *
 * Antes se quedaban huerfanos: sin cuenta no se podian ver, ni editar, ni deshacer, pero
 * seguian sumando en los totales del panel. La cuenta desaparecia y el gasto se quedaba.
 */
@RunWith(AndroidJUnit4::class)
class AccountCascadeDeleteTest {

    private fun newDb(): WalletDatabase = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        WalletDatabase::class.java
    ).allowMainThreadQueries().build()

    @Test
    fun deletingAnAccountTakesItsMovementsWithIt() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("cuenta", "Popular", "BANK", 10_000L, "DOP"))
            accounts.insertAccount(AccountEntity("otra", "BHD", "BANK", 5_000L, "DOP"))
            transactions.insertTransaction(tx("g1", "cuenta", 1_000L))
            transactions.insertTransaction(tx("g2", "cuenta", 2_000L))
            transactions.insertTransaction(tx("ajena", "otra", 3_000L))

            val dragged = accounts.softDeleteWithTransactionsAndOps(
                ownerId = "guest",
                id = "cuenta",
                accountOp = ::accountOp,
                transactionOp = ::transactionOp
            )

            assertEquals(setOf("g1", "g2"), dragged.map { it.id }.toSet())
            // Ni la cuenta ni sus movimientos siguen a la vista.
            assertEquals(listOf("otra"), accounts.getAllAccountsOnce("guest").map { it.id })
            assertEquals(listOf("ajena"), transactions.getAllTransactions("guest").first().map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun theMovementsOfOtherAccountsAreLeftAlone() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            accounts.insertAccount(AccountEntity("cuenta", "Popular", "BANK", 10_000L, "DOP"))
            accounts.insertAccount(AccountEntity("otra", "BHD", "BANK", 5_000L, "DOP"))
            transactions.insertTransaction(tx("ajena", "otra", 3_000L))

            accounts.softDeleteWithTransactionsAndOps("guest", "cuenta", ::accountOp, ::transactionOp)

            // El saldo de la otra cuenta tampoco se mueve: un movimiento solo afecta al
            // saldo de su propia cuenta, asi que borrar una no descuadra a las demas.
            assertEquals(5_000L, accounts.getAccountById("guest", "otra")!!.balance)
            assertEquals("ajena", transactions.getTransactionById("guest", "ajena")!!.id)
        } finally {
            database.close()
        }
    }

    @Test
    fun everyDeletionIsQueuedSoTheServerHearsAboutIt() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            val transactions = database.transactionDao()
            val pending = database.pendingOperationDao()
            accounts.insertAccount(AccountEntity("cuenta", "Popular", "BANK", 10_000L, "DOP"))
            transactions.insertTransaction(tx("g1", "cuenta", 1_000L))
            transactions.insertTransaction(tx("g2", "cuenta", 2_000L))

            accounts.softDeleteWithTransactionsAndOps("guest", "cuenta", ::accountOp, ::transactionOp)

            // Sin lapida encolada el borrado se queda en este telefono y todo vuelve del
            // servidor en la siguiente sincronizacion, ya sin cuenta a la que pertenecer.
            val queued = pending.getAll("guest").map { it.id }
            assertTrue("falta la cuenta: $queued", "ACCOUNT:cuenta" in queued)
            assertTrue("falta g1: $queued", "TRANSACTION:g1" in queued)
            assertTrue("falta g2: $queued", "TRANSACTION:g2" in queued)
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingAnAccountWithNoMovementsDragsNothing() = runBlocking {
        val database = newDb()
        try {
            val accounts = database.accountDao()
            accounts.insertAccount(AccountEntity("cuenta", "Popular", "BANK", 10_000L, "DOP"))

            val dragged = accounts.softDeleteWithTransactionsAndOps(
                "guest", "cuenta", ::accountOp, ::transactionOp
            )

            assertEquals(emptyList<TransactionEntity>(), dragged)
            assertEquals(emptyList<AccountEntity>(), accounts.getAllAccountsOnce("guest"))
        } finally {
            database.close()
        }
    }

    private fun tx(id: String, accountId: String, amount: Long) = TransactionEntity(
        id = id,
        accountId = accountId,
        amount = amount,
        type = "EXPENSE",
        categoryId = "",
        date = 1L,
        note = id
    )

    private fun accountOp(account: AccountEntity) = PendingOperationEntity(
        id = "ACCOUNT:${account.id}",
        entityType = "ACCOUNT",
        entityId = account.id,
        payload = "",
        createdAt = 1L,
        ownerId = account.ownerId
    )

    private fun transactionOp(transaction: TransactionEntity) = PendingOperationEntity(
        id = "TRANSACTION:${transaction.id}",
        entityType = "TRANSACTION",
        entityId = transaction.id,
        payload = "",
        createdAt = 1L,
        ownerId = transaction.ownerId
    )
}
