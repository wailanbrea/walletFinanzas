package com.bsolutions.wallet.core.common

import java.util.Locale

/**
 * Formato de moneda centralizado.
 *
 * Los montos se guardan en unidades menores (centavos). La moneda base de la app
 * es el peso dominicano; cuando llegue el soporte multi-moneda (CurrencyCode por
 * cuenta), este objeto es el único punto a modificar.
 */
object MoneyFormat {
    const val DEFAULT_CURRENCY = "DOP"

    private val symbols = mapOf(
        "DOP" to "RD$",
        "USD" to "US$",
        "EUR" to "€"
    )

    fun symbol(currency: String = DEFAULT_CURRENCY): String =
        symbols[currency] ?: "$currency "

    /** 1234567 -> "RD$12,345.67" */
    fun format(minorUnits: Long, currency: String = DEFAULT_CURRENCY): String =
        symbol(currency) + String.format(Locale.US, "%,.2f", minorUnits / 100.0)

    /** 1234567 -> "RD$12,346" (sin decimales, para espacios reducidos) */
    fun formatCompact(minorUnits: Long, currency: String = DEFAULT_CURRENCY): String =
        symbol(currency) + String.format(Locale.US, "%,.0f", minorUnits / 100.0)

    /** Con signo explícito: +RD$500.00 / -RD$500.00 según el tipo de movimiento. */
    fun formatSigned(minorUnits: Long, isIncome: Boolean, currency: String = DEFAULT_CURRENCY): String =
        (if (isIncome) "+" else "-") + format(minorUnits, currency)
}
