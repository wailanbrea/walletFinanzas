package com.bsolutions.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // "CASH", "BANK", "SAVINGS"
    val balance: Long, // minor units (cents)
    val currency: String = "USD",
    val countryCode: String = "DO",
    val institutionName: String? = null,
    val cardLastFour: String? = null,
    val isDeleted: Boolean = false
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val amount: Long, // minor units (cents)
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val categoryId: String,
    val date: Long, // Epoch millis (UTC)
    val note: String = "",
    val currency: String = "DOP", // ISO de la moneda del movimiento (heredada de su cuenta)
    val isDeleted: Boolean = false
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String, // Material Icons name
    val colorHex: String, // Hex color code
    val isDeleted: Boolean = false
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val limitAmount: Long, // minor units (cents)
    val spentAmount: Long, // minor units (cents)
    val period: String = "MONTHLY",
    val isDeleted: Boolean = false
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "track_changes", // Material Icons name
    val targetAmount: Long, // minor units (cents)
    val savedAmount: Long = 0L, // minor units (cents)
    val targetDate: Long? = null, // Epoch millis (UTC), opcional
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false
)

@Entity(tableName = "planned_payments")
data class PlannedPaymentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accountId: String,
    val categoryId: String = "",
    val amount: Long, // minor units (cents)
    val type: String = "EXPENSE", // "EXPENSE" | "INCOME"
    val frequency: String = "MONTHLY", // "WEEKLY", "BIWEEKLY", "MONTHLY", "YEARLY", "ONCE"
    val nextDueDate: Long, // Epoch millis (UTC)
    val isActive: Boolean = true,
    val isDeleted: Boolean = false
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String, // persona o entidad
    val description: String = "",
    val direction: String = "I_OWE", // "I_OWE" (yo debo) | "OWED_TO_ME" (me deben)
    val totalAmount: Long, // minor units (cents)
    val paidAmount: Long = 0L, // minor units (cents)
    val dueDate: Long? = null, // Epoch millis (UTC), opcional
    val isClosed: Boolean = false,
    val isDeleted: Boolean = false
)

/** Conexión bancaria vía Salt Edge (sandbox en Etapa A). Solo lectura (AISP). */
@Entity(tableName = "bank_connections")
data class BankConnectionEntity(
    @PrimaryKey
    val id: String, // connection id de Salt Edge
    val providerName: String,
    val providerCode: String = "",
    val countryCode: String = "",
    val status: String = "",
    val lastSyncAt: Long = 0L // Epoch millis del último refresh local
)
