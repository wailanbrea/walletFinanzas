package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.database.LocalDataIsolation
import com.bsolutions.wallet.core.database.NoOpLocalDataIsolation
import com.bsolutions.wallet.core.network.AuthPayload
import com.bsolutions.wallet.core.network.ForgotPasswordRequest
import com.bsolutions.wallet.core.network.LoginRequest
import com.bsolutions.wallet.core.network.RegisterRequest
import com.bsolutions.wallet.core.network.WalletApi
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException
import java.io.IOException

interface WalletSessionStore {
    val token: String?
    val user: AuthUser?
    val rememberSession: Boolean
    val userFlow: StateFlow<AuthUser?>

    fun save(token: String, user: AuthUser, rememberSession: Boolean = true)
    fun clear()
}

class WalletAuthRepository(
    private val api: WalletApi,
    private val session: WalletSessionStore,
    private val deviceName: String,
    private val localDataIsolation: LocalDataIsolation = NoOpLocalDataIsolation
) {
    val currentUserFlow: StateFlow<AuthUser?> = session.userFlow

    val currentUser: AuthUser?
        get() = session.user

    suspend fun signIn(email: String, password: String, rememberMe: Boolean = true): AuthResult = runAuth(rememberMe) {
        api.login(
            LoginRequest(
                email = email.trim().lowercase(),
                password = password,
                deviceName = deviceName
            )
        ).data
    }

    suspend fun signUp(name: String, email: String, password: String): AuthResult = runAuth(rememberMe = true) {
        api.register(
            RegisterRequest(
                name = name.trim(),
                email = email.trim().lowercase(),
                password = password,
                passwordConfirmation = password,
                deviceName = deviceName
            )
        ).data
    }

    suspend fun signOut() {
        runCatching { api.logout() }
        localDataIsolation.activateGuest()
        session.clear()
    }

    suspend fun sendPasswordReset(email: String): AuthResult = try {
        val normalizedEmail = email.trim().lowercase()
        api.forgotPassword(ForgotPasswordRequest(normalizedEmail))
        AuthResult.Success(AuthUser(uid = "", email = normalizedEmail))
    } catch (e: Exception) {
        AuthResult.Error(mapAuthError(e))
    }

    private suspend fun runAuth(rememberMe: Boolean = true, request: suspend () -> AuthPayload): AuthResult = try {
        val payload = request()
        val user = AuthUser(
            uid = payload.user.id.toString(),
            email = payload.user.email,
            name = payload.user.name
        )
        session.save(payload.token, user, rememberMe)
        try {
            localDataIsolation.activateUser(user.uid)
        } catch (e: Exception) {
            session.clear()
            throw e
        }
        AuthResult.Success(user)
    } catch (e: Exception) {
        AuthResult.Error(mapAuthError(e))
    }

    /** Traduce el fallo a un mensaje accionable (antes todo colapsaba a "sin conexión"). */
    private fun mapAuthError(e: Throwable): String = when (e) {
        is HttpException -> {
            val parsed = runCatching {
                e.response()?.errorBody()?.string()?.let { Gson().fromJson(it, LaravelError::class.java) }
            }.getOrNull()
            val emailTaken = parsed?.errors?.get("email")
                ?.any { it.contains("taken", true) || it.contains("registrad", true) || it.contains("already", true) } == true
            when (e.code()) {
                401 -> "Correo o contraseña incorrectos."
                422 -> if (emailTaken) "Este correo ya está registrado." else "Correo o contraseña incorrectos."
                429 -> "Demasiados intentos. Espera un momento e inténtalo de nuevo."
                in 500..599 -> "El servidor tuvo un problema. Inténtalo más tarde."
                else -> parsed?.message ?: "No se pudo completar la solicitud."
            }
        }
        is IOException -> "No se pudo conectar con el servidor."
        else -> "No se pudo conectar con el servidor."
    }

    /** Forma del error de validación de Laravel: { message, errors: { campo: [msgs] } }. */
    private data class LaravelError(
        @SerializedName("message") val message: String? = null,
        @SerializedName("errors") val errors: Map<String, List<String>>? = null
    )
}
