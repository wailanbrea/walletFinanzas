package com.bsolutions.wallet.core.deduplication

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

enum class FinancialEvidenceSource {
    EMAIL_GMAIL,
    EMAIL_MICROSOFT,
    BANK_NOTIFICATION,
    MANUAL_TRANSACTION
}

data class FinancialEventEvidence(
    val id: String,
    val source: FinancialEvidenceSource,
    val occurredAt: Long,
    val direction: String,
    val amountMinor: Long?,
    val currency: String?,
    val baseAmountMinor: Long? = null,
    val baseCurrency: String? = null,
    val merchant: String? = null,
    val last4Digits: String? = null,
    val eventType: String? = null
)

sealed interface FinancialMatchResult {
    data object NoMatch : FinancialMatchResult

    data class StrongMatch(
        val canonical: FinancialEventEvidence,
        val reason: String
    ) : FinancialMatchResult

    data class PossibleDuplicate(
        val candidate: FinancialEventEvidence,
        val reason: String
    ) : FinancialMatchResult
}

/**
 * Empareja evidencias del mismo evento financiero sin usar texto literal ni monto solo.
 * Los resultados ambiguos nunca se fusionan automáticamente.
 */
object FinancialEventMatcher {
    const val BASE_CURRENCY = "DOP"
    const val AMOUNT_TOLERANCE = 0.03
    const val EMAIL_WINDOW_MILLIS = 72L * 60L * 60L * 1_000L
    const val PUSH_EMAIL_WINDOW_MILLIS = 6L * 60L * 60L * 1_000L
    const val MANUAL_WINDOW_MILLIS = 24L * 60L * 60L * 1_000L

    /**
     * Cuanto puede separar a dos avisos del MISMO canal para seguir siendo el mismo evento.
     *
     * Corta a proposito. Dos avisos del mismo canal son una de dos cosas: el mismo mensaje
     * que llego dos veces —cosa de segundos— o dos compras distintas. Con la ventana larga
     * de antes, comprar dos veces lo mismo en la misma tienda el mismo dia hacia que la
     * segunda compra se marcara como duplicado y desapareciera. Y ese error no se ve: el
     * dinero salio de la cuenta y el movimiento no esta en ningun lado.
     *
     * Equivocarse al reves solo deja dos lineas que se descartan de un toque.
     */
    const val SAME_SOURCE_WINDOW_MILLIS = 10L * 60L * 1_000L

    private val gatewayPair = setOf("CARD_PURCHASE_APPROVED", "RECEIPT_CONFIRMED")

    fun match(
        incoming: FinancialEventEvidence,
        existing: List<FinancialEventEvidence>
    ): FinancialMatchResult {
        val compatible = existing.mapNotNull { candidate ->
            pairCompatibility(incoming, candidate)?.let { compatibility -> candidate to compatibility }
        }
        if (compatible.isEmpty()) return FinancialMatchResult.NoMatch

        val strong = compatible.filter { (_, compatibility) -> compatibility.strong }
        if (strong.size == 1) {
            val (candidate, compatibility) = strong.single()
            return FinancialMatchResult.StrongMatch(candidate, compatibility.reason)
        }
        if (strong.size > 1) {
            val firstCand = strong.first().first
            val allSameEvent = strong.all { (cand, _) ->
                cand.merchant == firstCand.merchant &&
                cand.amountMinor == firstCand.amountMinor &&
                cand.last4Digits == firstCand.last4Digits &&
                abs(cand.occurredAt - firstCand.occurredAt) < 1_000L
            }
            val nearest = strong.minBy { (candidate, _) -> abs(incoming.occurredAt - candidate.occurredAt) }
            return if (allSameEvent) {
                FinancialMatchResult.StrongMatch(nearest.first, nearest.second.reason)
            } else {
                FinancialMatchResult.PossibleDuplicate(
                    candidate = nearest.first,
                    reason = "Más de una evidencia coincide; requiere revisión manual."
                )
            }
        }

        val nearest = compatible.minBy { (candidate, _) -> abs(incoming.occurredAt - candidate.occurredAt) }
        return FinancialMatchResult.PossibleDuplicate(nearest.first, nearest.second.reason)
    }

