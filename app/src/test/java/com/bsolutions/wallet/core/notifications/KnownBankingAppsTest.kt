package com.bsolutions.wallet.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownBankingAppsTest {
    @Test
    fun `el catalogo contiene las siete apps transaccionales confirmadas`() {
        assertEquals(
            linkedSetOf(
                "com.banreservas.tubancoappmobile",
                "com.qik.android.app",
                "com.popular.app.android",
                "com.artech.infocorp_bhd.bhd",
                "com.sii.bdi",
                "com.popular.joven",
                "com.popular.pinkapp"
            ),
            KnownBankingApps.supported.mapTo(linkedSetOf(), KnownBankingApp::packageName)
        )
    }

    @Test
    fun `el catalogo excluye la app de autenticacion BHD`() {
        assertFalse(
            KnownBankingApps.supported.any { it.packageName == "com.bhdleon.tdcdigital" }
        )
    }

    @Test
    fun `el detector devuelve solo paquetes conocidos instalados`() {
        val installed = setOf("com.qik.android.app", "com.popular.pinkapp", "app.desconocida")

        val detected = InstalledBankingAppsDetector(installed::contains).detect()

        assertEquals(
            listOf("com.qik.android.app", "com.popular.pinkapp"),
            detected.map(KnownBankingApp::packageName)
        )
        assertTrue(detected.all { it.packageName in installed })
    }
}
