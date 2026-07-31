package com.bsolutions.wallet.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Cambia el dia de un movimiento conservando su hora.
 *
 * El selector de fecha devuelve el dia a medianoche UTC, sin hora. Si se guardara tal cual,
 * todos los movimientos corregidos caerian a la misma hora y el orden dentro del dia se
 * perderia: en la lista se barajan solos porque se ordena por fecha. Conservar la hora
 * original mantiene cada uno en su sitio.
 *
 * [pickedDateMillis] es lo que devuelve el selector; [originalMillis], el instante que ya
 * tenia el movimiento.
 */
fun withDateKeepingTime(
    originalMillis: Long,
    pickedDateMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val pickedDay = Instant.ofEpochMilli(pickedDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val originalTime = Instant.ofEpochMilli(originalMillis).atZone(zoneId).toLocalTime()

    return pickedDay.atTime(originalTime).atZone(zoneId).toInstant().toEpochMilli()
}
