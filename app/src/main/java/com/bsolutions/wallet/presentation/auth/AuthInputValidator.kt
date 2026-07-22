package com.bsolutions.wallet.presentation.auth

object AuthInputValidator {
    fun registrationError(name: String, email: String, password: String): String? = when {
        name.isBlank() -> "Escribe tu nombre."
        email.isBlank() -> "Escribe un correo válido."
        password.length < 10 -> "La contraseña debe tener al menos 10 caracteres."
        else -> null
    }
}
