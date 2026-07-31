package com.bsolutions.wallet.core.common

import com.bsolutions.wallet.domain.model.TRANSFER_CATEGORY_ID
import com.bsolutions.wallet.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Una transferencia se guarda como dos movimientos porque el saldo de las dos cuentas
 * tiene que moverse, pero en la lista de registros eso salia como el mismo importe dos
 * veces, uno en verde y otro en rojo.
 */
class TransfersTest {

    @Test
    fun `a transfer shows up once, as the money leaving`() {
        val rows = collapseTransferLegs(listOf(salida, entrada, gasto))

        assertEquals(listOf("tr1-out", "gasto1"), rows.map { it.id })
    }

    @Test
    fun `the incoming leg survives when its pair is not in the list`() {
        // Filtrando por la cuenta que recibio, la salida no esta: si tambien se escondiera
        // la entrada, en esa cuenta el dinero llegaria de la nada.
        val rows = collapseTransferLegs(listOf(entrada, gasto))

        assertEquals(listOf("tr1-in", "gasto1"), rows.map { it.id })
    }

    @Test
    fun `two different transfers each keep their own row`() {
        val otraSalida = salida.copy(id = "tr2-out")
        val otraEntrada = entrada.copy(id = "tr2-in")

        val rows = collapseTransferLegs(listOf(salida, entrada, otraSalida, otraEntrada))

        assertEquals(listOf("tr1-out", "tr2-out"), rows.map { it.id })
    }

    @Test
    fun `a list without transfers is left exactly as it was`() {
        val rows = listOf(gasto, gasto.copy(id = "gasto2"))

        assertEquals(rows, collapseTransferLegs(rows))
    }

    @Test
    fun `a normal movement whose note ends in -in is not mistaken for a transfer leg`() {
        // El corte es por el id, no por el texto: una nota puede decir cualquier cosa.
        val cena = gasto.copy(id = "abc123", note = "Cena con Robin")

        assertFalse(isTransferLeg(cena))
        assertTrue(isTransferLeg(salida))
        assertTrue(isTransferLeg(entrada))
    }

    private val salida = Transaction(
        id = "tr1-out",
        accountId = "cuenta_ahorro",
        amount = 378_450,
        type = "EXPENSE",
        categoryId = TRANSFER_CATEGORY_ID,
        date = 1_000L,
        note = "Transferencia Ahorro → Tarjeta",
        currency = "DOP"
    )

    private val entrada = salida.copy(
        id = "tr1-in",
        accountId = "cuenta_tarjeta",
        type = "INCOME"
    )

    private val gasto = Transaction(
        id = "gasto1",
        accountId = "cuenta_ahorro",
        amount = 50_000,
        type = "EXPENSE",
        categoryId = "cat_comida",
        date = 900L,
        note = "Colmado",
        currency = "DOP"
    )
}