    fun preferredCanonical(
        first: FinancialEventEvidence,
        second: FinancialEventEvidence
    ): FinancialEventEvidence {
        val firstScore = canonicalScore(first)
        val secondScore = canonicalScore(second)
        return when {
            firstScore > secondScore -> first
            secondScore > firstScore -> second
            first.occurredAt <= second.occurredAt -> first
            else -> second
        }
    }

    fun amountsMatch(first: FinancialEventEvidence, second: FinancialEventEvidence): Boolean {
        val amounts = comparableAmounts(first, second) ?: return false
        val reference = maxOf(abs(amounts.first), abs(amounts.second))
        if (reference == 0L) return false
        return abs(abs(amounts.first) - abs(amounts.second)).toDouble() / reference <= AMOUNT_TOLERANCE
    }

    private fun pairCompatibility(
        first: FinancialEventEvidence,
        second: FinancialEventEvidence
    ): PairCompatibility? {
        if (first.id == second.id) return null
        if (!directionsCompatible(first.direction, second.direction)) return null
        if (!eventsCompatible(first.eventType, second.eventType)) return null

        val window = matchingWindow(first, second) ?: return null
        if (abs(first.occurredAt - second.occurredAt) > window) return null
        if (!amountsMatch(first, second)) return null

        val firstLast4 = first.last4Digits?.takeIf { it.length == 4 }
        val secondLast4 = second.last4Digits?.takeIf { it.length == 4 }
        if (firstLast4 != null && secondLast4 != null && firstLast4 != secondLast4) return null

        val firstMerchant = normalizeMerchant(first.merchant)
        val secondMerchant = normalizeMerchant(second.merchant)
        val last4Match = firstLast4 != null && secondLast4 != null && firstLast4 == secondLast4
        val merchantMatch = firstMerchant != null && secondMerchant != null && firstMerchant == secondMerchant
        val sameSourceWindow = first.source == second.source &&
            abs(first.occurredAt - second.occurredAt) <= SAME_SOURCE_WINDOW_MILLIS

        if (first.source == FinancialEvidenceSource.MANUAL_TRANSACTION ||
            second.source == FinancialEvidenceSource.MANUAL_TRANSACTION
        ) {
            return PairCompatibility(
                strong = false,
                reason = "Coincide con un movimiento manual reciente; no se fusiona automáticamente."
            )
        }

        return if (last4Match || merchantMatch || sameSourceWindow) {
            PairCompatibility(
                strong = true,
                reason = buildString {
                    append("Monto, dirección y hora compatibles")
                    if (last4Match) append("; tarjeta terminada en $firstLast4")
                    if (merchantMatch) append("; comercio normalizado coincidente")
                    if (sameSourceWindow) append("; aviso duplicado de la misma fuente")
                    append('.')
                }
            )
        } else {
            PairCompatibility(
                strong = false,
                reason = "Monto y hora coinciden, pero falta confirmar tarjeta o comercio."
            )
        }
    }

    private fun matchingWindow(
        first: FinancialEventEvidence,
        second: FinancialEventEvidence
    ): Long? {
        val firstSource = first.source
        val secondSource = second.source

        return when {
            firstSource == FinancialEvidenceSource.MANUAL_TRANSACTION ||
                secondSource == FinancialEvidenceSource.MANUAL_TRANSACTION -> MANUAL_WINDOW_MILLIS

            // Mismo canal: ventana corta, salvo que sean las dos mitades de un mismo aviso
            // (el banco autoriza primero y confirma despues, y eso si tarda horas).
            firstSource == secondSource ->
                if (isTwoStageReport(first, second)) EMAIL_WINDOW_MILLIS else SAME_SOURCE_WINDOW_MILLIS

            setOf(firstSource, secondSource) == setOf(
                FinancialEvidenceSource.EMAIL_GMAIL,
                FinancialEvidenceSource.EMAIL_MICROSOFT
            ) -> EMAIL_WINDOW_MILLIS

            (firstSource == FinancialEvidenceSource.BANK_NOTIFICATION && secondSource.isEmail()) ||
                (secondSource == FinancialEvidenceSource.BANK_NOTIFICATION && firstSource.isEmail()) ->
                PUSH_EMAIL_WINDOW_MILLIS

            else -> null
        }
    }

