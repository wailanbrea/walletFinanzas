package com.bsolutions.wallet.core.common

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parser CSV tolerante para estados de cuenta bancarios.
 * Soporta separador `,` o `;` (auto-detectado), campos entre comillas,
 * varios formatos de fecha y montos con coma o punto decimal.
 */
object CsvParser {

    data class CsvData(
        val headers: List<String>,
        val rows: List<List<String>>
    )

    fun parse(content: String): CsvData {
        val lines = content.split("\r\n", "\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return CsvData(emptyList(), emptyList())

        val delimiter = detectDelimiter(lines.first())
        val parsed = lines.map { parseLine(it, delimiter) }

        return CsvData(
            headers = parsed.first().map { it.trim() },
            rows = parsed.drop(1)
        )
    }

    private fun detectDelimiter(headerLine: String): Char {
        val commas = headerLine.count { it == ',' }
        val semicolons = headerLine.count { it == ';' }
        return if (semicolons > commas) ';' else ','
    }

    /** Divide una línea respetando campos entre comillas ("a,b",c). */
    private fun parseLine(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"'); i++ // comilla escapada
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    fields.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString().trim())
        return fields
    }

    private val dateFormats = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yy")

    /** Intenta parsear la fecha con los formatos comunes de bancos; null si falla. */
    fun parseDate(value: String): Long? {
        val v = value.trim()
        if (v.isEmpty()) return null
        for (fmt in dateFormats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }
                return sdf.parse(v)?.time ?: continue
            } catch (_: Exception) {
                // probar el siguiente formato
            }
        }
        return null
    }

    /**
     * Parsea un monto a unidades menores (centavos). Tolera "RD$1,234.56",
     * "1.234,56", "-500", "(500)" (negativo contable). Null si no es numérico.
     */
    fun parseAmount(value: String): Long? = MoneyParser.parseMinorUnits(value)
}
