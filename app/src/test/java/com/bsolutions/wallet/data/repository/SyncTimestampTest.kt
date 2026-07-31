package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.data.repository.SyncRepository.Companion.isoUtc
import com.bsolutions.wallet.data.repository.SyncRepository.Companion.parseIsoOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La fecha que manda el servidor es la fecha en que ocurrio el movimiento. Si no se lee
 * bien, la lista de recientes deja de tener sentido: los movimientos se ordenan por fecha
 * y todos acaban con la misma.
 */
class SyncTimestampTest {

    /** Lo que devuelve `toISOString()` de Carbon, que es lo que responde el backend. */
    private val laravelFormat = "2026-07-31T05:12:34.000000Z"

    @Test
    fun `the format the server actually sends is understood`() {
        // 2026-07-31T05:12:34Z en milisegundos desde epoch.
        assertEquals(1785474754000L, parseIsoOrNull(laravelFormat))
    }

    @Test
    fun `seconds without a fraction still work`() {
        assertEquals(1785474754000L, parseIsoOrNull("2026-07-31T05:12:34Z"))
    }

    @Test
    fun `a date with an offset instead of Z is the same instant`() {
        assertEquals(
            parseIsoOrNull("2026-07-31T05:12:34Z"),
            parseIsoOrNull("2026-07-31T01:12:34-04:00")
        )
    }

    @Test
    fun `what we send is what we can read back`() {
        val original = 1785474754000L

        assertEquals(original, parseIsoOrNull(isoUtc(original)))
    }

    @Test
    fun `an unreadable date is reported as such and not as now`() {
        // Devolver la hora actual aqui era el fallo: cada sincronizacion le ponia la hora
        // del momento a todos los movimientos y la lista se barajaba sola.
        assertNull(parseIsoOrNull(null))
        assertNull(parseIsoOrNull(""))
        assertNull(parseIsoOrNull("ayer por la tarde"))
    }
}
