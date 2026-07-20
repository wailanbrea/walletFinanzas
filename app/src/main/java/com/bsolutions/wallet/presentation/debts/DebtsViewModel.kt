package com.bsolutions.wallet.presentation.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DebtsUiState(
    val iOweDebts: List<Debt> = emptyList(),
    val owedToMeDebts: List<Debt> = emptyList(),
    val totalIOwe: Long = 0L,
    val totalOwedToMe: Long = 0L
)

@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val debtRepository: DebtRepository
) : ViewModel() {

    val uiState: StateFlow<DebtsUiState> = debtRepository.getDebts()
        .map { debts ->
            val iOwe = debts.filter { it.direction == "I_OWE" }
            val owedToMe = debts.filter { it.direction == "OWED_TO_ME" }
            DebtsUiState(
                iOweDebts = iOwe,
                owedToMeDebts = owedToMe,
                totalIOwe = iOwe.filter { !it.isClosed }.sumOf { it.remainingAmount },
                totalOwedToMe = owedToMe.filter { !it.isClosed }.sumOf { it.remainingAmount }
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
    fun recordPayment(debt: Debt, amount: Long) {
        if (amount <= 0L) return
        viewModelScope.launch {
            val newPaid = (debt.paidAmount + amount).coerceAtMost(debt.totalAmount)
            debtRepository.updateDebt(
                debt.copy(
                    paidAmount = newPaid,
                    isClosed = newPaid >= debt.totalAmount
                )
            )
        }
    }

    fun deleteDebt(id: String) {
        viewModelScope.launch { debtRepository.deleteDebt(id) }
    }
}
