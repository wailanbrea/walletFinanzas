package com.bsolutions.wallet.domain.usecase

import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.DebtRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Categoria sembrada con tipo "BOTH": sirve para prestar y para cobrar. */
const val LOAN_CATEGORY_ID = "cat_prestamos_terceros"

/** Deuda a favor del usuario: le deben dinero. */
const val DEBT_OWED_TO_ME = "OWED_TO_ME"

/**
 * Une un prestamo con su cobro.
 *
 * Prestar dinero no es un gasto: el patrimonio no cambia, se cambia efectivo por un
 * derecho de cobro. Pero el dinero si sale de una cuenta, asi que el movimiento tiene
 * que existir; lo que no debe es contarse como consumo ni, al devolverlo, como ingreso
 * nuevo. Por eso las dos patas comparten la categoria de prestamos y quedan atadas a la
 * misma deuda.
 *
 * Lo cobrado NO se guarda como un numero editable aparte: se calcula sumando los
 * ingresos atados a la deuda. Asi cobrar desde la pantalla de deudas y registrar el
 * ingreso desde movimientos son el mismo hecho contado una vez, y no pueden
 * contradecirse.
 */
@Singleton
class DebtLedger @Inject constructor(
    private val transactions: TransactionRepository,
    private val debts: DebtRepository
) {

    /**
     * Convierte [transaction] en un prestamo a [personName] y crea la deuda por cobrar.
     *
     * No toca el monto ni la cuenta del movimiento: el dinero ya salio de donde salio.
     * Devuelve null si el movimiento no puede ser un prestamo.
     */
    suspend fun lend(
        transaction: Transaction,
        personName: String,
        description: String = "",
        dueDate: Long? = null
    ): Debt? {
        if (personName.isBlank() || transaction.type != "EXPENSE" || transaction.amount <= 0L) return null
        if (transaction.debtId != null) return debts.getDebt(transaction.debtId)

        val debt = Debt(
            id = UUID.randomUUID().toString(),
            name = personName.trim(),
            description = description.trim(),
            direction = DEBT_OWED_TO_ME,
            totalAmount = transaction.amount,
            paidAmount = 0L,
            dueDate = dueDate,
            isClosed = false
        )
        debts.addDebt(debt)
        transactions.updateTransaction(
            transaction.copy(debtId = debt.id, categoryId = LOAN_CATEGORY_ID)
        )
        return debt
    }

    /**
     * Registra que [debt] pago [amount], que entra de verdad en [accountId].
     *
     * El abono es un ingreso real y no solo un contador: sin el movimiento, el saldo de
     * la cuenta se queda corto y el dinero cobrado no aparece en ningun lado.
     */
    suspend fun recordPayment(
        debt: Debt,
        amount: Long,
        accountId: String,
        currency: String,
        dateMillis: Long,
        note: String = ""
    ): Boolean {
        if (amount <= 0L || accountId.isBlank()) return false
        val payment = Transaction(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            amount = amount,
            type = if (debt.direction == DEBT_OWED_TO_ME) "INCOME" else "EXPENSE",
            categoryId = LOAN_CATEGORY_ID,
            date = dateMillis,
            note = note.ifBlank { "Abono de ${debt.name}" },
            currency = currency,
            debtId = debt.id
        )
        transactions.addTransactionWithBalance(payment)
        refreshPaidAmount(debt.id)
        return true
    }

    /** Aplica un movimiento ya existente a [debtId] (abono registrado desde Registros). */
    suspend fun applyExistingTransaction(transaction: Transaction, debtId: String): Boolean {
        val debt = debts.getDebt(debtId) ?: return false
        transactions.updateTransaction(
            transaction.copy(debtId = debt.id, categoryId = LOAN_CATEGORY_ID)
        )
        refreshPaidAmount(debt.id)
        return true
    }

    /** Desata un movimiento de su deuda y vuelve a cuadrar lo cobrado. */
    suspend fun unlink(transaction: Transaction) {
        val debtId = transaction.debtId ?: return
        transactions.updateTransaction(transaction.copy(debtId = null))
        refreshPaidAmount(debtId)
    }

    /**
     * Recalcula lo cobrado de [debtId] desde los movimientos atados.
     *
     * Se llama despues de cualquier cambio para que los dos lados cuenten lo mismo.
     */
    suspend fun refreshPaidAmount(debtId: String) {
        val debt = debts.getDebt(debtId) ?: return
        val paid = paidFromTransactions(debtId, debt)
        if (paid == debt.paidAmount && (paid >= debt.totalAmount) == debt.isClosed) return
        debts.updateDebt(
            debt.copy(
                paidAmount = paid,
                isClosed = paid >= debt.totalAmount
            )
        )
    }

    /**
     * Suma los abonos de [debtId]. El movimiento que origino la deuda va en direccion
     * contraria al abono, asi que se excluye solo, sin necesidad de marcarlo aparte.
     */
    private suspend fun paidFromTransactions(debtId: String, debt: Debt): Long {
        val paymentType = if (debt.direction == DEBT_OWED_TO_ME) "INCOME" else "EXPENSE"
        return transactions.getTransactionsForDebt(debtId)
            .filter { it.type == paymentType }
            .sumOf { it.amount }
            .coerceAtMost(debt.totalAmount)
    }
}
