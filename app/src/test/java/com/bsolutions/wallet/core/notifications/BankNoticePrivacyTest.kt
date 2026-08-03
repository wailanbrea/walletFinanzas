package com.bsolutions.wallet.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankNoticePrivacyTest {
    @Test
    fun `descarta codigos de autenticacion en espanol e ingles`() {
        assertTrue(
            BankNoticePrivacy.isSensitiveAuthenticationNotice(
                "Código de seguridad",
                "Tu código es 482901. No lo compartas.",
                ""
            )
        )
        assertTrue(
            BankNoticePrivacy.isSensitiveAuthenticationNotice(
                "Verification code",
                "Use 918233 to access your account",
                ""
            )
        )
        assertTrue(
            BankNoticePrivacy.isSensitiveAuthenticationNotice(
                "Access request",
                "Your code is 918233",
                ""
            )
        )
    }

    @Test
    fun `no confunde una compra con referencia de tarjeta con un otp`() {
        assertFalse(
            BankNoticePrivacy.isSensitiveAuthenticationNotice(
                "Compra aprobada",
                "RD$ 1,250.00 con tarjeta terminada en 1234",
                "Supermercado Nacional"
            )
        )
    }

    @Test
    fun `identificador es estable y cambia cuando cambia el contenido`() {
        val original = capture(text = "Compra por RD$ 500.00")
        assertEquals(original.deterministicId, original.copy().deterministicId)
        assertNotEquals(original.deterministicId, capture(text = "Compra por RD$ 501.00").deterministicId)
    }

    @Test
    fun `exportacion oculta datos personales y conserva el monto util`() {
        val redacted = BankNoticePrivacy.redactForFixture(
            "Compra RD$ 1,250.00 en Tienda Central, tarjeta 12345678. " +
                "Contacto persona@example.com, +1 809-555-1212",
            merchant = "Tienda Central"
        )

        assertTrue(redacted.contains("RD$ 1,250.00"))
        assertFalse(redacted.contains("Tienda Central"))
        assertFalse(redacted.contains("persona@example.com"))
        assertFalse(redacted.contains("809-555-1212"))
        assertFalse(redacted.contains("12345678"))
    }

    private fun capture(text: String) = NotificationCaptureData(
        packageName = "com.bank.app",
        appLabel = "Banco",
        notificationKey = "notification-key",
        title = "Compra aprobada",
        text = text,
        bigText = "",
        postTime = 1_700_000_000_000
    )
}
