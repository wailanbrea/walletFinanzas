package com.bsolutions.wallet.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas

/** Segmento del gráfico de dona: valor absoluto + color. */
data class DonutSegment(
    val value: Long,
    val color: Color
)

/**
 * Gráfico de dona real dibujado con Canvas, proporcional a los valores.
 * Muestra un anillo gris si no hay datos.
 */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    strokeWidth: Dp = 22.dp,
    content: @Composable () -> Unit = {}
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val total = segments.sumOf { it.value }.coerceAtLeast(0L)

    // Barrido animado: el anillo se "dibuja" al entrar
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val sweepFraction by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "donutSweep"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (total <= 0L) {
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f * sweepFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            } else {
                // Pequeño hueco entre segmentos cuando hay más de uno
                val gapAngle = if (segments.size > 1) 2f else 0f
                var startAngle = -90f
                segments.filter { it.value > 0 }.forEach { segment ->
                    val sweep = (segment.value.toFloat() / total) * 360f * sweepFraction
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle + gapAngle / 2,
                        sweepAngle = (sweep - gapAngle).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                    startAngle += sweep
                }
            }
        }
        content()
    }
}

/**
 * Progreso animado para barras: arranca en 0 y se desliza suavemente
 * hasta el valor real (también anima cambios posteriores).
 */
@Composable
fun animatedProgress(target: Float): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) target.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "barProgress"
    )
    return progress
}

/** Convierte un hex tipo "#RRGGBB" en Color de forma segura. */
fun parseHexColor(hex: String, fallback: Color): Color =
    try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
