package com.bsolutions.wallet.presentation.common

/**
 * Las ocho piezas de la fila trasera, hundidas en el agua de la tarjeta.
 *
 * Son mas pesadas que el liquido, asi que se van al fondo y ruedan hacia el lado bajo
 * cuando el telefono se inclina. No flotan: eso las haria parecer globos.
 *
 * Se dibujan con los glifos de ajedrez de Unicode y no con trazados a mano: a este
 * tamano un contorno propio sale tosco, y la tipografia ya trae la silueta bien
 * resuelta a cualquier resolucion.
 */
internal class ChessPiece(
    val glyph: String,
    /** Fraccion del ancho de la tarjeta. */
    var x: Float,
    /** Fraccion del alto. */
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

/** Torre, caballo, alfil, dama, rey, alfil, caballo, torre. */
internal val CHESS_BACK_RANK = listOf("♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜")

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
    margin: Float
) {
    val gravity = 1.6f
    val drag = 2.6f
    val bounce = 0.25f

    for (piece in pieces) {
        piece.vx += gx * gravity * dt
        piece.vy += gy * gravity * dt
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

    separate(pieces, margin)
}

/**
 * Las aparta cuando se montan una encima de otra.
 *
 * Sin esto todas caen al mismo punto bajo y quedan superpuestas en un borron negro, que
 * es justo lo contrario de verse definidas.
 */
private fun separate(pieces: List<ChessPiece>, margin: Float) {
    val minimum = margin * 1.7f
    for (i in pieces.indices) {
        for (j in i + 1 until pieces.size) {
            val a = pieces[i]
            val b = pieces[j]
            val dx = b.x - a.x
            val dy = (b.y - a.y) * 0.5f // el alto pesa menos: se apilan algo
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance >= minimum || distance == 0f) continue
            val push = (minimum - distance) / 2f
            val nx = dx / distance
            val ny = dy / distance
            a.x -= nx * push
            b.x += nx * push
            a.y -= ny * push
            b.y += ny * push
        }
    }
}

/** Reparte las piezas a lo ancho del fondo, como salen de la caja. */
internal fun initialChessPieces(): List<ChessPiece> = CHESS_BACK_RANK.mapIndexed { index, glyph ->
    ChessPiece(
        glyph = glyph,
        x = 0.10f + index * 0.114f,
        y = 0.82f
    )
}
