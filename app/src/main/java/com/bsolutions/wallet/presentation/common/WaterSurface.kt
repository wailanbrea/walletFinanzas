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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Agua dentro de la tarjeta, con un caballo de ajedrez suelto.
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
    // Se crea una vez: medir texto en cada fotograma cuesta más que dibujarlo.
    val textMeasurer = rememberTextMeasurer()
    val pieceStyle = remember { TextStyle(fontSize = 52.sp, color = Color.Black) }
    // El nivel se anima para que al cambiar de meta el agua suba en vez de saltar.
    val filled by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "nivel"
    )

    Canvas(modifier = modifier) {
        // Se lee siempre: es lo que hace que el dibujo se rehaga mientras algo se mueva.
        val tick = water.tick.value
        if (filled <= 0f || tick < 0f) return@Canvas

        // El rebote mueve todo el nivel, no solo la ola: es lo que se ve al sacudirlo a
        // lo largo. Acotado para que no se salga de la tarjeta.
        val bobbed = (filled + water.bob.value * 0.07f).coerceIn(0f, 1f)
        val surfaceY = size.height * (1f - bobbed)
        // Desnivel entre un extremo y el otro. Va contra el ancho y no contra el alto: la
        // superficie cruza la tarjeta a lo largo, así que medirlo en la altura quedaba en
        // unos pocos píxeles y no se veía nada.
        val slope = water.tilt.value * size.width * 0.22f
        val energy = water.energy.value
        val phase = water.phase.value

        // El caballo va debajo del agua: se ve a través de ella, no flotando encima.
        for (piece in water.pieces) {
            val measured = textMeasurer.measure(text = piece.glyph, style = pieceStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    piece.x * size.width - measured.size.width / 2f,
                    piece.y * size.height - measured.size.height / 2f
                )
            )
        }

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
    // Perfil de Gerstner y no una senoidal: además de subir y bajar, cada punto se
    // desplaza en horizontal hacia la cresta. Eso agrupa agua arriba y la vacía abajo, de
    // modo que las crestas salen puntiagudas y los valles anchos. Una senoidal tiene picos
    // y valles idénticos, y es lo que la hace leerse como tela y no como agua.
    val sharpness = 0.35f

    fun heightAt(t: Float): Float {
        val principal = sin(phase + t * 2f * PI.toFloat())
        val secundaria = sin(phase * 1.7f + t * 3.4f * PI.toFloat()) * 0.35f

        return (principal + secundaria) * amplitude
    }

    fun shiftAt(t: Float): Float {
        // El desplazamiento va con el coseno: máximo en las laderas, nulo en la cresta.
        val principal = cos(phase + t * 2f * PI.toFloat())
        val secundaria = cos(phase * 1.7f + t * 3.4f * PI.toFloat()) * 0.35f

        return (principal + secundaria) * amplitude * sharpness
    }

    fun surfaceAt(t: Float): Float = surfaceY - slope + slope * 2f * t + heightAt(t)

    val path = Path().apply {
        moveTo(0f, surfaceAt(0f))
        // Curvas y no segmentos rectos: con tramos largos la superficie se ve poligonal.
        val steps = 24
        for (i in 1..steps) {
            val previous = (i - 1) / steps.toFloat()
            val current = i / steps.toFloat()
            val middle = (previous + current) / 2f
            // El punto de control se sitúa para que la curva pase por el punto medio real
            // de la onda, y no por la cuerda entre extremos.
            val controlY = 2f * surfaceAt(middle) - (surfaceAt(previous) + surfaceAt(current)) / 2f
            quadraticTo(
                size.width * middle + shiftAt(middle),
                controlY,
                size.width * current + shiftAt(current),
                surfaceAt(current)
            )
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, brush)
}

/** Estado del líquido: hacia dónde se inclina, cuánto chapotea y dónde está el caballo. */
private class WaterMotion {
    /** Inclinación actual de la superficie, de -1 a 1. */
    val tilt = mutableFloatStateOf(0f)
    /** De 0 a 1: cuánto queda del chapoteo. En 0 la superficie es una línea recta. */
    val energy = mutableFloatStateOf(0f)
    val phase = mutableFloatStateOf(0f)
    /** Cuánto sube o baja el nivel al sacudir el teléfono a lo largo. */
    val bob = mutableFloatStateOf(0f)
    /** Sube en cada fotograma: es lo que fuerza a redibujar. */
    val tick = mutableFloatStateOf(0f)

