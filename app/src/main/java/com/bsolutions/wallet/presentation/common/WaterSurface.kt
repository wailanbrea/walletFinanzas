package com.bsolutions.wallet.presentation.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.math.PI
import kotlin.math.sin

/**
 * Superficie de agua que se inclina con el teléfono.
 *
 * Se dibujan dos ondas desfasadas y no una: con una sola el movimiento se lee como una
 * bandera y no como líquido. La de atrás va más tenue y a otra velocidad, que es lo que
 * da la sensación de volumen.
 *
 * [level] es cuánto llena, de 0 a 1.
 */
@Composable
fun WaterSurface(
    level: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val tilt = rememberDeviceTilt()
    // El nivel se anima para que al cambiar de meta el agua suba en vez de saltar.
    val filled by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "nivel"
    )
    val waves = rememberInfiniteTransition(label = "olas")
    val phase by waves.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fase"
    )

    Canvas(modifier = modifier) {
        if (filled <= 0f) return@Canvas
        val surfaceY = size.height * (1f - filled)
        // La inclinación mueve un extremo respecto al otro: es lo que hace que el agua
        // parezca quedarse horizontal mientras el teléfono gira.
        val slope = tilt * size.height * 0.12f

        drawWave(
            surfaceY = surfaceY,
            slope = slope * 0.6f,
            phase = phase * 0.7f,
            amplitude = size.height * 0.035f,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.10f)),
                startY = surfaceY,
                endY = size.height
            )
        )
        drawWave(
            surfaceY = surfaceY,
            slope = slope,
            phase = phase,
            amplitude = size.height * 0.05f,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.40f), color.copy(alpha = 0.16f)),
                startY = surfaceY,
                endY = size.height
            )
        )
    }
}

/** Una onda rellena desde su superficie hasta abajo. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(
    surfaceY: Float,
    slope: Float,
    phase: Float,
    amplitude: Float,
    brush: Brush
) {
    val path = Path().apply {
        moveTo(0f, surfaceY - slope)
        // Doce tramos bastan para que la curva se vea suave a este tamaño.
        val steps = 12
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val x = size.width * t
            val y = surfaceY - slope + slope * 2f * t + sin(phase + t * 2f * PI.toFloat()) * amplitude
            lineTo(x, y)
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, brush)
}

/**
 * Inclinación lateral del teléfono, de -1 a 1.
 *
 * El listener se suelta al salir de la pantalla: un sensor que sigue escuchando cuando
 * no se ve nada gasta batería sin que el usuario tenga forma de notarlo.
 */
@Composable
private fun rememberDeviceTilt(): Float {
    // En las vistas previas y en los tests no hay sensor: el agua se queda quieta.
    if (LocalInspectionMode.current) return 0f
    val context = LocalContext.current
    var tilt by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // El eje X en m/s²; a 9.8 el teléfono está de lado. Se suaviza para que
                // el agua no tiemble con el pulso.
                val target = (event.values[0] / 9.8f).coerceIn(-1f, 1f)
                tilt += (target - tilt) * 0.12f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null) {
            sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensors?.unregisterListener(listener) }
    }

    return tilt
}

/**
 * Qué meta representa el agua.
 *
 * Sin metas es decorativa y llena hasta la mitad. Con metas se elige la más pequeña,
 * que es la que está más cerca de cumplirse y la que más motiva ver subir.
 */
fun waterLevelFor(goalProgress: Float?): Float = goalProgress?.coerceIn(0f, 1f) ?: 0.5f
