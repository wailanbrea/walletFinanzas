package com.bsolutions.wallet.domain.model

data class Account(
    val id: String,
    val name: String,
    val type: String, // "CASH", "BANK", "SAVINGS"
    val balance: Long, // minor units (cents)
    val currency: String,
    val countryCode: String = "DO",
    val institutionName: String? = null,
    /** Solo los últimos cuatro dígitos; nunca se almacena el número completo de tarjeta. */
    val cardLastFour: String? = null,
    val creditLimit: Long? = null // minor units (cents)
)

/** Categoría de las dos patas de una transferencia; ninguna es gasto ni ingreso. */
const val TRANSFER_CATEGORY_ID = "cat_transferencias"

data class Transaction(
    val id: String,
    val accountId: String,
    val amount: Long, // minor units (cents)
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val categoryId: String,
    val date: Long, // Epoch UTC
    val note: String,
    val currency: String = "DOP", // moneda del movimiento (la de su cuenta)
    /** Deuda a la que pertenece: el gasto que la origino o un abono recibido. */
    val debtId: String? = null
) {
    /**
     * Si cuenta como gasto o ingreso propio en los totales y las gráficas.
     *
     * Un movimiento atado a una deuda no lo es: prestar dinero y cobrarlo no cambia el
     * patrimonio, se cambia efectivo por un derecho de cobro. Contarlo haría que el mes
     * en que prestas parezca un gasto enorme y el mes en que te pagan un ingreso que no
     * ganaste. El saldo de la cuenta sí se movió, y eso lo refleja el balance.
     *
     * Toda suma de gastos o ingresos debe filtrar por aquí; está en el modelo justamente
     * para que no se olvide al añadir una gráfica nueva.
     */
    val isConsumption: Boolean get() = debtId == null && categoryId != TRANSFER_CATEGORY_ID
}

data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    /** "EXPENSE", "INCOME" o "BOTH". Un gasto no debe poder etiquetarse "Salario". */
    val type: String = "EXPENSE"
)

data class Budget(
    val id: String,
    val categoryId: String,
    val limitAmount: Long,
    val spentAmount: Long,
    val period: String
)

data class Goal(
    val id: String,
    val name: String,
    val icon: String,
    val targetAmount: Long, // minor units (cents)
    val savedAmount: Long, // minor units (cents)
    val targetDate: Long?, // Epoch millis (UTC)
    val isCompleted: Boolean
) {
    val progress: Float
        get() = if (targetAmount > 0) (savedAmount.toFloat() / targetAmount).coerceIn(0f, 1f) else 0f
}

data class PlannedPayment(
    val id: String,
    val name: String,
    val accountId: String,
    val categoryId: String,
    val amount: Long, // minor units (cents)
    val type: String, // "EXPENSE" | "INCOME"
    val frequency: String, // "WEEKLY", "BIWEEKLY", "MONTHLY", "YEARLY", "ONCE"
    val nextDueDate: Long, // Epoch millis (UTC)
    val isActive: Boolean
)

data class Debt(
    val id: String,
    val name: String,
    val description: String,
    val direction: String, // "I_OWE" | "OWED_TO_ME"
    val totalAmount: Long, // minor units (cents)
    val paidAmount: Long, // minor units (cents)
    val dueDate: Long?, // Epoch millis (UTC)
    val isClosed: Boolean
) {
    val remainingAmount: Long
        get() = (totalAmount - paidAmount).coerceAtLeast(0L)
    val progress: Float
        get() = if (totalAmount > 0) (paidAmount.toFloat() / totalAmount).coerceIn(0f, 1f) else 0f
}
