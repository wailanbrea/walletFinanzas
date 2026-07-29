package com.bsolutions.wallet.presentation.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditCardAmountsTest {
    @Test
    fun `deuda positiva se almacena como balance negativo solo en credito`() {
        assertEquals(-25_000L, storedBalance("CREDIT_CARD", 25_000L))
        assertEquals(25_000L, storedBalance("BANK", 25_000L))
    }

    @Test
    fun `resumen de credito nunca muestra deuda o disponible negativos`() {
        assertEquals(25_000L, creditCardDebt(-25_000L))
        assertEquals(0L, creditCardDebt(5_000L))
        assertEquals(75_000L, availableCredit(-25_000L, 100_000L))
        assertEquals(0L, availableCredit(-125_000L, 100_000L))
    }
}
