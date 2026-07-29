package com.bsolutions.wallet.presentation.plannedpayments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.CategoryRuleRepository
import com.bsolutions.wallet.core.common.ExpenseCategorizer
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.PlannedPayment
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.PlannedPaymentRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class PlannedPaymentsUiState(
    val payments: List<PlannedPayment> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val monthlyTotal: Long = 0L
)

@HiltViewModel
class PlannedPaymentsViewModel @Inject constructor(
    private val plannedPaymentRepository: PlannedPaymentRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryRules: CategoryRuleRepository
) : ViewModel() {

    val uiState: StateFlow<PlannedPaymentsUiState> = combine(
        plannedPaymentRepository.getPlannedPayments(),
        accountRepository.getAccounts(),
        categoryRepository.getCategories()
    ) { payments, accounts, categories ->
        PlannedPaymentsUiState(
            payments = payments,
            accounts = accounts,
            categories = categories,
            monthlyTotal = payments
                .filter { it.isActive && it.type == "EXPENSE" && it.frequency == "MONTHLY" }
                .sumOf { it.amount }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlannedPaymentsUiState()
    )

    fun addPayment(
        name: String,
        accountId: String,
        categoryId: String,
        amount: Long,
        frequency: String,
        firstDueDate: Long,
        type: String = "EXPENSE"
    ) {
        if (name.isBlank() || accountId.isBlank() || amount <= 0L) return
        viewModelScope.launch {
            val finalCategoryId = resolveCategoryId(categoryId, name)
            plannedPaymentRepository.addPlannedPayment(
                PlannedPayment(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    accountId = accountId,
                    categoryId = finalCategoryId,
                    amount = amount,
                    type = type,
                    frequency = frequency,
                    nextDueDate = firstDueDate,
                    isActive = true
                )
            )
        }
    }

    /**
     * Registra la transacción del pago y avanza la fecha del próximo vencimiento.
     *
     * [actualAmount] permite corregir el importe de esa ocurrencia sin tocar el plan:
     * una quincena puede venir con horas extra o con un descuento, y lo que debe quedar
     * registrado es lo que realmente entró, no lo previsto.
     */
    fun payNow(payment: PlannedPayment, actualAmount: Long? = null) {
        val amount = (actualAmount ?: payment.amount).takeIf { it > 0L } ?: return
        viewModelScope.launch {
            // Antes no se ajustaba el saldo (descuadre). Ahora: saldo + movimiento
            // atómicos, con la divisa de la cuenta del pago.
            val account = accountRepository.getAccount(payment.accountId) ?: return@launch
            val finalCategoryId = resolveCategoryId(payment.categoryId, payment.name)
            transactionRepository.addTransactionWithBalance(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = payment.accountId,
                    amount = amount,
                    type = payment.type,
                    categoryId = finalCategoryId,
                    date = System.currentTimeMillis(),
                    note = payment.name,
                    currency = account.currency
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

    private suspend fun resolveCategoryId(selectedId: String, text: String): String {
        val categories = categoryRepository.getCategories().first()
        if (categories.any { it.id == selectedId }) return selectedId
        return ExpenseCategorizer.categoryIdFor(
            text = text,
            categories = categories,
            customRules = categoryRules.rules.first()
        ).orEmpty()
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
