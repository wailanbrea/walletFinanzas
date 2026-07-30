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
    fun surfaceAt(t: Float): Float {
        // Dos senoidales de distinta longitud: una sola da una ondulación regular que se
        // nota artificial, y superpuestas nunca repiten el mismo perfil.
        val principal = sin(phase + t * 2f * PI.toFloat())
        val secundaria = sin(phase * 1.7f + t * 3.4f * PI.toFloat()) * 0.35f

        return surfaceY - slope + slope * 2f * t + (principal + secundaria) * amplitude
    }

    val path = Path().apply {
        moveTo(0f, surfaceAt(0f))
        // Curvas y no segmentos rectos: con tramos largos la superficie se ve poligonal,
        // y subir el número de rectas para disimularlo cuesta más que interpolar.
        val steps = 24
        for (i in 1..steps) {
            val previous = (i - 1) / steps.toFloat()
            val current = i / steps.toFloat()
            val middle = (previous + current) / 2f
            // El punto de control se sitúa de forma que la curva pase por el punto medio
            // real de la onda, y no por la cuerda entre extremos.
            val controlY = 2f * surfaceAt(middle) - (surfaceAt(previous) + surfaceAt(current)) / 2f
            quadraticTo(size.width * middle, controlY, size.width * current, surfaceAt(current))
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, brush)
}

/** Estado del líquido: hacia dónde se inclina y cuánto chapotea. */
private class WaterMotion {
    /** Inclinación actual de la superficie, de -1 a 1. */
    val tilt = mutableFloatStateOf(0f)
    /** De 0 a 1: cuánto queda del chapoteo. En 0 la superficie es una línea recta. */
    val energy = mutableFloatStateOf(0f)
    val phase = mutableFloatStateOf(0f)

    /** Velocidad con la que la superficie se mueve; es lo que la hace pasarse de largo. */
    var velocity = 0f
    var lastEventNanos = 0L
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
                // Segundos reales entre lecturas: los eventos no llegan a intervalos
                // iguales, y avanzar un paso fijo por evento es lo que se ve a tirones.
                val previous = water.lastEventNanos
                water.lastEventNanos = event.timestamp
                if (previous == 0L) return
                val dt = ((event.timestamp - previous) / 1_000_000_000f).coerceIn(0.001f, 0.05f)

                // El eje X es el giro lateral. Se amplifica porque en la mano el teléfono
                // se inclina poco: nadie lo pone de costado para mirar el saldo.
                val target = (event.values[0] / 4.5f).coerceIn(-1f, 1f)

                // Resorte amortiguado en vez de perseguir la gravedad con retardo: la
                // superficie se pasa de largo y vuelve, que es lo que hace que se lea como
                // líquido y no como una barra que se acomoda. Subamortiguado a propósito.
                val stiffness = 26f
                val damping = 4.2f
                val acceleration = (target - water.tilt.floatValue) * stiffness - water.velocity * damping
                water.velocity += acceleration * dt
                water.tilt.floatValue += water.velocity * dt

                // Las olas viven de lo rápido que se mueve la superficie, no de dónde
                // está: quieto en diagonal queda inclinado y liso.
                val stirred = (kotlin.math.abs(water.velocity) * 0.5f).coerceAtMost(1f)
                val decay = kotlin.math.exp(-1.6f * dt)
                water.energy.floatValue = maxOf(water.energy.floatValue * decay, stirred)
                if (water.energy.floatValue < 0.01f) {
                    water.energy.floatValue = 0f
                    water.velocity *= 0.5f
                } else {
                    // Avanza con el tiempo, no por evento, para que la ola no acelere
                    // cuando el sensor reporta más seguido.
                    water.phase.floatValue += (5.5f + water.energy.floatValue * 5f) * dt
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