    /** La autorizacion y el recibo de una misma compra, que el banco manda por separado. */
    private fun isTwoStageReport(
        first: FinancialEventEvidence,
        second: FinancialEventEvidence
    ): Boolean {
        val firstType = first.eventType ?: return false
        val secondType = second.eventType ?: return false

        return firstType != secondType && firstType in gatewayPair && secondType in gatewayPair
    }

    fun normalizeCurrency(currency: String?): String {
        if (currency.isNullOrBlank()) return BASE_CURRENCY
        val norm = currency.trim().uppercase(Locale.ROOT)
        if (norm in setOf("DOP", "RD$", "RD", "RD $", "DOP$", "R$")) return "DOP"
        if (norm in setOf("USD", "US$", "$")) return "USD"
        return norm
    }

    private fun comparableAmounts(
        first: FinancialEventEvidence,
        second: FinancialEventEvidence
    ): Pair<Long, Long>? {
        val firstCurrency = normalizeCurrency(first.currency ?: first.baseCurrency)
        val secondCurrency = normalizeCurrency(second.currency ?: second.baseCurrency)
        if (firstCurrency == secondCurrency) {
            val amount1 = first.amountMinor ?: first.baseAmountMinor
            val amount2 = second.amountMinor ?: second.baseAmountMinor
            if (amount1 != null && amount2 != null) {
                return amount1 to amount2
            }
        }
        val firstBaseCurrency = first.baseCurrency?.uppercase(Locale.ROOT)
        val secondBaseCurrency = second.baseCurrency?.uppercase(Locale.ROOT)
        if (firstBaseCurrency == BASE_CURRENCY && secondBaseCurrency == BASE_CURRENCY &&
            first.baseAmountMinor != null && second.baseAmountMinor != null
        ) {
            return first.baseAmountMinor to second.baseAmountMinor
        }
        return null
    }

    private fun directionsCompatible(dir1: String, dir2: String): Boolean {
        if (dir1.isBlank() || dir2.isBlank()) return true
        val d1 = dir1.lowercase(Locale.ROOT)
        val d2 = dir2.lowercase(Locale.ROOT)
        if (d1 == d2) return true
        val expenseSet = setOf("expense", "debit", "out", "egreso", "gasto", "compra", "consumo")
        val incomeSet = setOf("income", "credit", "in", "ingreso", "deposito", "abono")
        return (d1 in expenseSet && d2 in expenseSet) || (d1 in incomeSet && d2 in incomeSet)
    }

    private fun eventsCompatible(first: String?, second: String?): Boolean {
        if (first == null || second == null) return true
        if (first == second) return true
        return first in gatewayPair && second in gatewayPair
    }

    private fun canonicalScore(evidence: FinancialEventEvidence): Int =
        (when (evidence.source) {
            FinancialEvidenceSource.MANUAL_TRANSACTION -> 400
            FinancialEvidenceSource.BANK_NOTIFICATION -> 300
            FinancialEvidenceSource.EMAIL_GMAIL,
            FinancialEvidenceSource.EMAIL_MICROSOFT -> 200
        }) + (if (evidence.currency.equals(BASE_CURRENCY, ignoreCase = true)) 20 else 0) +
            (if (evidence.last4Digits != null) 5 else 0) +
            (if (evidence.merchant != null) 2 else 0)

    private fun normalizeMerchant(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val ascii = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
        return ascii.replace(Regex("[^a-z0-9]+"), " ").trim().takeIf(String::isNotEmpty)
    }

    private fun FinancialEvidenceSource.isEmail(): Boolean =
        this == FinancialEvidenceSource.EMAIL_GMAIL || this == FinancialEvidenceSource.EMAIL_MICROSOFT

    private data class PairCompatibility(val strong: Boolean, val reason: String)
}
