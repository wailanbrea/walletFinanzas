package com.bsolutions.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "detected_movements",
    primaryKeys = ["ownerId", "id"],
    indices = [
        Index(value = ["ownerId", "source", "sourceReference"], unique = true),
        Index(value = ["ownerId", "occurredAt"]),
        Index(value = ["ownerId", "canonicalId"])
    ]
)
data class DetectedMovementEntity(
    val id: String,
    val source: String, // "EMAIL" or "NOTIFICATION"
    val senderOrApp: String = "",
    val title: String = "",
    val rawBody: String = "",
    val merchant: String? = null,
    val amountMinor: Long? = null,
    val currency: String? = null,
    val last4Digits: String? = null,
    val detectedAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // "PENDING", "APPROVED", "DISMISSED"
    val suggestedCategoryId: String? = null,
    val confidence: Int = 0,
    val needsSync: Boolean = false,
    val ownerId: String = WALLET_GUEST_OWNER_ID,
    /** Identidad estable entregada por el canal: candidate UUID o hash del aviso. */
    val sourceReference: String? = null,
    /** Hora del evento financiero; [detectedAt] es cuándo Wallet recibió la evidencia. */
    val occurredAt: Long = detectedAt,
    val direction: String = "expense",
    val eventType: String? = null,
    /** Importe comparable en DOP; null si no existe una conversión confiable. */
    val baseAmountMinor: Long? = null,
    val baseCurrency: String? = null,
    /** Raíz del grupo. En datos legacy puede ser null y equivale a [id]. */
    val canonicalId: String? = null,
    /** Si no es null, esta fila es evidencia oculta del movimiento canónico. */
    val duplicateOfId: String? = null,
    /** Coincidencia insuficiente para ocultarla: permanece visible para revisión. */
    val possibleDuplicateOfId: String? = null,
    val dedupeState: String = "CANONICAL",
    val dedupeReason: String? = null
)
