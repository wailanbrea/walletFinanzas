package com.bsolutions.wallet.core.notifications

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal const val RAW_NOTICE_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L

data class NotificationCaptureData(
    val packageName: String,
    val appLabel: String,
    val notificationKey: String,
    val title: String,
    val text: String,
    val bigText: String,
    val postTime: Long
) {
    val contentHash: String
        get() = BankNoticePrivacy.sha256Hex("$title\u0000$text\u0000$bigText")

    val deterministicId: String
        get() = BankNoticePrivacy.sha256Hex(
            "$packageName\u0000$notificationKey\u0000$contentHash"
        )
}

data class AnonymizedBankNoticeFixture(
    val source: String,
    val postTime: Long,
    val title: String,
    val text: String,
    val bigText: String
)

data class BankNoticeFixtureExport(
    val schemaVersion: Int = 1,
    val generatedAt: Long,
    val warning: String,
    val fixtures: List<AnonymizedBankNoticeFixture>
)

object BankNoticePrivacy {
    private const val REDACTED_EMAIL = "<EMAIL>"
    private const val REDACTED_URL = "<URL>"
    private const val REDACTED_PHONE = "<TELÉFONO>"
    private const val REDACTED_NUMBER = "<NÚMERO>"
    private const val REDACTED_MERCHANT = "<COMERCIO>"

    private val strongAuthenticationPhrases = listOf(
        "codigo de verificacion",
        "codigo de seguridad",
        "codigo de acceso",
        "clave dinamica",
        "clave de seguridad",
        "one time password",
        "one-time password",
        "verification code",
        "security code",
        "authentication code",
        "temporary code",
        "your code",
        "codigo temporal",
        "codigo para iniciar sesion",
        "codigo para autorizar",
        "token de seguridad",
        "no compartas este codigo"
    )
    private val shortAuthenticationWord = Regex("""\b(otp|pin)\b""")
    private val shortCode = Regex("""\b\d{4,8}\b""")
    private val email = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
    private val url = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)
    private val phone = Regex("""(?<!\d)(?:\+?1[\s.-]?)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}(?!\d)""")
    private val accountReference = Regex(
        """(?i)\b(tarjeta|cuenta|cta|card|terminada en|finalizada en)([\s*#:.-]*)(\d{4,})\b"""
    )
    private val longNumber = Regex("""(?<![\d.,])\d{6,}(?![\d.,])""")

    /**
     * Se ejecuta con el texto únicamente en memoria. Si devuelve true, el caller no
     * debe escribir ni siquiera metadatos derivados de esa notificación.
     */
    fun isSensitiveAuthenticationNotice(title: String, text: String, bigText: String): Boolean {
        val normalized = normalize("$title $text $bigText")
        if (strongAuthenticationPhrases.any(normalized::contains)) return true
        if (shortAuthenticationWord.containsMatchIn(normalized)) return true

        val mentionsGenericCode = normalized.contains("tu codigo") ||
            normalized.contains("el codigo") ||
            normalized.contains("este codigo")
        val expiresOrWarns = normalized.contains("vence") || normalized.contains("expira") ||
            normalized.contains("no lo compartas") || normalized.contains("no compartir")
        return shortCode.containsMatchIn(normalized) && (mentionsGenericCode || expiresOrWarns)
    }

    fun redactForFixture(value: String, merchant: String? = null): String {
        var redacted = value
        merchant?.trim()?.takeIf { it.length >= 3 }?.let { rawMerchant ->
            redacted = redacted.replace(
                Regex(Regex.escape(rawMerchant), RegexOption.IGNORE_CASE),
                REDACTED_MERCHANT
            )
        }
        redacted = email.replace(redacted, REDACTED_EMAIL)
        redacted = url.replace(redacted, REDACTED_URL)
        redacted = phone.replace(redacted, REDACTED_PHONE)
        redacted = accountReference.replace(redacted) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}0000"
        }
        return longNumber.replace(redacted, REDACTED_NUMBER)
    }

    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
}
