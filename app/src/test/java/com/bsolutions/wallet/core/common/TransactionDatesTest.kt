package com.bsolutions.wallet.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Corregir la fecha de un movimiento cambia el dia, nunca la hora.
 *
 * El selector devuelve el dia a medianoche UTC. Guardarlo tal cual dejaria todos los
 * movimientos corregidos a la misma hora, y como la lista se ordena por fecha, los de ese
 * dia se barajarian entre ellos.
 */
class TransactionDatesTest {

    private val santoDomingo = ZoneId.of("America/Santo_Domingo")

    @Test
    fun `the day changes and the time of day survives`() {
        val original = at(2026, 7, 31, 13, 45)
        val picked = dayAtUtcMidnight(2026, 7, 1)

        val moved = withDateKeepingTime(original, picked, santoDomingo)

        assertEquals(LocalDate.of(2026, 7, 1), localDate(moved))
        assertEquals("13:45", localTime(moved))
    }

    @Test
    fun `moving a movement to another month keeps its time`() {
        val original = at(2026, 7, 15, 6, 5)
        val picked = dayAtUtcMidnight(2026, 3, 28)

        val moved = withDateKeepingTime(original, picked, santoDomingo)

        assertEquals(LocalDate.of(2026, 3, 28), localDate(moved))
        assertEquals("06:05", localTime(moved))
    }

    @Test
    fun `picking the same day it already had changes nothing`() {
        val original = at(2026, 7, 31, 1, 48)
        val picked = dayAtUtcMidnight(2026, 7, 31)

        assertEquals(original, withDateKeepingTime(original, picked, santoDomingo))
    }

    @Test
    fun `a movement just before midnight does not slide into the next day`() {
        // A las 23:30 en Santo Domingo ya es el dia siguiente en UTC. Mezclar las dos
        // zonas movia el movimiento un dia entero sin que nadie lo pidiera.
        val original = at(2026, 7, 31, 23, 30)
        val picked = dayAtUtcMidnight(2026, 7, 10)

        val moved = withDateKeepingTime(original, picked, santoDomingo)

        assertEquals(LocalDate.of(2026, 7, 10), localDate(moved))
        assertEquals("23:30", localTime(moved))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(santoDomingo)
            .toInstant()
            .toEpochMilli()

    /** Lo que devuelve el selector de fecha: el dia a medianoche UTC. */
    private fun dayAtUtcMidnight(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(santoDomingo).toLocalDate()

    private fun localTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(santoDomingo).toLocalTime().toString()
}
