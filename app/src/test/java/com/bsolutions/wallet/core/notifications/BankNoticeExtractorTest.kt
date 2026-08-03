package com.bsolutions.wallet.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BankNoticeExtractorTest {

    @Test
    fun `parse Banreservas Dominican Republic purchase notice`() {
        val title = "Aviso de Consumo"
        val body = "Compra por RD$ 2,450.00 en SUPERMERCADO BRAVO con tu tarjeta **5678"

        val parsed = BankNoticeExtractor.parse(title, body)

        assertNotNull(parsed)
        assertEquals("SUPERMERCADO BRAVO", parsed?.merchant)
        assertEquals(245000L, parsed?.amountMinor)
        assertEquals("DOP", parsed?.currency)
        assertEquals("5678", parsed?.last4Digits)
        assertEquals("expense", parsed?.direction)
        assertEquals("CARD_PURCHASE_APPROVED", parsed?.eventType)
        assertEquals("cat_alimentacion", parsed?.suggestedCategoryId)
    }

    @Test
    fun `parse Banco Popular USD purchase notice`() {
        val title = "Consumo con Tarjeta"
        val body = "Consumo por US$ 14.99 en NETFLIX terminada en 1234"

        val parsed = BankNoticeExtractor.parse(title, body)

        assertNotNull(parsed)
        assertEquals("NETFLIX", parsed?.merchant)
        assertEquals(1499L, parsed?.amountMinor)
        assertEquals("USD", parsed?.currency)
        assertEquals("1234", parsed?.last4Digits)
        assertEquals("cat_entretenimiento", parsed?.suggestedCategoryId)
    }

    @Test
    fun `parse Uber transport purchase notice`() {
        val title = "Notificación de Pago"
        val body = "Se ha realizado un pago por RD$ 350.00 en UBER TRIP con la tarjeta **9988"

        val parsed = BankNoticeExtractor.parse(title, body)

        assertNotNull(parsed)
        assertEquals(35000L, parsed?.amountMinor)
        assertEquals("DOP", parsed?.currency)
        assertEquals("cat_transporte", parsed?.suggestedCategoryId)
    }

    @Test
    fun `ignores non-financial marketing notifications`() {
        val title = "Prestamo Preaprobado"
        val body = "Tienes un prestamo preaprobado por RD$ 50,000.00 en Banco Popular"

        val parsed = BankNoticeExtractor.parse(title, body)

        assertNull(parsed)
    }
}
