package com.bsolutions.wallet.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.sync.SyncScheduler
import com.bsolutions.wallet.data.preferences.DEFAULT_USER_NAME
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.data.repository.AuthResult
import com.bsolutions.wallet.data.repository.WalletAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** true cuando login/registro terminó bien (o se envió el correo de recuperación). */
    val success: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userEmail: String = ""
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: WalletAuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoggedIn = authRepository.currentUser != null,
            userEmail = authRepository.currentUser?.email.orEmpty()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = user != null,
                    userEmail = user?.email.orEmpty()
                )
            }
        }
    }

    val rememberSession: Flow<Boolean> = userPreferencesRepository.rememberSession
    val rememberedEmail: Flow<String> = userPreferencesRepository.rememberedEmail

    fun login(email: String, password: String, rememberMe: Boolean = true) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Completa correo y contraseña.")
            return
        }
        viewModelScope.launch {
            userPreferencesRepository.setRememberSession(rememberMe)
            if (rememberMe) {
                userPreferencesRepository.setRememberedEmail(email.trim().lowercase())
            } else {
                userPreferencesRepository.setRememberedEmail("")
            }
        }
        runAuth { authRepository.signIn(email, password, rememberMe) }
    }

    fun register(name: String, email: String, password: String) {
        AuthInputValidator.registrationError(name, email, password)?.let { error ->
            _uiState.value = _uiState.value.copy(error = error)
            return
        }
        runAuth { authRepository.signUp(name, email, password) }
    }

    fun recoverPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Escribe tu correo.")
            return
        }
        runAuth { authRepository.sendPasswordReset(email) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AuthUiState()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun consumeSuccess() {
        _uiState.value = _uiState.value.copy(success = false)
    }

    private fun runAuth(block: suspend () -> AuthResult) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = false)
            when (val result = block()) {
                is AuthResult.Success -> {
                    // El perfil del backend manda sobre los valores por defecto locales: al
                    // entrar en un teléfono nuevo el menú debe decir el nombre y el correo
                    // reales, no "Mi Perfil". Un nombre que el usuario ya personalizó aquí
                    // se respeta.
                    if (result.user.email.isNotBlank()) {
                        val profile = userPreferencesRepository.profile.first()
                        val keepsDefaultName = profile.userName.isBlank() ||
                            profile.userName == DEFAULT_USER_NAME
                        val resolvedName = result.user.name
                            .takeIf { it.isNotBlank() && keepsDefaultName }
                            ?: profile.userName
                        if (profile.email != result.user.email || resolvedName != profile.userName) {
                            userPreferencesRepository.saveProfile(
                                userName = resolvedName,
                                email = result.user.email,
                                walletName = profile.walletName,
                                financialCountryCode = profile.financialCountryCode
                            )
                        }
                    }
                    // Con sesión nueva, sube de inmediato lo pendiente y baja lo del servidor.
                    if (authRepository.currentUser != null) {
                        syncScheduler.requestSyncNow()
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        success = true,
                        isLoggedIn = authRepository.currentUser != null,
                        userEmail = result.user.email
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
            }
        }
    }
}
