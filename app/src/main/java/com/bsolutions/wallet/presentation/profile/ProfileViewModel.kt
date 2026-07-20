package com.bsolutions.wallet.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.data.preferences.UserProfilePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

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
        }
    }
}
