package com.bsolutions.wallet.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class AuthUser(
    val uid: String,
    val email: String
)

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Autenticación con Firebase Auth (email/contraseña, plan Spark).
 * La sesión es persistente: FirebaseAuth guarda el usuario entre arranques.
 * La app sigue siendo usable sin cuenta (offline-first); la cuenta se usará
 * para la sincronización en la nube.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor() {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUser: AuthUser?
        get() = auth.currentUser?.let { AuthUser(it.uid, it.email.orEmpty()) }

    suspend fun signIn(email: String, password: String): AuthResult = try {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user
        if (user != null) AuthResult.Success(AuthUser(user.uid, user.email.orEmpty()))
        else AuthResult.Error("No se pudo iniciar sesión.")
    } catch (e: Exception) {
        AuthResult.Error(mapError(e))
    }

    suspend fun signUp(email: String, password: String): AuthResult = try {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user
        if (user != null) AuthResult.Success(AuthUser(user.uid, user.email.orEmpty()))
        else AuthResult.Error("No se pudo crear la cuenta.")
    } catch (e: Exception) {
        AuthResult.Error(mapError(e))
    }

    suspend fun sendPasswordReset(email: String): AuthResult = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        AuthResult.Success(AuthUser("", email.trim()))
    } catch (e: Exception) {
        AuthResult.Error(mapError(e))
    }

    fun signOut() {
        auth.signOut()
    }

    private fun mapError(e: Exception): String = when (e) {
        is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo."
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
        is FirebaseAuthWeakPasswordException -> "La contraseña debe tener al menos 6 caracteres."
        is FirebaseNetworkException -> "Sin conexión. Verifica tu internet e inténtalo de nuevo."
        else -> "Error de autenticación: ${e.message ?: "desconocido"}"
    }
}
