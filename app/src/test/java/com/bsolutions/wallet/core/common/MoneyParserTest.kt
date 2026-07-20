package com.bsolutions.wallet.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {

    @Test
    fun `parses Dominican and international formats without precision loss`() {
        assertEquals(123456L, MoneyParser.parseMinorUnits("RD$1,234.56"))
        assertEquals(123456L, MoneyParser.parseMinorUnits("1.234,56"))
        assertEquals(100000000001L, MoneyParser.parseMinorUnits("1,000,000,000.01"))
    }

    @Test
    fun `parses negative accounting values and rounds half up`() {
        assertEquals(-50000L, MoneyParser.parseMinorUnits("(500)"))
        assertEquals(-5050L, MoneyParser.parseMinorUnits("-50.50"))
        assertEquals(101L, MoneyParser.parseMinorUnits("1.005"))
    }

    @Test
    fun `rejects malformed and overflowing amounts`() {
        assertNull(MoneyParser.parseMinorUnits(""))
        assertNull(MoneyParser.parseMinorUnits("12-34"))
        assertNull(MoneyParser.parseMinorUnits("999999999999999999999999999999"))
    }

    @Test
    fun `comma heuristics distinguish thousands from decimals`() {
        // Grupo final de 3 dígitos tras coma única = separador de miles
        assertEquals(123400L, MoneyParser.parseMinorUnits("1,234"))
        // Grupo final de 2 dígitos = decimal europeo
        assertEquals(123456L, MoneyParser.parseMinorUnits("1234,56"))
        assertEquals(123L, MoneyParser.parseMinorUnits("1,23"))
    }

    @Test
    fun `multiple dots are treated as thousand separators`() {
        assertEquals(123456700L, MoneyParser.parseMinorUnits("1.234.567"))
    }
}
