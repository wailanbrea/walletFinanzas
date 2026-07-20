package com.bsolutions.wallet.presentation.common

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Pide autenticación biométrica (huella / rostro) con respaldo a la credencial del
 * dispositivo (PIN/patrón). Invoca [onSuccess] solo si el usuario se autentica.
 *
 * Si el dispositivo no tiene ningún método de seguridad configurado, ejecuta
 * [onSuccess] directamente para no dejar la función inaccesible.
 */
fun Context.authenticateBiometric(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit
) {
    val activity = this as? FragmentActivity ?: run { onSuccess(); return }

    val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    val canAuth = BiometricManager.from(activity).canAuthenticate(allowed)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        // Sin biometría ni PIN configurados: no bloquear la acción
        onSuccess()
        return
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(allowed)
        .build()
    prompt.authenticate(info)
}
