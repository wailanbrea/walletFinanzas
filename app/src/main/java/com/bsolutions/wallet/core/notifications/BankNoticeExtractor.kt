package com.bsolutions.wallet.core.notifications

import com.bsolutions.wallet.core.common.ExpenseCategorizer
import java.text.Normalizer
import java.util.Locale

data class ParsedBankNotice(
    val merchant: String?,
    val amountMinor: Long?,
    val currency: String?,
    val last4Digits: String?,
    val suggestedCategoryId: String?
)

object BankNoticeExtractor {

    // Regex para detectar patrones bancarios comunes (Popular, Banreservas, BHD, Scotiabank, Qik, etc.)
    private val patternAmountCurrency = Regex("""(?i)(RD\$\s*|USD\$\s*|US\$\s*|EUR\$\s*|\$\s*|DOP\s*|USD\s*)([\d,]+\.\d{2})""")
    private val patternLast4 = Regex("""(?i)(tarjeta|cta|cuenta|card|\*{2,4}|terminada en)\s*(\d{4})""")
    private val patternMerchantIn = Regex("""(?i)(?:en|en el comercio|en el establecimiento|comercio)\s+([A-Z0-9\s\.\*\-\&\']+?)(?=\s+(?:con|por|terminada|cta|cuenta|card|tarjeta|\*{2,4}|el|la|fecha|\.|$))""")

    fun parse(title: String, body: String): ParsedBankNotice? {
        val text = "$title $body"
        val normalizedText = normalize(text)

        // Si contiene palabras clave negativas (ej. preaprobado, estado de cuenta), ignorar
        if (normalizedText.contains("preaprobado") || normalizedText.contains("solicitud") || normalizedText.contains("clave dinamica")) {
            return null
        }

        // 1. Extracción de Monto y Moneda
        val amountMatch = patternAmountCurrency.find(text)
        var amountMinor: Long? = null
        var currency: String? = null

        if (amountMatch != null) {
            val currGroup = amountMatch.groupValues[1].uppercase(Locale.ROOT)
            val valGroup = amountMatch.groupValues[2].replace(",", "")
            
            currency = when {
                currGroup.contains("US") || currGroup.contains("USD") -> "USD"
                currGroup.contains("EUR") -> "EUR"
                else -> "DOP"
            }

            val doubleVal = valGroup.toDoubleOrNull()
            if (doubleVal != null) {
                amountMinor = Math.round(doubleVal * 100)
            }
        }

        // Si no hay monto detectado, no es una notificación de consumo
        if (amountMinor == null || amountMinor <= 0L) {
            return null
        }

        // 2. Extracción de Últimos 4 dígitos
        val last4Match = patternLast4.find(text)
        val last4Digits = last4Match?.groupValues?.get(2)

        // 3. Extracción de Comercio
        var merchant: String? = null
        val merchantMatch = patternMerchantIn.find(text)
        if (merchantMatch != null) {
            val candidate = merchantMatch.groupValues[1].trim()
            if (candidate.length in 3..40) {
                merchant = candidate
            }
        }

        // Fallback para comercio si no coincide la regex de "en ..."
        if (merchant == null) {
            val words = text.split(" ")
            val enIndex = words.indexOfFirst { it.equals("en", ignoreCase = true) }
            if (enIndex != -1 && enIndex + 1 < words.size) {
                merchant = words.subList(enIndex + 1, minOf(enIndex + 4, words.size)).joinToString(" ")
            }
        }

        // 4. Sugerencia de Categoría
        val suggestedCategory = if (merchant != null) {
            ExpenseCategorizer.inferCategoryId(merchant)
        } else {
            ExpenseCategorizer.inferCategoryId(text)
        }

        return ParsedBankNotice(
            merchant = merchant,
            amountMinor = amountMinor,
            currency = currency ?: "DOP",
            last4Digits = last4Digits,
            suggestedCategoryId = suggestedCategory
        )
    }

    private fun normalize(str: String): String {
        val normalized = Normalizer.normalize(str, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase(Locale.ROOT)
    }
}
