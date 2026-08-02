package com.bsolutions.wallet.core.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import com.bsolutions.wallet.data.repository.DetectedMovementRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class BankNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var detectedMovementRepository: DetectedMovementRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val sbnNonNull = sbn ?: return
        val packageName = sbnNonNull.packageName ?: ""
        val extras = sbnNonNull.notification?.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isEmpty() && text.isEmpty()) return

        val parsed = BankNoticeExtractor.parse(title, text) ?: return

        val entity = DetectedMovementEntity(
            id = "notif_" + UUID.randomUUID().toString().take(12),
            source = "NOTIFICATION",
            senderOrApp = packageName,
            title = title,
            rawBody = text,
            merchant = parsed.merchant,
            amountMinor = parsed.amountMinor,
            currency = parsed.currency,
            last4Digits = parsed.last4Digits,
            detectedAt = System.currentTimeMillis(),
            status = "PENDING",
            suggestedCategoryId = parsed.suggestedCategoryId,
            confidence = 85,
            needsSync = true
        )

        serviceScope.launch {
            detectedMovementRepository.saveMovement(entity)
        }
    }
}
