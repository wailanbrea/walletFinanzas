package com.bsolutions.wallet.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * El aviso real de Qik, copiado tal cual de la notificacion del telefono.
 *
 * Se guarda entero a proposito: la vista previa que se ve en la barra esta recortada
 * ("Tarjeta ...") y engaña sobre lo que el extractor tiene delante. El texto completo trae
 * el importe, el comercio y la tarjeta, y de ahi sale todo menos la categoria.
 */
class QikNoticeTest {

    private val title = "notificaciones@qik.do"
    private val body = " Usaste tu tarjeta de credito Qik \n" +
        "¡Hola WAILAN DANIEL BREA NUÑEZ! Tarjeta 53*************8324 " +
        "Se hizo una transaccion de RD$ 210.05 en BOXPAQ ENS ESPAILLAT " +
        "con tu tarjeta credito Qik que termina en 53*************8324 " +
        "Localidad BOXPAQ ENS ESPAILLAT"

    @Test
    fun `the Qik notice gives amount, merchant and card`() {
        val parsed = BankNoticeExtractor.parse(title, body)

        assertNotNull(parsed)
        assertEquals(21_005L, parsed?.amountMinor)
        assertEquals("DOP", parsed?.currency)
        assertEquals("BOXPAQ ENS ESPAILLAT", parsed?.merchant)
        assertEquals("8324", parsed?.last4Digits)
        assertEquals("expense", parsed?.direction)
    }

    @Test
    fun `the card number is never kept beyond its last four digits`() {
        val parsed = BankNoticeExtractor.parse(title, body)

        // El aviso trae 53*************8324. Guardar mas que los cuatro ultimos seria
        // guardar un numero de tarjeta, y eso no entra en la base de datos.
        assertEquals(4, parsed?.last4Digits?.length)
        assertEquals(false, parsed?.merchant?.contains("53"))
    }

    @Test
    fun `a courier charge is recognised as a purchase`() {
        val parsed = BankNoticeExtractor.parse(title, body)

        // Antes ninguna regla conocia BOXPAQ y el consumo llegaba sin categoria. Lo que se
        // paga en un courier es el envio de una compra por internet, asi que cae del mismo
        // lado que la compra. Una regla propia ("boxpaq -> Amazon") manda sobre esta.
        assertEquals("cat_compras", parsed?.suggestedCategoryId)
    }
}
