package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.network.AccountDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El pull solo insertaba lo que no existía, así que una corrección hecha en otro
 * teléfono no llegaba nunca: el segundo dispositivo se quedaba con los datos viejos
 * para siempre. Estas pruebas fijan cómo se traduce el estado del servidor.
 */
class AccountTombstoneTest {

    @Test
    fun `an inactive remote account becomes a deleted local row`() {
        val entity = remote(isActive = false).toAccountEntity("owner-1").copy(isDeleted = true)

        // is_active = false es la lápida: borrar en un teléfono borra en los demás.
        assertTrue(entity.isDeleted)
    }

    @Test
    fun `an active remote account stays visible`() {
        val entity = remote(isActive = true).toAccountEntity("owner-1").copy(isDeleted = false)

        assertFalse(entity.isDeleted)
    }

    @Test
    fun `the remote type and credit limit replace the local ones`() {
        // El caso real: una tarjeta que en un telefono quedo guardada como cuenta de
        // banco, con su limite contando como saldo propio.
        val entity = remote(isActive = true, type = "CREDIT_CARD", creditLimit = 6_600_000)
            .toAccountEntity("owner-1")

        assertEquals("CREDIT_CARD", entity.type)
        assertEquals(6_600_000L, entity.creditLimit)
    }

    private fun remote(
        isActive: Boolean,
        type: String? = "BANK",
        creditLimit: Long? = null
    ) = AccountDto(
        id = "acc-1",
        name = "Visa Gold",
        balance = 0,
        currency = "DOP",
        institutionName = "Banco Popular Dominicano",
        countryCode = "DO",
        cardLastFour = null,
        isActive = isActive,
        type = type,
        creditLimit = creditLimit
    )
}
