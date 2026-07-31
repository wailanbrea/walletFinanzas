package com.bsolutions.wallet.core.common

import com.bsolutions.wallet.domain.model.Transaction

/**
 * Una transferencia se guarda como dos movimientos, uno en cada cuenta.
 *
 * Tiene que ser asi: el servidor no conoce el concepto de transferencia, solo movimientos
 * con importe sobre una cuenta, y el saldo de las dos cuentas tiene que moverse. Pero en
 * la lista de registros eso salia como dos lineas del mismo importe, una en positivo y
 * otra en negativo, y se leia como si el dinero se hubiera gastado y ganado a la vez.
 *
 * Aqui se esconde la pata de entrada y se deja la de salida, que ya lleva en la nota de
 * donde a donde fue. No se borra nada: el movimiento sigue existiendo y la cuenta que
 * recibio sigue teniendo su linea cuando se mira esa cuenta por separado.
 */
private const val TRANSFER_OUT_SUFFIX = "-out"
private const val TRANSFER_IN_SUFFIX = "-in"

/** El id compartido por las dos patas, o null si el movimiento no es una de ellas. */
private fun transferPairId(transaction: Transaction): String? = when {
    transaction.id.endsWith(TRANSFER_OUT_SUFFIX) -> transaction.id.removeSuffix(TRANSFER_OUT_SUFFIX)
    transaction.id.endsWith(TRANSFER_IN_SUFFIX) -> transaction.id.removeSuffix(TRANSFER_IN_SUFFIX)
    else -> null
}

/**
 * Deja una sola linea por transferencia.
 *
 * Solo se esconde la entrada cuando su pareja esta en la misma lista. Si se filtra por la
 * cuenta que recibio, la salida no aparece y la entrada tiene que seguir viendose: si no,
 * en esa cuenta el dinero llegaria de la nada.
 */
fun collapseTransferLegs(transactions: List<Transaction>): List<Transaction> {
    val outgoing = transactions.mapNotNullTo(mutableSetOf()) { transaction ->
        transferPairId(transaction)?.takeIf { transaction.id.endsWith(TRANSFER_OUT_SUFFIX) }
    }
    if (outgoing.isEmpty()) return transactions

    return transactions.filterNot { transaction ->
        transaction.id.endsWith(TRANSFER_IN_SUFFIX) &&
            transaction.id.removeSuffix(TRANSFER_IN_SUFFIX) in outgoing
    }
}

/**
 * Si el movimiento es una pata de una transferencia.
 *
 * Se usa para pintarlo sin signo: una transferencia no es ni ingreso ni gasto, el dinero
 * sigue siendo tuyo y solo cambio de sitio. Mostrarlo en rojo como un gasto hacia pensar
 * que se habia perdido.
 */
fun isTransferLeg(transaction: Transaction): Boolean = transferPairId(transaction) != null
