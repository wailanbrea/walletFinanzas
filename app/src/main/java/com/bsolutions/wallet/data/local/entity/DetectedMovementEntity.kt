package com.bsolutions.wallet.data.local.entity

import androidx.room.Entity

@Entity(tableName = "detected_movements", primaryKeys = ["ownerId", "id"])
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
    val ownerId: String = WALLET_GUEST_OWNER_ID
)
