package com.bsolutions.wallet.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * El agua se dibuja en un marco propio que se gira hasta que su "abajo" coincide con la
 * gravedad real. Si el giro sale del lado contrario, el liquido se va a la izquierda
 * cuando el telefono se inclina a la derecha.
 *
 * La prueba no comprueba el numero de grados: aplica el giro al vector "abajo" del marco
 * y verifica donde acaba apuntando. Asi mide lo que de verdad importa, y no se puede
 * hacer pasar simplemente copiando la formula.
 */
class WaterSurfaceAngleTest {

    @Test
    fun `tilting right sends the water to the right`() {
        // Telefono inclinado a la derecha: la gravedad cae hacia abajo y a la derecha.
        val down = rotatedDown(surfaceAngleFor(gravityX = 0.6f, gravityY = 0.8f))

        assertTrue("el fondo del vaso deberia quedar a la derecha", down.first > 0f)
        assertTrue("y seguir estando hacia abajo", down.second > 0f)
    }

    @Test
    fun `tilting left sends the water to the left`() {
        val down = rotatedDown(surfaceAngleFor(gravityX = -0.6f, gravityY = 0.8f))

        assertTrue("el fondo del vaso deberia quedar a la izquierda", down.first < 0f)
        assertTrue("y seguir estando hacia abajo", down.second > 0f)
    }

    @Test
    fun `the rotated bottom always lands on the gravity direction`() {
        // Vuelta completa: en cualquier angulo el marco girado tiene que apuntar donde
        // apunta la gravedad. Un signo cambiado falla en la mitad de estas posiciones.
        for (degrees in 0 until 360 step 15) {
            val radians = Math.toRadians(degrees.toDouble())
            val gx = sin(radians).toFloat()
            val gy = cos(radians).toFloat()

            val down = rotatedDown(surfaceAngleFor(gx, gy))

            assertEquals("gravedad a $degrees grados, eje X", gx, down.first, 0.001f)
            assertEquals("gravedad a $degrees grados, eje Y", gy, down.second, 0.001f)
        }
    }

    @Test
    fun `upright water is not rotated at all`() {
        assertEquals(0f, surfaceAngleFor(gravityX = 0f, gravityY = 1f), 0.001f)
    }

    /**
     * Hacia donde acaba apuntando el "abajo" del marco tras girarlo [degrees] grados.
     *
     * Reproduce lo que hace `rotate` de Compose: giro horario sobre un lienzo con el eje Y
     * hacia abajo, que sobre la matriz estandar lleva (x,y) a (x·cos - y·sen, x·sen + y·cos).
     */
    private fun rotatedDown(degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        val localX = 0f
        val localY = 1f

        return Pair(
            (localX * cos(radians) - localY * sin(radians)).toFloat(),
            (localX * sin(radians) + localY * cos(radians)).toFloat()
        )
    }
}
