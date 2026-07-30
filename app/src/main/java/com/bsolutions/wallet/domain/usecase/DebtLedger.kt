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
        applyDelta(debt.id, payment.type, payment.amount)
        return true
    }

    /**
     * Ata un movimiento ya existente a [debtId].
     *
     * Segun su direccion se interpreta solo: un ingreso es un abono y baja lo que
     * falta; un gasto es un cargo nuevo y sube lo que deben. Asi el currier de algo
     * que ya prestaste se suma a la misma deuda en vez de abrir otra.
     */
    suspend fun applyExistingTransaction(transaction: Transaction, debtId: String): Boolean {
        val debt = debts.getDebt(debtId) ?: return false
        if (transaction.debtId == debtId) return true
        transactions.updateTransaction(
            transaction.copy(debtId = debt.id, categoryId = LOAN_CATEGORY_ID)
        )
        applyDelta(debt.id, transaction.type, transaction.amount)
        return true
    }

    /**
     * Anade un cargo nuevo a [debt]: dinero que sale ahora y engorda lo que te deben.
     * El gasto se registra de verdad en [accountId], no es solo subir el total.
     */
    suspend fun addCharge(
        debt: Debt,
        amount: Long,
        accountId: String,
        currency: String,
        dateMillis: Long,
        note: String = ""
    ): Boolean {
        if (amount <= 0L || accountId.isBlank()) return false
        val charge = Transaction(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            amount = amount,
            type = if (debt.direction == DEBT_OWED_TO_ME) "EXPENSE" else "INCOME",
            categoryId = LOAN_CATEGORY_ID,
            date = dateMillis,
            note = note.ifBlank { "Cargo de ${debt.name}" },
            currency = currency,
            debtId = debt.id
        )
        transactions.addTransactionWithBalance(charge)
        applyDelta(debt.id, charge.type, charge.amount)
        return true
    }

    /**
     * Ajusta la deuda por un movimiento que se creo ya atado a ella.
     *
     * Lo usa la clasificacion de correos: el movimiento nace con su deuda puesta, asi
     * que no hay que atarlo despues, solo mover la deuda.
     */
    suspend fun onLinkedTransactionAdded(transaction: Transaction) {
        val debtId = transaction.debtId ?: return
        applyDelta(debtId, transaction.type, transaction.amount)
    }

    /** Desata un movimiento de su deuda y deshace su efecto. */
    suspend fun unlink(transaction: Transaction) {
        val debtId = transaction.debtId ?: return
        transactions.updateTransaction(transaction.copy(debtId = null))
        applyDelta(debtId, transaction.type, -transaction.amount)
    }

    /** Deshace el efecto de un movimiento borrado. */
    suspend fun onTransactionDeleted(transaction: Transaction) {
        val debtId = transaction.debtId ?: return
        applyDelta(debtId, transaction.type, -transaction.amount)
    }

    /** Ajusta la deuda por la diferencia cuando se edita el monto de un movimiento atado. */
    suspend fun onAmountEdited(transaction: Transaction, oldAmount: Long) {
        val debtId = transaction.debtId ?: return
        applyDelta(debtId, transaction.type, transaction.amount - oldAmount)
    }

    /**
     * Mueve la deuda en [delta] segun la direccion del movimiento.
     *
     * Se ajusta por diferencia y no se recalcula desde los movimientos a proposito: hay
     * deudas creadas a mano cuyo historial nunca fue un movimiento, y recalcular las
     * dejaria en cero. Con deltas, lo escrito a mano y lo que viene de movimientos
     * conviven.
     */
    private suspend fun applyDelta(debtId: String, transactionType: String, delta: Long) {
        val debt = debts.getDebt(debtId) ?: return
        val isPayment = transactionType == paymentTypeFor(debt)
        val moved = if (isPayment) {
            debt.copy(paidAmount = (debt.paidAmount + delta).coerceAtLeast(0L))
        } else {
            debt.copy(totalAmount = (debt.totalAmount + delta).coerceAtLeast(0L))
        }
        debts.updateDebt(
            moved.copy(isClosed = moved.totalAmount > 0L && moved.paidAmount >= moved.totalAmount)
        )
    }

    /** La direccion en la que un movimiento cuenta como pago de esta deuda. */
    private fun paymentTypeFor(debt: Debt): String =
        if (debt.direction == DEBT_OWED_TO_ME) "INCOME" else "EXPENSE"
}
