package com.bsolutions.wallet.data.repository

import androidx.paging.PagingSource
import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.data.local.dao.TransactionDao
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Prueba la paginación del detalle de cuenta y la lógica de claves de TransactionPagingSource:
 * valida que el orden (date DESC), límites, offsets y las claves prevKey/nextKey sigan la especificación.
 */
class TransactionPagingTest {

    private lateinit var dao: FakeTransactionDao
    private lateinit var owner: String
    private lateinit var accountA: String
    private lateinit var accountB: String

    @Before
    fun setUp() {
        dao = FakeTransactionDao()
        owner = WalletOwnerScope.ownerIdFor("owner-1")
        accountA = "acct-a"
        accountB = "acct-b"
    }

    private fun seed() {
        val base = 1_700_000_000_000L
        // 75 movimientos para la cuenta A del propietario owner-1
        for (i in 0 until 75) {
            dao.transactions += TransactionEntity(
                id = "tx-a-$i",
                accountId = accountA,
                amount = 100L + i,
                type = "EXPENSE",
                categoryId = "food",
                date = base + i * 60_000L,
                ownerId = owner
            )
        }
        // 10 movimientos para la cuenta B del mismo propietario
        for (i in 0 until 10) {
            dao.transactions += TransactionEntity(
                id = "tx-b-$i",
                accountId = accountB,
                amount = 200L + i,
                type = "EXPENSE",
                categoryId = "food",
                date = base + i * 60_000L,
                ownerId = owner
            )
        }
        // 5 movimientos de OTRO propietario en la misma cuenta A
        val otherOwner = WalletOwnerScope.ownerIdFor("owner-2")
        for (i in 0 until 5) {
            dao.transactions += TransactionEntity(
                id = "tx-other-$i",
                accountId = accountA,
                amount = 999L,
                type = "EXPENSE",
                categoryId = "food",
                date = base + i * 60_000L,
                ownerId = otherOwner
            )
        }
    }

    private fun source(accountId: String, ownerId: String = owner): TransactionPagingSource {
        return TransactionPagingSource(dao, ownerId, accountId)
    }

    private fun load(
        source: TransactionPagingSource,
        key: Int?,
        size: Int = TransactionPagingSource.PAGE_SIZE
    ): PagingSource.LoadResult.Page<Int, com.bsolutions.wallet.domain.model.Transaction> {
        var page: PagingSource.LoadResult.Page<Int, com.bsolutions.wallet.domain.model.Transaction>? = null
        runTest {
            val result = source.load(PagingSource.LoadParams.Refresh(key, size, false))
            require(result is PagingSource.LoadResult.Page<Int, com.bsolutions.wallet.domain.model.Transaction>) {
                "Se esperaba una pagina, llego $result"
            }
            page = result
        }
        return page!!
    }

    @Test
    fun `la primera pagina trae los 30 mas recientes en orden descendente`() {
        seed()
        val page = load(source(accountA), null)
        assertEquals(30, page.data.size)
        assertEquals("tx-a-74", page.data.first().id)
        assertEquals("tx-a-45", page.data.last().id)
        assertNull(page.prevKey)
        assertEquals(30, page.nextKey)
    }

    @Test
    fun `las paginas se encadenan sin huecos ni duplicados hasta el final`() {
        seed()
        val src = source(accountA)
        val page0 = load(src, null)
        val page1 = load(src, page0.nextKey)
        val page2 = load(src, page1.nextKey)
        val all = page0.data + page1.data + page2.data
        assertEquals(75, all.size)
        assertEquals(75, all.map { it.id }.toSet().size)
        val expected = (74 downTo 0).map { "tx-a-$it" }
        assertEquals(expected, all.map { it.id })
        assertEquals(30, page1.prevKey)
        assertEquals(60, page2.prevKey)
        assertEquals(75, page2.nextKey)
    }

    @Test
    fun `la ultima pagina corta y cierra la paginacion`() {
        seed()
        val src = source(accountA)
        val last = load(src, 60)
        assertEquals(15, last.data.size)
        assertEquals("tx-a-14", last.data.first().id)
        assertEquals("tx-a-0", last.data.last().id)
        val beyond = load(src, last.nextKey)
        assertTrue(beyond.data.isEmpty())
        assertNull(beyond.nextKey)
        assertNull(beyond.prevKey)
    }

    @Test
    fun `solo ve los movimientos de su cuenta y de su propietario`() {
        seed()
        val b = load(source(accountB), null)
        assertEquals(10, b.data.size)
        assertEquals("tx-b-9", b.data.first().id)
        assertNull(b.nextKey)
        // Otro propietario sin movimientos no ve nada
        val other = load(source(accountA, ownerId = WalletOwnerScope.ownerIdFor("owner-3")), null)
        assertTrue(other.data.isEmpty())
    }

    private class FakeTransactionDao : TransactionDao {
        val transactions = mutableListOf<TransactionEntity>()

        override suspend fun getTransactionsPaginated(
            ownerId: String,
            accountId: String,
            limit: Int,
            offset: Int
        ): List<TransactionEntity> {
            return transactions
                .filter { it.ownerId == ownerId && it.accountId == accountId && !it.isDeleted }
                .sortedByDescending { it.date }
                .drop(offset)
                .take(limit)
        }

        override fun getAllTransactions(ownerId: String): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun getAllTransactionsOnce(ownerId: String): List<TransactionEntity> = emptyList()
        override suspend fun findRecentPotentialDuplicates(
            ownerId: String,
            type: String,
            currency: String,
            fromInclusive: Long,
            toInclusive: Long
        ): List<TransactionEntity> = emptyList()

        override suspend fun findRecentByTypeAndDate(
            ownerId: String,
            type: String,
            fromInclusive: Long,
            toInclusive: Long
        ): List<TransactionEntity> = emptyList()

        override fun getTransactionsByAccount(ownerId: String, accountId: String): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun getTransactionById(ownerId: String, id: String): TransactionEntity? = null
        override suspend fun getTransactionByIdIncludingDeleted(ownerId: String, id: String): TransactionEntity? = null
        override suspend fun getTransactionsForDebt(ownerId: String, debtId: String): List<TransactionEntity> = emptyList()
        override suspend fun getAccountCurrency(ownerId: String, accountId: String): String? = "DOP"
        override suspend fun getAccountBalance(ownerId: String, accountId: String): Long? = 0L
        override suspend fun categoryExists(ownerId: String, categoryId: String): Boolean = true
        override suspend fun insertTransaction(transaction: TransactionEntity) {}
        override suspend fun debitAccount(ownerId: String, accountId: String, amount: Long): Int = 1
        override suspend fun subtractFromAccount(ownerId: String, accountId: String, amount: Long): Int = 1
        override suspend fun creditAccount(ownerId: String, accountId: String, amount: Long): Int = 1
        override suspend fun insertPendingOp(op: PendingOperationEntity) {}
        override suspend fun updateTransaction(transaction: TransactionEntity): Int = 1
        override suspend fun softDeleteTransaction(ownerId: String, id: String): Int = 1
    }
}
