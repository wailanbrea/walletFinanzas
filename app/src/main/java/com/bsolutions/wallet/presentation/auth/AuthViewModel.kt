package com.bsolutions.wallet.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.data.repository.AuthResult
import com.bsolutions.wallet.data.repository.FirebaseAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val authRepository: FirebaseAuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoggedIn = authRepository.currentUser != null,
            userEmail = authRepository.currentUser?.email.orEmpty()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Completa correo y contraseña.")
            return
        }
        runAuth { authRepository.signIn(email, password) }
    }

    fun register(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Correo válido y contraseña de al menos 6 caracteres.")
            return
        }
        runAuth { authRepository.signUp(email, password) }
    }

    fun recoverPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Escribe tu correo.")
            return
        }
        runAuth { authRepository.sendPasswordReset(email) }
    }

    fun logout() {
        authRepository.signOut()
        _uiState.value = AuthUiState()
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
                    // Guardar el correo en el perfil local si aún no hay uno
                    if (result.user.email.isNotBlank()) {
                        val profile = userPreferencesRepository.profile.first()
                        if (profile.email.isBlank()) {
                            userPreferencesRepository.saveProfile(
                                userName = profile.userName,
                                email = result.user.email,
                                walletName = profile.walletName
                            )
                        }
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
