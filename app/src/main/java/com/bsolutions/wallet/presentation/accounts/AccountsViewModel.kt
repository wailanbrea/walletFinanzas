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
        displayedBalance: Long,
        countryCode: String = "DO",
        institutionName: String? = null,
        cardLastFour: String? = null,
        creditLimit: Long? = null
    ) {
        if (name.isBlank() || (type == "CREDIT_CARD" && (creditLimit ?: 0L) <= 0L)) return
        viewModelScope.launch {
            accountRepository.addAccount(
                Account(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    type = type,
                    balance = storedBalance(type, displayedBalance),
                    currency = "DOP",
                    countryCode = countryCode,
                    institutionName = institutionName,
                    cardLastFour = cardLastFour,
                    creditLimit = creditLimit.takeIf { type == "CREDIT_CARD" }
                )
            )
        }
    }

    fun updateAccount(
        account: Account,
        newName: String,
        newType: String,
        displayedBalance: Long,
        creditLimit: Long?
    ) {
        if (newName.isBlank() || (newType == "CREDIT_CARD" && (creditLimit ?: 0L) <= 0L)) return
        viewModelScope.launch {
            accountRepository.updateAccount(
                account.copy(
                    name = newName.trim(),
                    type = newType,
                    balance = storedBalance(newType, displayedBalance),
                    creditLimit = creditLimit.takeIf { newType == "CREDIT_CARD" }
                )
            )
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
            if (selectedAccountId.value == accountId) selectedAccountId.value = null
        }
    }
}

internal fun storedBalance(type: String, displayedBalance: Long): Long =
    if (type == "CREDIT_CARD") -displayedBalance.coerceAtLeast(0L) else displayedBalance

internal fun creditCardDebt(balance: Long): Long = when {
    balance == Long.MIN_VALUE -> Long.MAX_VALUE
    balance < 0L -> -balance
    else -> 0L
}

internal fun availableCredit(balance: Long, creditLimit: Long?): Long {
    val limit = (creditLimit ?: 0L).coerceAtLeast(0L)
    if (balance > Long.MAX_VALUE - limit) return Long.MAX_VALUE
    return (limit + balance).coerceAtLeast(0L)
}

/**
 * Cuanto se llena la barra de una cuenta, de 0 a 1.
 *
 * Las barras de esa tarjeta miden dos cosas distintas y por eso se llenan al reves. En una
 * cuenta de banco la barra es el dinero que hay: crece cuanto mas tengas. En una tarjeta es
 * el credito que te queda: esta llena cuando no debes nada y se vacia segun consumes el
 * limite. Asi una barra llena siempre significa lo mismo —vas bien— en las dos.
 *
 * [largestBalance] es el mayor saldo entre las cuentas que no son tarjeta, porque una
 * cuenta se compara con las otras cuentas y no con el limite de una tarjeta.
 * [largestCardDebt] solo se usa para las tarjetas a las que no se les ha registrado
 * limite: sin limite no hay forma de saber cuanto margen queda, asi que se comparan entre
 * ellas. La mas endeudada queda vacia y la que no debe nada, llena.
 */
internal fun balanceBarFraction(
    type: String,
    balance: Long,
    creditLimit: Long?,
    largestBalance: Long,
    largestCardDebt: Long
): Float {
    if (type == "CREDIT_CARD") {
        val limit = (creditLimit ?: 0L).coerceAtLeast(0L)
        if (limit > 0L) {
            return (availableCredit(balance, limit).toFloat() / limit).coerceIn(0f, 1f)
        }
        val debt = creditCardDebt(balance)
        if (debt <= 0L || largestCardDebt <= 0L) return 1f
        return (1f - debt.toFloat() / largestCardDebt).coerceIn(0f, 1f)
    }

    val available = balance.coerceAtLeast(0L)
    if (available <= 0L) return 0f
    val share = available.toFloat() / largestBalance.coerceAtLeast(1L)
    // Un saldo pequeno al lado de uno grande da una fraccion invisible. Con un minimo
    // visible la comparacion sigue siendo justa y deja de parecer que la cuenta esta
    // vacia cuando no lo esta.
    return share.coerceIn(0.06f, 1f)
}
