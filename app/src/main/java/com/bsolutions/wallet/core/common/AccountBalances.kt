package com.bsolutions.wallet.core.common

import com.bsolutions.wallet.domain.model.Account

/**
 * Consolidación de saldos respetando la divisa de cada cuenta.
 *
 * No convertimos entre monedas (no hay tasas de cambio en el MVP): el saldo
 * principal solo agrega las cuentas en la moneda base (RD$/DOP) y las demás
 * divisas se muestran como subtotales aparte. Así el Balance Total deja de
 * sumar euros, dólares y pesos como si fueran lo mismo.
 */
object AccountBalances {

    /** Saldo total de las cuentas en la moneda base (DOP). */
    fun primaryTotal(accounts: List<Account>): Long =
        accounts.filter { it.currency == MoneyFormat.DEFAULT_CURRENCY }
            .sumOf { it.balance }

    /**
     * Subtotales por cada divisa distinta de la base, ordenados por código.
     *
     * Las divisas cuyo subtotal queda en cero no se listan: una cuenta en dólares
     * vacía no es información, y "Además: US$0.00" bajo el Balance Total se lee
     * como un fallo. Vuelve a aparecer sola en cuanto la cuenta tenga saldo.
     */
    fun foreignTotals(accounts: List<Account>): Map<String, Long> =
        accounts.filter { it.currency != MoneyFormat.DEFAULT_CURRENCY }
            .groupBy { it.currency }
            .mapValues { (_, accs) -> accs.sumOf { it.balance } }
            .filterValues { it != 0L }
            .toSortedMap()

    /**
     * Línea legible con los subtotales en otras divisas, p. ej.
     * "US$2,019.17 · €2,009.70". Null si todas las cuentas están en DOP.
     */
    fun foreignSubtitle(accounts: List<Account>): String? {
        val foreign = foreignTotals(accounts)
        if (foreign.isEmpty()) return null
        return foreign.entries.joinToString(" · ") { (currency, amount) ->
            MoneyFormat.format(amount, currency)
        }
    }
}
