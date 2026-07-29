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

data class Transaction(
    val id: String,
    val accountId: String,
    val amount: Long, // minor units (cents)
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val categoryId: String,
    val date: Long, // Epoch UTC
    val note: String,
    val currency: String = "DOP" // moneda del movimiento (la de su cuenta)
)

data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: String
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
