package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.core.notifications.AnonymizedBankNoticeFixture
import com.bsolutions.wallet.core.notifications.BankNoticeFixtureExport
import com.bsolutions.wallet.core.notifications.BankNoticeExtractor
import com.bsolutions.wallet.core.notifications.BankNoticePrivacy
import com.bsolutions.wallet.core.notifications.InstalledBankingAppsDetector
import com.bsolutions.wallet.core.notifications.NotificationCaptureData
import com.bsolutions.wallet.core.notifications.RAW_NOTICE_RETENTION_MILLIS
import com.bsolutions.wallet.data.local.dao.RawBankNoticeDao
import com.bsolutions.wallet.data.local.entity.NotificationSourceEntity
import com.bsolutions.wallet.data.local.entity.RawBankNoticeEntity
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

enum class NotificationCaptureOutcome {
    DISCARDED_SENSITIVE,
    SOURCE_DISCOVERED,
    CAPTURED,
    DUPLICATE
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class BankNotificationRepository @Inject constructor(
    private val dao: RawBankNoticeDao,
    private val ownerScope: WalletOwnerScope,
    private val gson: Gson,
    private val installedAppsDetector: InstalledBankingAppsDetector,
    private val detectedMovementRepository: DetectedMovementRepository
) {
    val sources: Flow<List<NotificationSourceEntity>> = ownerScope.ownerId
        .flatMapLatest(dao::observeSources)

    fun notices(limit: Int = 100): Flow<List<RawBankNoticeEntity>> = ownerScope.ownerId
        .flatMapLatest { ownerId -> dao.observeNotices(ownerId, limit) }

    /**
     * Punto único de entrada del listener. El filtro de OTP ocurre antes de consultar o
     * modificar Room, de modo que un código nunca deja huella ni como fuente observada.
     */
    suspend fun processNotification(
        capture: NotificationCaptureData,
        capturedAt: Long = System.currentTimeMillis()
    ): NotificationCaptureOutcome {
        if (BankNoticePrivacy.isSensitiveAuthenticationNotice(capture.title, capture.text, capture.bigText)) {
            return NotificationCaptureOutcome.DISCARDED_SENSITIVE
        }

        val ownerId = ownerScope.currentOwnerId()
        dao.recordSource(
            NotificationSourceEntity(
                packageName = capture.packageName,
                displayName = capture.appLabel,
                lastSeenAt = capture.postTime,
                ownerId = ownerId
            )
        )
        if (dao.isSourceEnabled(ownerId, capture.packageName) != true) {
            return NotificationCaptureOutcome.SOURCE_DISCOVERED
        }

        dao.purgeExpired(ownerId, capturedAt)
        val notice = RawBankNoticeEntity(
                id = capture.deterministicId,
                packageName = capture.packageName,
                appLabel = capture.appLabel,
                notificationKeyHash = BankNoticePrivacy.sha256Hex(capture.notificationKey),
                contentHash = capture.contentHash,
                title = capture.title.take(MAX_TITLE_LENGTH),
                text = capture.text.take(MAX_BODY_LENGTH),
                bigText = capture.bigText.take(MAX_BODY_LENGTH),
                postTime = capture.postTime,
                capturedAt = capturedAt,
                expiresAt = capturedAt + RAW_NOTICE_RETENTION_MILLIS,
                ownerId = ownerId
            )
        val inserted = dao.insertNotice(notice)
        return if (inserted == -1L) {
            NotificationCaptureOutcome.DUPLICATE
        } else {
            // La captura cruda ya quedó segura. Si el extractor falla, la pantalla de
            // diagnóstico reintentará el backfill sin perder el aviso original.
            runCatching { reconcileNotice(notice) }
            NotificationCaptureOutcome.CAPTURED
        }
    }

    suspend fun setSourceEnabled(packageName: String, enabled: Boolean) {
        require(packageName.isNotBlank()) { "El paquete de la app no puede estar vacío." }
        dao.setSourceEnabled(ownerScope.currentOwnerId(), packageName, enabled)
    }

    /**
     * Registra únicamente apps conocidas que Android confirma como instaladas.
     * INSERT IGNORE conserva autorización, nombre y métricas de fuentes existentes.
     */
    suspend fun discoverInstalledKnownApps(): Int {
        val ownerId = ownerScope.currentOwnerId()
        val suggestions = installedAppsDetector.detect().map { app ->
            NotificationSourceEntity(
                packageName = app.packageName,
                displayName = app.displayName,
                isEnabled = false,
                lastSeenAt = 0L,
                observedCount = 0,
                ownerId = ownerId
            )
        }
        if (suggestions.isEmpty()) return 0
        return dao.insertSources(suggestions).count { rowId -> rowId != -1L }
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int =
        dao.purgeExpired(ownerScope.currentOwnerId(), now)

    suspend fun clearNotices() = dao.clearNotices(ownerScope.currentOwnerId())

    /** Reintenta avisos históricos y hace idempotente una interrupción tras la captura. */
    suspend fun reconcileCapturedNotices(): Int {
        val ownerId = ownerScope.currentOwnerId()
        return dao.getAllNotices(ownerId).count { notice ->
            runCatching { reconcileNotice(notice) }.getOrNull() != null
        }
    }

    suspend fun buildAnonymizedFixtureExport(now: Long = System.currentTimeMillis()): String {
        val fixtures = dao.getAllNotices(ownerScope.currentOwnerId()).map { notice ->
            val merchant = BankNoticeExtractor.parse(
                title = notice.title,
                body = listOf(notice.text, notice.bigText).filter(String::isNotBlank).joinToString(" ")
            )?.merchant
            AnonymizedBankNoticeFixture(
                source = "bank-app-${BankNoticePrivacy.sha256Hex(notice.packageName).take(12)}",
                postTime = notice.postTime,
                title = BankNoticePrivacy.redactForFixture(notice.title, merchant),
                text = BankNoticePrivacy.redactForFixture(notice.text, merchant),
                bigText = BankNoticePrivacy.redactForFixture(notice.bigText, merchant)
            )
        }
        return gson.toJson(
            BankNoticeFixtureExport(
                generatedAt = now,
                warning = "Exportación anonimizada automáticamente. Revísala antes de compartirla.",
                fixtures = fixtures
            )
        )
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 500
        const val MAX_BODY_LENGTH = 12_000
    }

    private suspend fun reconcileNotice(notice: RawBankNoticeEntity): MovementIngestionResult? {
        val parsed = BankNoticeExtractor.parse(
            title = notice.title,
            body = listOf(notice.text, notice.bigText).filter(String::isNotBlank).joinToString(" ")
        ) ?: return null
        return detectedMovementRepository.ingestNotification(
            ownerId = notice.ownerId,
            noticeId = notice.id,
            appLabel = notice.appLabel,
            title = notice.title,
            occurredAt = notice.postTime,
            parsed = parsed
        )
    }
}
