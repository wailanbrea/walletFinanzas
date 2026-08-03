package com.bsolutions.wallet.core.notifications

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bsolutions.wallet.data.repository.BankNotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Capturador de la Fase A. No extrae, categoriza ni crea movimientos.
 *
 * El contenido se mantiene en memoria hasta que [BankNotificationRepository] confirma
 * que no es un OTP y que el usuario autorizó explícitamente el paquete emisor.
 */
@AndroidEntryPoint
class BankNotificationListenerService : NotificationListenerService() {
    @Inject
    lateinit var repository: BankNotificationRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val sourcePackage = sbn.packageName?.takeIf(String::isNotBlank) ?: return
        if (sourcePackage == packageName || sourcePackage in IGNORED_SYSTEM_PACKAGES) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString(separator = "\n") { it.toString() }
                .orEmpty()
        if (title.isBlank() && text.isBlank() && bigText.isBlank()) return

        val capture = NotificationCaptureData(
            packageName = sourcePackage,
            appLabel = appLabel(sourcePackage),
            notificationKey = sbn.key.orEmpty(),
            title = title,
            text = text,
            bigText = bigText,
            postTime = sbn.postTime
        )
        serviceScope.launch {
            // El resultado no se registra: incluso un log de diagnóstico puede revelar
            // que llegó un código o un aviso financiero.
            repository.processNotification(capture)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch { repository.purgeExpired() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRebind(ComponentName(this, BankNotificationListenerService::class.java))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun appLabel(sourcePackage: String): String = runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(sourcePackage, 0)
        ).toString()
    }.getOrDefault(sourcePackage)

    private companion object {
        val IGNORED_SYSTEM_PACKAGES = setOf("android", "com.android.systemui")
    }
}
