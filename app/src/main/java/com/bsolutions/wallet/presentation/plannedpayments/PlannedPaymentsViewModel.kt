package com.bsolutions.wallet.presentation.plannedpayments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.PlannedPayment
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.PlannedPaymentRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class PlannedPaymentsUiState(
    val payments: List<PlannedPayment> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val monthlyTotal: Long = 0L
)

@HiltViewModel
class PlannedPaymentsViewModel @Inject constructor(
    private val plannedPaymentRepository: PlannedPaymentRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val uiState: StateFlow<PlannedPaymentsUiState> = combine(
        plannedPaymentRepository.getPlannedPayments(),
        accountRepository.getAccounts()
    ) { payments, accounts ->
        PlannedPaymentsUiState(
            payments = payments,
            accounts = accounts,
            monthlyTotal = payments
                .filter { it.isActive && it.type == "EXPENSE" && it.frequency == "MONTHLY" }
                .sumOf { it.amount }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlannedPaymentsUiState()
    )

    fun addPayment(name: String, accountId: String, amount: Long, frequency: String, firstDueDate: Long) {
        if (name.isBlank() || accountId.isBlank() || amount <= 0L) return
        viewModelScope.launch {
            plannedPaymentRepository.addPlannedPayment(
                PlannedPayment(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    accountId = accountId,
                    categoryId = "",
                    amount = amount,
                    type = "EXPENSE",
                    frequency = frequency,
                    nextDueDate = firstDueDate,
                    isActive = true
                )
            )
        }
    }

    /** Registra la transacción del pago y avanza la fecha del próximo vencimiento. */
    fun payNow(payment: PlannedPayment) {
        viewModelScope.launch {
            transactionRepository.addTransaction(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = payment.accountId,
                    amount = payment.amount,
                    type = payment.type,
                    categoryId = payment.categoryId,
                    date = System.currentTimeMillis(),
                    note = payment.name
                )
            )
            if (payment.frequency == "ONCE") {
                plannedPaymentRepository.updatePlannedPayment(payment.copy(isActive = false))
            } else {
                plannedPaymentRepository.updatePlannedPayment(
                    payment.copy(nextDueDate = nextDate(payment.nextDueDate, payment.frequency))
                )
            }
        }
    }

    fun deletePayment(id: String) {
        viewModelScope.launch { plannedPaymentRepository.deletePlannedPayment(id) }
    }

    private fun nextDate(from: Long, frequency: String): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        when (frequency) {
            "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "BIWEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 2)
            "MONTHLY" -> cal.add(Calendar.MONTH, 1)
            "YEARLY" -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }
}
