package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.network.AccountDto
import com.bsolutions.wallet.data.local.entity.AccountEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El pull creaba una cuenta nueva por cada id remoto desconocido. Cuando la misma
 * cuenta real se habia creado en dos telefonos, aparecia duplicada y el Balance Total
 * la contaba dos veces.
 */
class AccountReconciliationTest {

    @Test
    fun `matches the same card by last four digits within the institution`() {
        val local = account(id = "local-1", name = "Visa Gold", institution = "Banco Popular Dominicano", cardLastFour = "4266")
        val remote = remote(id = "remote-1", name = "POPULAR VISA ISI", institution = "Banco Popular Dominicano", cardLastFour = "4266")

        assertTrue(local.matchesSameRealAccount(remote))
    }

    @Test
    fun `matches by name when neither side carries card digits`() {
        val local = account(id = "local-2", name = "Popular Corriente", institution = "Banco Popular Dominicano")
        val remote = remote(id = "remote-2", name = "  popular   corriente ", institution = "banco popular dominicano")

        assertTrue(local.matchesSameRealAccount(remote))
    }

    @Test
    fun `never matches across institutions or currencies`() {
        val local = account(id = "local-3", name = "Ahorros", institution = "Banreservas", cardLastFour = "1234")

        assertFalse(local.matchesSameRealAccount(remote(id = "r", name = "Ahorros", institution = "Banco BHD", cardLastFour = "1234")))
        assertFalse(
            local.matchesSameRealAccount(
                remote(id = "r", name = "Ahorros", institution = "Banreservas", cardLastFour = "1234", currency = "USD")
            )
        )
    }

    @Test
    fun `different cards in the same bank are not the same account`() {
        val local = account(id = "local-4", name = "Visa", institution = "Banreservas", cardLastFour = "4116")
        val remote = remote(id = "remote-4", name = "Visa", institution = "Banreservas", cardLastFour = "8324")

        // Mismo banco y mismo nombre, pero los digitos mandan: son dos tarjetas.
        assertFalse(local.matchesSameRealAccount(remote))
    }

    @Test
    fun `a shared generic name without institution is not enough evidence`() {
        val local = account(id = "local-5", name = "Efectivo", institution = null)
        val remote = remote(id = "remote-5", name = "Efectivo", institution = null)

        assertFalse(local.matchesSameRealAccount(remote))
    }

    @Test
    fun `a deleted local account never absorbs a remote one`() {
        val local = account(id = "local-6", name = "Vieja", institution = "Banreservas", cardLastFour = "4116")
            .copy(isDeleted = true)
        val remote = remote(id = "remote-6", name = "Vieja", institution = "Banreservas", cardLastFour = "4116")

        assertFalse(local.matchesSameRealAccount(remote))
    }

    private fun account(
        id: String,
        name: String,
        institution: String?,
        cardLastFour: String? = null,
        currency: String = "DOP"
    ) = AccountEntity(
        id = id,
        name = name,
        type = "BANK",
        balance = 1000,
        currency = currency,
        countryCode = "DO",
        institutionName = institution,
        cardLastFour = cardLastFour,
        ownerId = "owner-1"
    )

    private fun remote(
        id: String,
        name: String,
        institution: String?,
        cardLastFour: String? = null,
        currency: String = "DOP"
    ) = AccountDto(
        id = id,
        name = name,
        balance = 1000,
        currency = currency,
        institutionName = institution,
        countryCode = "DO",
        cardLastFour = cardLastFour,
        isActive = true
    )
}
