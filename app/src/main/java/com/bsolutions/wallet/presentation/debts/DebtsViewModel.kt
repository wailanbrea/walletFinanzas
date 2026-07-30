package com.bsolutions.wallet.presentation.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.usecase.DebtLedger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DebtsUiState(
    val iOweDebts: List<Debt> = emptyList(),
    val owedToMeDebts: List<Debt> = emptyList(),
    val totalIOwe: Long = 0L,
    val totalOwedToMe: Long = 0L,
    /** Cuentas donde puede entrar (o de donde puede salir) el dinero de un abono. */
    val accounts: List<Account> = emptyList()
)

@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val debtRepository: DebtRepository,
    private val accountRepository: AccountRepository,
    private val debtLedger: DebtLedger
) : ViewModel() {

    val uiState: StateFlow<DebtsUiState> = combine(
        debtRepository.getDebts(),
        accountRepository.getAccounts()
    ) { debts, accounts ->
            val iOwe = debts.filter { it.direction == "I_OWE" }
            val owedToMe = debts.filter { it.direction == "OWED_TO_ME" }
            DebtsUiState(
                iOweDebts = iOwe,
                owedToMeDebts = owedToMe,
                totalIOwe = iOwe.filter { !it.isClosed }.sumOf { it.remainingAmount },
                totalOwedToMe = owedToMe.filter { !it.isClosed }.sumOf { it.remainingAmount },
                accounts = accounts
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DebtsUiState()
        )

    fun addDebt(name: String, description: String, direction: String, totalAmount: Long) {
        if (name.isBlank() || totalAmount <= 0L) return
        viewModelScope.launch {
            debtRepository.addDebt(
                Debt(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    description = description.trim(),
                    direction = direction,
                    totalAmount = totalAmount,
                    paidAmount = 0L,
                    dueDate = null,
                    isClosed = false
                )
            )
        }
    }

    /** Registra un abono; cierra la deuda al completarse. */
    /**
     * Registra un abono de [debt] que entra de verdad en [accountId].
     *
     * Antes esto solo subia un contador y el dinero cobrado no aparecia en ninguna
     * cuenta: el saldo se quedaba corto. Ahora el abono es un movimiento real y lo
     * cobrado se calcula de esos movimientos, asi que este lado y la pantalla de
     * movimientos no pueden contradecirse.
     */
    /**
     * Suma un cargo nuevo a [debt]: dinero que sale ahora por lo mismo que ya prestaste,
     * como el currier de lo que compraste. Sube lo que te deben en vez de abrir otra deuda.
     */
    fun addCharge(debt: Debt, amount: Long, accountId: String, note: String = "") {
        if (amount <= 0L || accountId.isBlank()) return
        viewModelScope.launch {
            val account = uiState.value.accounts.firstOrNull { it.id == accountId } ?: return@launch
            debtLedger.addCharge(
                debt = debt,
                amount = amount,
                accountId = account.id,
                currency = account.currency,
                dateMillis = System.currentTimeMillis(),
                note = note
            )
        }
    }

    fun recordPayment(debt: Debt, amount: Long, accountId: String) {
        if (amount <= 0L || accountId.isBlank()) return
        viewModelScope.launch {
            val account = uiState.value.accounts.firstOrNull { it.id == accountId } ?: return@launch
            debtLedger.recordPayment(
                debt = debt,
                amount = amount,
                accountId = account.id,
                currency = account.currency,
                dateMillis = System.currentTimeMillis()
            )
        }
    }

    fun deleteDebt(id: String) {
        viewModelScope.launch { debtRepository.deleteDebt(id) }
    }
}
