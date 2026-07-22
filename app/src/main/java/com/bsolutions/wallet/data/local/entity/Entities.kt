package com.bsolutions.wallet.data.local.entity

import androidx.room.Entity
import java.util.UUID

const val WALLET_GUEST_OWNER_ID = "guest"

@Entity(tableName = "accounts", primaryKeys = ["ownerId", "id"])
data class AccountEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // "CASH", "BANK", "SAVINGS"
    val balance: Long, // minor units (cents)
    val currency: String = "USD",
    val countryCode: String = "DO",
    val institutionName: String? = null,
    val cardLastFour: String? = null,
    val isDeleted: Boolean = false,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

@Entity(tableName = "transactions", primaryKeys = ["ownerId", "id"])
data class TransactionEntity(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val amount: Long, // minor units (cents)
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val categoryId: String,
    val date: Long, // Epoch millis (UTC)
    val note: String = "",
    val currency: String = "DOP", // ISO de la moneda del movimiento (heredada de su cuenta)
    val isDeleted: Boolean = false,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

@Entity(tableName = "categories", primaryKeys = ["ownerId", "id"])
data class CategoryEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String, // Material Icons name
    val colorHex: String, // Hex color code
    val isDeleted: Boolean = false,
    /** Cambió localmente y debe subirse antes del siguiente pull remoto. */
    val needsSync: Boolean = true,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

@Entity(tableName = "budgets", primaryKeys = ["ownerId", "id"])
data class BudgetEntity(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val limitAmount: Long, // minor units (cents)
    val spentAmount: Long, // minor units (cents)
    val period: String = "MONTHLY",
    val isDeleted: Boolean = false,
    val needsSync: Boolean = true,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

@Entity(tableName = "goals", primaryKeys = ["ownerId", "id"])
data class GoalEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "track_changes", // Material Icons name
    val targetAmount: Long, // minor units (cents)
    val savedAmount: Long = 0L, // minor units (cents)
    val targetDate: Long? = null, // Epoch millis (UTC), opcional
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false,
    val needsSync: Boolean = true,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

@Entity(tableName = "planned_payments", primaryKeys = ["ownerId", "id"])
data class PlannedPaymentEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accountId: String,
    val categoryId: String = "",
    val amount: Long, // minor units (cents)
    val type: String = "EXPENSE", // "EXPENSE" | "INCOME"
    val frequency: String = "MONTHLY", // "WEEKLY", "BIWEEKLY", "MONTHLY", "YEARLY", "ONCE"
    val nextDueDate: Long, // Epoch millis (UTC)
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val needsSync: Boolean = true,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

@Entity(tableName = "debts", primaryKeys = ["ownerId", "id"])
data class DebtEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // persona o entidad
    val description: String = "",
    val direction: String = "I_OWE", // "I_OWE" (yo debo) | "OWED_TO_ME" (me deben)
    val totalAmount: Long, // minor units (cents)
    val paidAmount: Long = 0L, // minor units (cents)
    val dueDate: Long? = null, // Epoch millis (UTC), opcional
    val isClosed: Boolean = false,
    val isDeleted: Boolean = false,
    val needsSync: Boolean = true,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

/**
 * Operación local pendiente de subir al backend (sync offline-first, solo CREATE).
 * [payload] es un snapshot JSON de la entidad al momento de encolarse: así la cuenta
 * se sube con su saldo INICIAL y los movimientos posteriores ajustan el saldo en el
 * servidor sin contarse dos veces. PK determinista → re-encolar no duplica.
 */
@Entity(tableName = "pending_operations", primaryKeys = ["ownerId", "id"])
data class PendingOperationEntity(
    val id: String, // "$entityType:$entityId"
    val entityType: String, // "ACCOUNT" | "TRANSACTION"
    val entityId: String,
    val payload: String, // snapshot JSON de la entidad
    val createdAt: Long,
    val attempts: Int = 0,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

/** Conexión bancaria vía Salt Edge (sandbox en Etapa A). Solo lectura (AISP). */
@Entity(tableName = "bank_connections", primaryKeys = ["ownerId", "id"])
data class BankConnectionEntity(
    val id: String, // connection id de Salt Edge
    val providerName: String,
    val providerCode: String = "",
    val countryCode: String = "",
    val status: String = "",
    val ownerId: String = WALLET_GUEST_OWNER_ID,
    val lastSyncAt: Long = 0L // Epoch millis del último refresh local
)
