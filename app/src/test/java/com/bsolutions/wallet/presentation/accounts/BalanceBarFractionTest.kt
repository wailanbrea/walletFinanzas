package com.bsolutions.wallet.presentation.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Una barra llena tiene que significar lo mismo en las dos mitades de la tarjeta: vas
 * bien. En una cuenta eso es tener dinero; en una tarjeta, no deberlo.
 */
class BalanceBarFractionTest {

    @Test
    fun `a card with no debt is full`() {
        val fraction = cardBar(balance = 0L, limit = 100_000L)

        assertEquals(1f, fraction, 0.001f)
    }

    @Test
    fun `a card empties as the limit is used up`() {
        val quarterUsed = cardBar(balance = -25_000L, limit = 100_000L)
        val halfUsed = cardBar(balance = -50_000L, limit = 100_000L)

        assertEquals(0.75f, quarterUsed, 0.001f)
        assertEquals(0.50f, halfUsed, 0.001f)
    }

    @Test
    fun `a maxed out card is empty and never goes below that`() {
        assertEquals(0f, cardBar(balance = -100_000L, limit = 100_000L), 0.001f)
        // Pasarse del limite no puede dar una barra negativa.
        assertEquals(0f, cardBar(balance = -180_000L, limit = 100_000L), 0.001f)
    }

    @Test
    fun `cards without a registered limit are compared against each other`() {
        // Sin limite no hay forma de saber cuanto margen queda, asi que la mas endeudada
        // queda vacia y la que no debe nada, llena.
        assertEquals(1f, cardBar(balance = 0L, limit = null, largestCardDebt = 40_000L), 0.001f)
        assertEquals(0f, cardBar(balance = -40_000L, limit = null, largestCardDebt = 40_000L), 0.001f)
        assertEquals(0.5f, cardBar(balance = -20_000L, limit = null, largestCardDebt = 40_000L), 0.001f)
    }

    @Test
    fun `a bank account fills up the more it holds`() {
        val small = bankBar(balance = 20_000L, largest = 100_000L)
        val big = bankBar(balance = 100_000L, largest = 100_000L)

        assertTrue("la cuenta pequena debe quedar por debajo de la grande", small < big)
        assertEquals(1f, big, 0.001f)
    }

    @Test
    fun `an empty bank account shows nothing and a tiny one still shows something`() {
        assertEquals(0f, bankBar(balance = 0L, largest = 100_000L), 0.001f)
        // Un saldo diminuto al lado de uno enorme daria una fraccion invisible, y la
        // cuenta pareceria vacia sin estarlo.
        assertTrue(bankBar(balance = 100L, largest = 10_000_000L) >= 0.06f)
    }

    @Test
    fun `a bank account in the red is empty, not full`() {
        assertEquals(0f, bankBar(balance = -5_000L, largest = 100_000L), 0.001f)
    }

    @Test
    fun `a card at zero and an empty account do not look the same`() {
        // Es el caso que delataba el fallo viejo: las dos barras salian identicas aunque
        // una cuenta vacia y una tarjeta sin deuda son situaciones opuestas.
        val cardAtZero = cardBar(balance = 0L, limit = 100_000L)
        val emptyAccount = bankBar(balance = 0L, largest = 100_000L)

        assertEquals(1f, cardAtZero, 0.001f)
        assertEquals(0f, emptyAccount, 0.001f)
    }

    private fun cardBar(balance: Long, limit: Long?, largestCardDebt: Long = 0L) =
        balanceBarFraction(
            type = "CREDIT_CARD",
            balance = balance,
            creditLimit = limit,
            largestBalance = 999_999_999L,
            largestCardDebt = largestCardDebt
        )

    private fun bankBar(balance: Long, largest: Long) =
        balanceBarFraction(
            type = "BANK",
            balance = balance,
            creditLimit = null,
            largestBalance = largest,
            largestCardDebt = 999_999_999L
        )
}
