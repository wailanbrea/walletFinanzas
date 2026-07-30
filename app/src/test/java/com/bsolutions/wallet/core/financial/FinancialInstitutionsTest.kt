package com.bsolutions.wallet.core.financial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialInstitutionsTest {
    @Test
    fun `catalogo dominicano incluye resumen oficial de junio 2025`() {
        val institutions = FinancialInstitutions.dominicanRepublic
        val multipleBanks = setOf(
            "Banreservas", "Banco Popular Dominicano", "Banco BHD", "Banco Santa Cruz",
            "Scotiabank República Dominicana", "Banco Promerica", "Banco Caribe", "Banesco",
            "Banco BDI", "Banco López de Haro", "Banco Vimenca", "Banco Ademi", "Citibank",
            "Banco Lafise", "Qik Banco Digital", "JMMB Bank"
        )

        assertEquals(28, institutions.size)
        assertEquals(multipleBanks, institutions.take(16).mapTo(mutableSetOf()) { it.name })
        assertTrue(institutions.any { it.name == "Banco Agrícola" })
        assertTrue(institutions.any { it.name == "BANDEX" })
        assertEquals(10, institutions.drop(18).size)
        assertTrue(institutions.any { it.name.contains("Maguana") })
    }

    @Test
    fun `logos vienen de la ficha oficial de la SB y no de terceros`() {
        val banreservas = FinancialInstitutions.findByName("Banreservas")!!
        val santaCruz = FinancialInstitutions.findByName("Banco Santa Cruz")!!

        assertEquals("https://sb.gob.do/media/dlghc233/banreservas.svg", banreservas.logoUrl)
        assertEquals("https://sb.gob.do/media/vkinapnx/santa-cruz.svg", santaCruz.logoUrl)

        // Ningún logo puede depender de Google/Clearbit: filtrarían qué bancos usa el usuario.
        FinancialInstitutions.dominicanRepublic.forEach { institution ->
            val url = institution.logoUrl ?: return@forEach
            assertTrue(
                "${institution.name} no apunta a la SB: $url",
                url.startsWith("https://sb.gob.do/media/")
            )
            assertFalse(url.contains("clearbit", ignoreCase = true))
            assertFalse(url.contains("google", ignoreCase = true))
        }

        assertEquals("do_apap", FinancialInstitutions.findByName("APAP")!!.id)
        assertNull(FinancialInstitutions.findByName("Entidad personalizada"))
    }

    @Test
    fun `toda entidad del catalogo tiene logo oficial publicado`() {
        val sinLogo = FinancialInstitutions.dominicanRepublic.filter { it.logoPath == null }

        // El monograma local queda solo como respaldo (red caída o entidad fuera del catálogo).
        assertTrue("Entidades sin activo oficial: ${sinLogo.map { it.name }}", sinLogo.isEmpty())
    }
}
