package com.bsolutions.wallet.core.deduplication

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprar dos veces lo mismo en la misma tienda no es un duplicado.
 *
 * El caso real: RD$210.05 en el mismo courier el 4 de agosto y otra vez el 6. La segunda
 * se marco como duplicado de la primera y desaparecio. Ese error no se ve por ningun lado:
 * el dinero salio de la cuenta y el movimiento no esta en ninguna lista.
 */
class RepeatPurchaseTest {

    private val cuatroDeAgosto = 1_785_888_180_000L
    private val unMinuto = 60L * 1_000L
    private val unaHora = 60L * unMinuto
    private val unDia = 24L * unaHora

    @Test
    fun `the same purchase two days later is not a duplicate`() {
        val elCuatro = compra("ev-4", cuatroDeAgosto)
        val elSeis = compra("ev-6", cuatroDeAgosto + 2 * unDia)

        val result = FinancialEventMatcher.match(elSeis, listOf(elCuatro))

        assertTrue(
            "dos compras con dos dias de diferencia no pueden fusionarse, era $result",
            result is FinancialMatchResult.NoMatch
        )
    }

    @Test
    fun `two purchases hours apart on the same day are not a duplicate either`() {
        val manana = compra("ev-am", cuatroDeAgosto)
        val tarde = compra("ev-pm", cuatroDeAgosto + 5 * unaHora)

        val result = FinancialEventMatcher.match(tarde, listOf(manana))

        assertTrue(
            "cinco horas separan dos compras, no dos avisos de una, era $result",
            result is FinancialMatchResult.NoMatch
        )
    }

    @Test
    fun `the same notice arriving twice is still merged`() {
        // Lo que si es un duplicado de verdad: el mismo mensaje entrando dos veces. Eso
        // pasa en segundos, no en horas.
        val primero = compra("ev-1", cuatroDeAgosto)
        val repetido = compra("ev-2", cuatroDeAgosto + 30_000L)

        val result = FinancialEventMatcher.match(repetido, listOf(primero))

        assertTrue(
            "el mismo aviso repetido si debe fusionarse, era $result",
            result is FinancialMatchResult.StrongMatch
        )
    }

    @Test
    fun `the authorisation and the receipt of one purchase still merge hours apart`() {
        // El banco autoriza primero y confirma despues, y entre las dos pasan horas. Son
        // dos mitades de una compra, no dos compras.
        val autorizacion = compra("ev-auth", cuatroDeAgosto, eventType = "CARD_PURCHASE_APPROVED")
        val recibo = compra("ev-receipt", cuatroDeAgosto + 4 * unaHora, eventType = "RECEIPT_CONFIRMED")

        val result = FinancialEventMatcher.match(recibo, listOf(autorizacion))

        assertTrue(
            "autorizacion y recibo son el mismo evento, era $result",
            result is FinancialMatchResult.StrongMatch
        )
    }

    @Test
    fun `an email and a push about the same purchase still merge`() {
        // Dos canales contando la misma compra: eso es lo que la fusion existe para juntar.
        val correo = compra("ev-mail", cuatroDeAgosto, source = FinancialEvidenceSource.EMAIL_MICROSOFT)
        val push = compra("ev-push", cuatroDeAgosto + unMinuto, source = FinancialEvidenceSource.BANK_NOTIFICATION)

        val result = FinancialEventMatcher.match(push, listOf(correo))

        assertTrue(
            "correo y notificacion de la misma compra deben fusionarse, era $result",
            result is FinancialMatchResult.StrongMatch
        )
    }

    private fun compra(
        id: String,
        occurredAt: Long,
        source: FinancialEvidenceSource = FinancialEvidenceSource.EMAIL_MICROSOFT,
        eventType: String? = "CARD_PURCHASE_APPROVED"
    ) = FinancialEventEvidence(
        id = id,
        source = source,
        occurredAt = occurredAt,
        direction = "expense",
        amountMinor = 21_005L,
        currency = "DOP",
        merchant = "BOXPAQ ENS ESPAILLAT",
        last4Digits = "8324",
        eventType = eventType
    )
}
