package com.bsolutions.wallet.presentation.common

/**
 * Un caballo de ajedrez suelto en el agua de la tarjeta.
 *
 * Es mas pesado que el liquido, asi que se va al fondo y rueda hacia el lado bajo
 * cuando el telefono se inclina. No flota: eso lo haria parecer un globo.
 *
 * Se dibuja con el glifo de ajedrez de Unicode y no con un trazado a mano: a este tamano
 * un contorno propio sale tosco, y la tipografia trae la silueta bien resuelta.
 */
internal class ChessPiece(
    val glyph: String,
    /** Desfase de la corriente que lo mece. */
    val sway: Float,
    /** Fraccion del ancho de la tarjeta. */
    var x: Float,
    /** Fraccion del alto. */
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

/** Un solo caballo: suelto en el vaso, no un tablero volcado dentro. */
internal const val CHESS_KNIGHT = "♞"

/**
 * Mueve las piezas por el fondo del vaso.
 *
 * [gx] y [gy] son la direccion de la gravedad en coordenadas de la tarjeta. El rozamiento
 * es alto a proposito: dentro del agua nada se desliza libre, y sin el las piezas
 * patinarian como sobre hielo.
 */
internal fun advanceChessPieces(
    pieces: List<ChessPiece>,
    gx: Float,
    gy: Float,
    dt: Float,
    /** Mitad del tamaño de una pieza, en fracción, para que no se salgan por el borde. */
    margin: Float,
    /** Segundos desde que se abrió la pantalla, para la deriva continua. */
    elapsed: Float,
    /** Cuánto se está agitando el agua ahora mismo, de 0 a 1. */
    stirred: Float
) {
    val gravity = 1.6f
    val drag = 2.6f
    val bounce = 0.25f

    for (piece in pieces) {
        piece.vx += gx * gravity * dt
        piece.vy += gy * gravity * dt

        // Nada suelto en un liquido se queda del todo quieto: siempre hay corriente. Cada
        // pieza tiene su propio ritmo, y con el agua agitada la corriente arrecia.
        val current = 0.05f + stirred * 0.55f
        piece.vx += kotlin.math.sin(elapsed * 0.9f + piece.sway) * current * dt
        piece.vy += kotlin.math.cos(elapsed * 0.7f + piece.sway * 1.6f) * current * dt

        // El agua frena: la velocidad se pierde sola en vez de acumularse.
        piece.vx -= piece.vx * drag * dt
        piece.vy -= piece.vy * drag * dt
        piece.x += piece.vx * dt
        piece.y += piece.vy * dt

        // Paredes del vaso. Rebotan poco: un choque bajo el agua es sordo.
        if (piece.x < margin) {
            piece.x = margin
            piece.vx = -piece.vx * bounce
        } else if (piece.x > 1f - margin) {
            piece.x = 1f - margin
            piece.vx = -piece.vx * bounce
        }
        val floor = 1f - margin
        if (piece.y > floor) {
            piece.y = floor
            piece.vy = -piece.vy * bounce
        } else if (piece.y < margin) {
            piece.y = margin
            piece.vy = -piece.vy * bounce
        }
    }
}

/** El caballo empieza centrado y ya hundido. */
internal fun initialChessPieces(): List<ChessPiece> = listOf(
    ChessPiece(glyph = CHESS_KNIGHT, sway = 0f, x = 0.5f, y = 0.8f)
)