    var velocity = 0f
    var bobVelocity = 0f

    /** Hacia dónde cae la gravedad, en coordenadas de la tarjeta y normalizada. */
    var gravityX = 0f
    var gravityY = 1f

    var lastEventNanos = 0L
    var elapsed = 0f

    /** El caballo suelto en el vaso. */
    val pieces = initialChessPieces()
}

/**
 * Sigue el movimiento del teléfono y mueve las piezas.
 *
 * El agua responde al sensor, pero el caballo se mueve por fotograma y no por lectura del
 * acelerómetro: algo suelto en un líquido nunca se queda del todo quieto, y atarlo al
 * sensor lo dejaba congelado en cuanto el teléfono se apoyaba en la mesa.
 *
 * Tanto el bucle como el listener se sueltan al salir de la pantalla: seguir calculando o
 * escuchando cuando no se ve nada gasta batería sin que el usuario pueda notarlo.
 */
@Composable
private fun rememberWaterMotion(): WaterMotion {
    val water = remember { WaterMotion() }
    // En las vistas previas y en los tests no hay sensor ni bucle: todo queda quieto.
    if (LocalInspectionMode.current) return water
    val context = LocalContext.current

    DisposableEffect(context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Segundos reales entre lecturas: los eventos no llegan a intervalos
                // iguales, y avanzar un paso fijo por evento se ve a tirones.
                val previous = water.lastEventNanos
                water.lastEventNanos = event.timestamp
                if (previous == 0L) return
                val dt = ((event.timestamp - previous) / 1_000_000_000f).coerceIn(0.001f, 0.05f)

                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                // Dirección real de la gravedad en el plano de la pantalla. Leer un solo
                // eje hacía que el agua dejara de responder al girar el teléfono.
                val planar = kotlin.math.sqrt(gx * gx + gy * gy)
                val target = if (planar > 0.5f) (gx / planar).coerceIn(-1f, 1f) else 0f
                if (planar > 0.5f) {
                    water.gravityX = gx / planar
                    water.gravityY = gy / planar
                }

                // Resorte amortiguado en vez de perseguir la gravedad con retardo: la
                // superficie se pasa de largo y vuelve, que es lo que se lee como líquido.
                val acceleration = (target - water.tilt.floatValue) * 20f - water.velocity * 3.4f
                water.velocity += acceleration * dt
                water.tilt.floatValue += water.velocity * dt

                // Sacudirlo comprime el líquido contra el fondo. Se mide la magnitud total
                // menos la gravedad: así da igual cómo se sostenga el teléfono.
                val magnitude = kotlin.math.sqrt(gx * gx + gy * gy + gz * gz)
                val verticalPush = ((magnitude - 9.8f) / 9.8f).coerceIn(-1f, 1f)
                val bobAcceleration = (verticalPush - water.bob.floatValue) * 30f - water.bobVelocity * 5f
                water.bobVelocity += bobAcceleration * dt
                water.bob.floatValue = (water.bob.floatValue + water.bobVelocity * dt).coerceIn(-1f, 1f)

                // Las olas viven de lo rápido que se mueve la superficie, no de dónde está:
                // quieto en diagonal queda inclinado y liso.
                val stirred = maxOf(
                    kotlin.math.abs(water.velocity) * 0.5f,
                    kotlin.math.abs(water.bobVelocity) * 0.35f
                ).coerceAtMost(1f)
                val decay = kotlin.math.exp(-1.6f * dt)
                water.energy.floatValue = maxOf(water.energy.floatValue * decay, stirred)
                if (water.energy.floatValue < 0.01f) {
                    water.energy.floatValue = 0f
                    water.velocity *= 0.5f
                    water.bobVelocity *= 0.5f
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

    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                if (dt > 0f) {
                    water.elapsed += dt
                    advanceChessPieces(
                        pieces = water.pieces,
                        gx = water.gravityX,
                        gy = water.gravityY,
                        dt = dt,
                        margin = 0.13f,
                        elapsed = water.elapsed,
                        stirred = water.energy.floatValue
                    )
                    water.tick.floatValue += dt
                }
            }
        }
    }

    return water
}

/**
 * Qué meta representa el agua.
 *
 * Sin metas es decorativa y llena hasta la mitad. Con metas se elige la más pequeña, que
 * es la que está más cerca de cumplirse y la que más motiva ver subir.
 */
fun waterLevelFor(goalProgress: Float?): Float = goalProgress?.coerceIn(0f, 1f) ?: 0.5f
