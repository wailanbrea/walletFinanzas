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
    val expensePayments: List<PlannedPayment> = emptyList(),
    val incomePayments: List<PlannedPayment> = emptyList(),
    val activeExpenseTotal: Long = 0L,
    val activeIncomeTotal: Long = 0L
)

internal fun buildPlannedPaymentsUiState(
    payments: List<PlannedPayment>,
    accounts: List<Account>,
    categories: List<Category>
): PlannedPaymentsUiState {
    // Los datos antiguos sin tipo reconocido se conservan como gasto: antes de admitir
    // ingresos, EXPENSE era el único valor y ocultarlos rompería compatibilidad.
    val expenses = payments.filter { it.type != "INCOME" }
    val incomes = payments.filter { it.type == "INCOME" }
    return PlannedPaymentsUiState(
        payments = payments,
        accounts = accounts,
        categories = categories,
        expensePayments = expenses,
        incomePayments = incomes,
        activeExpenseTotal = expenses.filter { it.isActive }.sumOf { it.amount },
        activeIncomeTotal = incomes.filter { it.isActive }.sumOf { it.amount }
    )
}

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
        buildPlannedPaymentsUiState(payments, accounts, categories)
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

    private fun nextDate(from: Long, frequency: String): Long = nextDueDate(from, frequency)
}

/**
 * Cuándo toca la próxima vez.
 *
 * Hay dos familias y confundirlas descuadra el calendario. Las de intervalo cuentan días
 * desde la última vez y se corren solas. Las de día fijo caen siempre en la misma fecha
 * del mes, que es como se cobra un sueldo o se paga un alquiler.
 *
 * "Quincenal" en República Dominicana es el 15 y el último día del mes, no cada catorce
 * días: con el intervalo, en pocos meses el sueldo terminaba cayendo cualquier día.
 */
internal fun nextDueDate(fromMillis: Long, frequency: String): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
    when (frequency) {
        "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
        // Intervalos: se cuentan días, sin mirar el calendario.
        "BIWEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 2)
        "EVERY_15_DAYS" -> cal.add(Calendar.DAY_OF_MONTH, 15)
        "EVERY_30_DAYS" -> cal.add(Calendar.DAY_OF_MONTH, 30)
        // Día fijo: el 15 y el último del mes, saltando al que toque después de hoy.
        "SEMIMONTHLY" -> {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            if (day < 15) {
                cal.set(Calendar.DAY_OF_MONTH, 15)
            } else {
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                if (day >= cal.get(Calendar.DAY_OF_MONTH)) {
                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, 15)
                }
            }
        }
        // Mismo día cada mes; si ese día no existe (31 en febrero) cae en el último.
        "MONTHLY" -> {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val wasLastDayOfMonth = day == cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, 1)
            val targetMonthLastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(
                Calendar.DAY_OF_MONTH,
                if (wasLastDayOfMonth) targetMonthLastDay else minOf(day, targetMonthLastDay)
            )
        }
        "YEARLY" -> cal.add(Calendar.YEAR, 1)
    }

    return cal.timeInMillis
}
