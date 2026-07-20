package com.bsolutions.wallet.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.AccountBalances
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val totalBalance: Long = 0L,
    /** Subtotales en divisas distintas de RD$ (null si todas las cuentas son DOP). */
    val foreignBalancesSubtitle: String? = null,
    val selectedAccountId: String? = null,
    val selectedAccountTransactions: List<Transaction> = emptyList(),
    val financialCountryCode: String = "DO",
    /** Modo privacidad: si está activo, la UI ofusca los montos. */
    val balancesHidden: Boolean = false
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val selectedAccountId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.getAccounts(),
        transactionRepository.getTransactions(),
        selectedAccountId,
        userPreferencesRepository.profile
    ) { accounts, allTransactions, selectedId, profile ->
        // Total principal solo en RD$; otras divisas como subtotales aparte
        val total = AccountBalances.primaryTotal(accounts)
        val filteredTx = if (selectedId != null) {
            allTransactions.filter { it.accountId == selectedId }
        } else {
            emptyList()
        }

        AccountsUiState(
            accounts = accounts,
            totalBalance = total,
            foreignBalancesSubtitle = AccountBalances.foreignSubtitle(accounts),
            selectedAccountId = selectedId,
            selectedAccountTransactions = filteredTx,
            financialCountryCode = profile.financialCountryCode,
            balancesHidden = profile.balancesHidden
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountsUiState()
    )

    fun selectAccount(accountId: String?) {
        selectedAccountId.value = accountId
    }

    fun toggleBalancesHidden() {
        viewModelScope.launch {
            val current = userPreferencesRepository.profile.first().balancesHidden
            userPreferencesRepository.setBalancesHidden(!current)
        }
    }

    fun addAccount(
        name: String,
        type: String,
        balance: Long,
        countryCode: String = "DO",
        institutionName: String? = null,
        cardLastFour: String? = null
    ) {
        viewModelScope.launch {
            accountRepository.addAccount(
                Account(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    balance = balance,
                    currency = "DOP",
                    countryCode = countryCode,
                    institutionName = institutionName,
                    cardLastFour = cardLastFour
                )
            )
        }
    }

    fun updateAccount(account: Account, newName: String, newType: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            accountRepository.updateAccount(account.copy(name = newName.trim(), type = newType))
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
            if (selectedAccountId.value == accountId) selectedAccountId.value = null
        }
    }
}
