package com.bsolutions.wallet.presentation.transactions

import com.bsolutions.wallet.domain.model.Transaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El buscador de registros solo miraba la nota. Escribir el nombre de una categoria no
 * encontraba nada, que es justo lo que uno prueba primero cuando busca "transporte".
 */
class TransactionSearchTest {

    @Test
    fun `an empty search matches everything`() {
        assertTrue(matchesSearch(gasolina, "", "Transporte"))
        assertTrue(matchesSearch(gasolina, "   ", "Transporte"))
    }

    @Test
    fun `the note matches without minding capitals`() {
        assertTrue(matchesSearch(gasolina, "gasolina", "Transporte"))
        assertTrue(matchesSearch(gasolina, "GASOLINA", "Transporte"))
        assertTrue(matchesSearch(gasolina, "asoli", "Transporte"))
    }

    @Test
    fun `the category name matches even when the note never says it`() {
        assertTrue(matchesSearch(gasolina, "transporte", "Transporte"))
    }

    @Test
    fun `surrounding spaces do not break the search`() {
        assertTrue(matchesSearch(gasolina, "  gasolina  ", "Transporte"))
    }

    @Test
    fun `what does not match is left out`() {
        assertFalse(matchesSearch(gasolina, "amazon", "Transporte"))
    }

    @Test
    fun `a movement without a category is not a crash`() {
        assertTrue(matchesSearch(gasolina, "gasolina", null))
        assertFalse(matchesSearch(gasolina, "transporte", null))
    }

    private val gasolina = Transaction(
        id = "t1",
        accountId = "cuenta",
        amount = 363_420,
        type = "EXPENSE",
        categoryId = "cat_transporte",
        date = 1_000L,
        note = "Gasolina",
        currency = "DOP"
    )
}
