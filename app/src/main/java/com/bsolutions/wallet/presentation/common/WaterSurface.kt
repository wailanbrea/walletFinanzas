package com.bsolutions.wallet.presentation.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
    val water = rememberWaterMotion()  // se lee dentro del Canvas, no aquí
    // El nivel se anima para que al cambiar de meta el agua suba en vez de saltar.
    val filled by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "nivel"
    )

    Canvas(modifier = modifier) {
        if (filled <= 0f) return@Canvas
        val surfaceY = size.height * (1f - filled)
        // Desnivel entre un extremo y el otro. Va contra el ancho y no contra el alto:
        // la superficie cruza la tarjeta a lo largo, así que un desnivel medido en la
        // altura quedaba en unos pocos píxeles y no se veía nada.
        val slope = water.tilt.value * size.width * 0.22f
        // La ola solo existe mientras quede chapoteo: con el teléfono quieto la
        // superficie es una línea recta y no se redibuja nada.
        val energy = water.energy.value
        val phase = water.phase.value

        drawWave(
            surfaceY = surfaceY,
            slope = slope * 0.6f,
            phase = phase * 0.7f,
            amplitude = size.height * 0.045f * energy,
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
            amplitude = size.height * 0.065f * energy,
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

/** Estado del líquido: hacia dónde se inclina y cuánto chapotea. */
private class WaterMotion {
    val tilt = mutableFloatStateOf(0f)
    /** De 0 a 1: cuánto queda del chapoteo. En 0 la superficie es una línea recta. */
    val energy = mutableFloatStateOf(0f)
    val phase = mutableFloatStateOf(0f)
}

/**
 * Sigue el movimiento del teléfono.
 *
 * El agua no se mueve sola: con el teléfono quieto la superficie queda plana y quita.
 * Solo al moverlo aparece el chapoteo, que luego se apaga como se apaga el de un vaso.
 *
 * Todo se calcula en el propio evento del sensor, sin animación de fondo. Un
 * acelerómetro reporta también en reposo, así que basta con mirar cuánto cambia: si no
 * cambia, la energía cae a cero y deja de dibujarse movimiento.
 *
 * El listener se suelta al salir de la pantalla: un sensor que sigue escuchando cuando
 * no se ve nada gasta batería sin que el usuario tenga forma de notarlo.
 */
@Composable
private fun rememberWaterMotion(): WaterMotion {
    val water = remember { WaterMotion() }
    // En las vistas previas y en los tests no hay sensor: el agua se queda quieta.
    if (LocalInspectionMode.current) return water
    val context = LocalContext.current

    DisposableEffect(context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // El eje X es el giro lateral. Se amplifica porque en la mano el teléfono
                // se inclina poco: nadie lo pone de costado para mirar el saldo.
                val target = (event.values[0] / 4.5f).coerceIn(-1f, 1f)
                val change = target - water.tilt.floatValue
                water.tilt.floatValue += change * 0.35f

                // Lo que agita el agua es el cambio, no la posición: sostenerlo inclinado
                // y quieto deja la superficie en diagonal pero sin olas.
                val stirred = (kotlin.math.abs(change) * 14f).coerceAtMost(1f)
                water.energy.floatValue = maxOf(water.energy.floatValue * 0.94f, stirred)
                if (water.energy.floatValue < 0.01f) {
                    water.energy.floatValue = 0f
                } else {
                    water.phase.floatValue += 0.25f + water.energy.floatValue * 0.5f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null) {
            sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensors?.unregisterListener(listener) }
    }

    return water
}

/**
 * Qué meta representa el agua.
 *
 * Sin metas es decorativa y llena hasta la mitad. Con metas se elige la más pequeña,
 * que es la que está más cerca de cumplirse y la que más motiva ver subir.
 */
fun waterLevelFor(goalProgress: Float?): Float = goalProgress?.coerceIn(0f, 1f) ?: 0.5f
