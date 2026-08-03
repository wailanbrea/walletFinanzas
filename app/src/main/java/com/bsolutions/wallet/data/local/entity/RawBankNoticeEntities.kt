package com.bsolutions.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Fuente que ha emitido una notificación no sensible.
 *
 * No contiene el texto del aviso. Nace deshabilitada y sólo permite guardar contenido
 * después de que el usuario la marque explícitamente como una app bancaria.
 */
@Entity(tableName = "notification_sources", primaryKeys = ["ownerId", "packageName"])
data class NotificationSourceEntity(
    val packageName: String,
    val displayName: String,
    val isEnabled: Boolean = false,
    val lastSeenAt: Long,
    val observedCount: Int = 1,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)

/** Texto crudo cifrado por SQLCipher y retenido como máximo 30 días. */
@Entity(
    tableName = "raw_bank_notices",
    primaryKeys = ["ownerId", "id"],
    indices = [
        Index(value = ["ownerId", "postTime"]),
        Index(value = ["ownerId", "packageName"]),
        Index(value = ["ownerId", "expiresAt"])
    ]
)
data class RawBankNoticeEntity(
    val id: String,
    val packageName: String,
    val appLabel: String,
    /** Hash de la clave del sistema; la clave original nunca se persiste. */
    val notificationKeyHash: String,
    val contentHash: String,
    val title: String,
    val text: String,
    val bigText: String,
    val postTime: Long,
    val capturedAt: Long,
    val expiresAt: Long,
    val ownerId: String = WALLET_GUEST_OWNER_ID
)
