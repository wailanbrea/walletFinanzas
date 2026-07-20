package com.bsolutions.wallet.core.common

import java.math.BigDecimal
import java.math.RoundingMode

/** Convierte importes ingresados por personas o archivos a unidades menores. */
object MoneyParser {
    /** No usa Double: los importes financieros se representan como Long. */
    fun parseMinorUnits(value: String): Long? {
        var normalizedValue = value.trim()
        if (normalizedValue.isEmpty()) return null

        var isNegative = false
        if (normalizedValue.startsWith("(") && normalizedValue.endsWith(")")) {
            isNegative = true
            normalizedValue = normalizedValue.substring(1, normalizedValue.length - 1)
        }

        normalizedValue = normalizedValue.replace(Regex("[^0-9.,-]"), "")
        if (normalizedValue.startsWith("-")) {
            isNegative = true
            normalizedValue = normalizedValue.drop(1)
        }
        if (normalizedValue.isEmpty() || normalizedValue.contains('-')) return null

        val lastComma = normalizedValue.lastIndexOf(',')
        val lastDot = normalizedValue.lastIndexOf('.')
        val decimalNormalized = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) normalizedValue.replace(".", "").replace(',', '.')
                else normalizedValue.replace(",", "")
            lastComma >= 0 ->
                if (normalizedValue.length - lastComma - 1 == 3) normalizedValue.replace(",", "")
                else normalizedValue.replace(',', '.')
            lastDot >= 0 && normalizedValue.count { it == '.' } > 1 -> normalizedValue.replace(".", "")
            else -> normalizedValue
        }

        return try {
            val minorUnits = BigDecimal(decimalNormalized)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
            if (isNegative) -minorUnits else minorUnits
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }
}
