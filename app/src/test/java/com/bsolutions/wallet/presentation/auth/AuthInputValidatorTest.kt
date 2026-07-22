package com.bsolutions.wallet.presentation.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInputValidatorTest {

    @Test
    fun `registration requires a name`() {
        assertEquals(
            "Escribe tu nombre.",
            AuthInputValidator.registrationError("", "ada@example.com", "Password123!")
        )
    }

    @Test
    fun `registration requires ten character password`() {
        assertEquals(
            "La contraseña debe tener al menos 10 caracteres.",
            AuthInputValidator.registrationError("Ada", "ada@example.com", "short")
        )
    }

    @Test
    fun `valid backend registration has no validation error`() {
        assertNull(AuthInputValidator.registrationError("Ada", "ada@example.com", "Password123!"))
    }
}
