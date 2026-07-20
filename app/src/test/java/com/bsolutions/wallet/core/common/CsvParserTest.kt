package com.bsolutions.wallet.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvParserTest {

    @Test
    fun `parses quoted semicolon separated records`() {
        val csv = CsvParser.parse("Fecha;Descripción;Monto\n16/07/2026;\"Compra; supermercado\";1.234,56")

        assertEquals(listOf("Fecha", "Descripción", "Monto"), csv.headers)
        assertEquals(listOf("16/07/2026", "Compra; supermercado", "1.234,56"), csv.rows.single())
    }

    @Test
    fun `delegates financial parsing to the shared money parser`() {
        assertEquals(-123456L, CsvParser.parseAmount("(RD$1,234.56)"))
        assertNull(CsvParser.parseAmount("sin importe"))
    }

    @Test
    fun `parses comma separated records`() {
        val csv = CsvParser.parse("Fecha,Descripción,Monto\n01/07/2026,Súper,-1500.50")
        assertEquals(listOf("Fecha", "Descripción", "Monto"), csv.headers)
        assertEquals(listOf("01/07/2026", "Súper", "-1500.50"), csv.rows.single())
    }

    @Test
    fun `unescapes doubled quotes inside quoted fields`() {
        val csv = CsvParser.parse("A,B\n\"Restaurante \"\"El Sol\"\"\",100")
        assertEquals("Restaurante \"El Sol\"", csv.rows.single()[0])
    }

    @Test
    fun `empty content yields empty structures`() {
        val csv = CsvParser.parse("")
        assertEquals(0, csv.headers.size)
        assertEquals(0, csv.rows.size)
    }

    @Test
    fun `blank lines are skipped`() {
        val csv = CsvParser.parse("A,B\n\n1,2\n\n")
        assertEquals(1, csv.rows.size)
    }

    @Test
    fun `parses supported bank date formats`() {
        val slash = CsvParser.parseDate("15/07/2026")
        val iso = CsvParser.parseDate("2026-07-15")
        val dash = CsvParser.parseDate("15-07-2026")
        assertEquals(true, slash != null && slash > 0)
        // Los tres formatos apuntan al mismo día
        assertEquals(slash, iso)
        assertEquals(slash, dash)
    }

    @Test
    fun `invalid dates return null`() {
        assertNull(CsvParser.parseDate("no es fecha"))
        assertNull(CsvParser.parseDate(""))
        assertNull(CsvParser.parseDate("32/13/2026"))
    }
}
