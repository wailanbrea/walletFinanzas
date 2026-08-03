package com.bsolutions.wallet.core.deduplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialEventMatcherTest {
    @Test
    fun `gmail y microsoft se emparejan por monto comercio y ventana de 72 horas`() {
        val gmail = evidence(
            id = "gmail",
            source = FinancialEvidenceSource.EMAIL_GMAIL,
            occurredAt = 1_000,
            merchant = "Supermercado Bravo"
        )
        val microsoft = evidence(
            id = "microsoft",
            source = FinancialEvidenceSource.EMAIL_MICROSOFT,
            occurredAt = 1_000 + 48.hours,
            merchant = "SUPERMERCADO-BRAVO"
        )

        val result = FinancialEventMatcher.match(microsoft, listOf(gmail))

        assertTrue(result is FinancialMatchResult.StrongMatch)
        assertEquals("gmail", (result as FinancialMatchResult.StrongMatch).canonical.id)
    }

    @Test
    fun `push y correo fuera de seis horas no se fusionan`() {
        val email = evidence(source = FinancialEvidenceSource.EMAIL_GMAIL, occurredAt = 1_000)
        val push = evidence(
            id = "push",
            source = FinancialEvidenceSource.BANK_NOTIFICATION,
            occurredAt = 1_000 + 7.hours
        )

        assertSame(FinancialMatchResult.NoMatch, FinancialEventMatcher.match(push, listOf(email)))
    }

    @Test
    fun `ultimos cuatro conflictivos impiden fusion aunque monto y comercio coincidan`() {
        val email = evidence(source = FinancialEvidenceSource.EMAIL_GMAIL, last4 = "1234")
        val push = evidence(
            id = "push",
            source = FinancialEvidenceSource.BANK_NOTIFICATION,
            last4 = "9876"
        )

        assertSame(FinancialMatchResult.NoMatch, FinancialEventMatcher.match(push, listOf(email)))
    }

    @Test
    fun `monto solo produce posible duplicado y nunca fusion automatica`() {
        val email = evidence(
            source = FinancialEvidenceSource.EMAIL_GMAIL,
            merchant = null,
            last4 = null
        )
        val push = evidence(
            id = "push",
            source = FinancialEvidenceSource.BANK_NOTIFICATION,
            merchant = null,
            last4 = null
        )

        assertTrue(FinancialEventMatcher.match(push, listOf(email)) is FinancialMatchResult.PossibleDuplicate)
    }

    @Test
    fun `dos coincidencias fuertes son ambiguas`() {
        val first = evidence(id = "first", source = FinancialEvidenceSource.EMAIL_GMAIL)
        val second = evidence(
            id = "second",
            source = FinancialEvidenceSource.EMAIL_MICROSOFT,
            occurredAt = 2_000
        )
        val push = evidence(
            id = "push",
            source = FinancialEvidenceSource.BANK_NOTIFICATION,
            occurredAt = 1_500
        )

        assertTrue(
            FinancialEventMatcher.match(push, listOf(first, second)) is
                FinancialMatchResult.PossibleDuplicate
        )
    }

    @Test
    fun `conversion DOP admite diferencia de tres por ciento pero no mayor`() {
        val usd = evidence(
            source = FinancialEvidenceSource.EMAIL_GMAIL,
            amount = 1_000,
            currency = "USD",
            baseAmount = 6_100,
            baseCurrency = "DOP"
        )
        val within = evidence(
            id = "within",
            source = FinancialEvidenceSource.BANK_NOTIFICATION,
            amount = 6_250,
            baseAmount = 6_250
        )
        val outside = within.copy(id = "outside", amountMinor = 6_400, baseAmountMinor = 6_400)

        assertTrue(FinancialEventMatcher.amountsMatch(usd, within))
        assertTrue(!FinancialEventMatcher.amountsMatch(usd, outside))
    }

    @Test
    fun `notificacion bancaria gana como evidencia canonica`() {
        val email = evidence(source = FinancialEvidenceSource.EMAIL_GMAIL)
        val push = evidence(id = "push", source = FinancialEvidenceSource.BANK_NOTIFICATION)

        assertEquals(push, FinancialEventMatcher.preferredCanonical(email, push))
    }

    private fun evidence(
        id: String = "email",
        source: FinancialEvidenceSource,
        occurredAt: Long = 1_000,
        amount: Long = 10_000,
        currency: String = "DOP",
        baseAmount: Long? = amount,
        baseCurrency: String? = "DOP",
        merchant: String? = "Amazon",
        last4: String? = "1234"
    ) = FinancialEventEvidence(
        id = id,
        source = source,
        occurredAt = occurredAt,
        direction = "expense",
        amountMinor = amount,
        currency = currency,
        baseAmountMinor = baseAmount,
        baseCurrency = baseCurrency,
        merchant = merchant,
        last4Digits = last4,
        eventType = "CARD_PURCHASE_APPROVED"
    )

    private val Int.hours: Long get() = this * 60L * 60L * 1_000L
}
