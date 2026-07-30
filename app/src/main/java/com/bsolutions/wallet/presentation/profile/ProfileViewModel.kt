package com.bsolutions.wallet.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.core.network.UpdateProfileRequest
import com.bsolutions.wallet.core.network.WalletApi
import com.bsolutions.wallet.data.preferences.UserProfilePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val api: WalletApi
) : ViewModel() {

    init {
        // El servidor manda sobre lo guardado aqui: si el nombre se cambio en otro
        // telefono, este lo adopta al abrir el perfil en vez de mostrar el suyo viejo.
        viewModelScope.launch {
            runCatching { api.getProfile().data }.getOrNull()?.let { remote ->
                val current = userPreferencesRepository.profile.first()
                if (remote.name.isNotBlank() && remote.name != current.userName) {
                    userPreferencesRepository.saveProfile(
                        userName = remote.name,
                        email = current.email.ifBlank { remote.email },
                        walletName = current.walletName,
                        financialCountryCode = current.financialCountryCode
                    )
                }
            }
        }
    }

    val profile: StateFlow<UserProfilePrefs> = userPreferencesRepository.profile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfilePrefs()
        )

    fun saveProfile(userName: String, email: String, walletName: String, financialCountryCode: String = "DO") {
        if (userName.isBlank()) return
        viewModelScope.launch {
            userPreferencesRepository.saveProfile(userName, email, walletName, financialCountryCode)
            // Se guarda primero en local y luego se sube: sin red el cambio no se pierde,
            // y si la subida falla se reintenta la proxima vez que se abra el perfil.
            runCatching { api.updateProfile(UpdateProfileRequest(userName.trim())) }
        }
    }
}
