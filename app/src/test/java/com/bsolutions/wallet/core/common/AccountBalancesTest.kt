package com.bsolutions.wallet.core.common

import com.bsolutions.wallet.domain.model.Account
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El limite de credito no es dinero del usuario: es cuanto le presta el banco. Sumarlo
 * al Balance Total le diria que tiene mas de lo que tiene, que es el peor error posible
 * en una app de finanzas personales.
 */
class AccountBalancesTest {

    @Test
    fun `the credit limit never counts as money in the total`() {
        val cuenta = account(id = "a1", balance = 50_000)
        val tarjetaSinDeuda = creditCard(id = "a2", debt = 0, limit = 6_600_000)

        // La tarjeta aporta 0: su limite de 66,000 no es saldo.
        assertEquals(50_000L, AccountBalances.primaryTotal(listOf(cuenta, tarjetaSinDeuda)))
    }

    @Test
    fun `a credit card with debt subtracts from the total`() {
        val cuenta = account(id = "a1", balance = 50_000)
        val tarjeta = creditCard(id = "a2", debt = 20_000, limit = 6_600_000)

        assertEquals(30_000L, AccountBalances.primaryTotal(listOf(cuenta, tarjeta)))
    }

    @Test
    fun `raising the credit limit does not change the total`() {
        val cuenta = account(id = "a1", balance = 50_000)
        val antes = creditCard(id = "a2", debt = 20_000, limit = 1_000_000)
        val despues = antes.copy(creditLimit = 9_000_000)

        assertEquals(
            AccountBalances.primaryTotal(listOf(cuenta, antes)),
            AccountBalances.primaryTotal(listOf(cuenta, despues))
        )
    }

    @Test
    fun `foreign currency accounts stay out of the base total`() {
        val pesos = account(id = "a1", balance = 50_000)
        val dolares = account(id = "a2", balance = 30_000, currency = "USD")

        assertEquals(50_000L, AccountBalances.primaryTotal(listOf(pesos, dolares)))
        assertEquals(mapOf("USD" to 30_000L), AccountBalances.foreignTotals(listOf(pesos, dolares)))
    }

    private fun account(
        id: String,
        balance: Long,
        currency: String = MoneyFormat.DEFAULT_CURRENCY
    ) = Account(
        id = id,
        name = "Cuenta $id",
        type = "BANK",
        balance = balance,
        currency = currency
    )

    /** Una tarjeta guarda la deuda como saldo negativo; el limite va aparte. */
    private fun creditCard(id: String, debt: Long, limit: Long) = Account(
        id = id,
        name = "Tarjeta $id",
        type = "CREDIT_CARD",
        balance = -debt,
        currency = MoneyFormat.DEFAULT_CURRENCY,
        creditLimit = limit
    )
}
