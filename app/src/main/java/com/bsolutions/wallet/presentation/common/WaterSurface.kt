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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
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
    modifier: Modifier = Modifier,
    /** Colores del fondo que hay detrás, para que las gotas puedan refractarlo. */
    backgroundTop: Color? = null,
    backgroundBottom: Color? = null
) {
    val water = rememberWaterMotion()  // se lee dentro del Canvas, no aquí
    // El nivel se anima para que al cambiar de meta el agua suba en vez de saltar.
    val filled by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "nivel"
    )

    // Las gotas refractan el fondo donde hay AGSL (Android 13+). Por debajo se quedan
    // dibujadas a mano, que es lo que ya se veía.
    val refraction = if (backgroundTop != null && backgroundBottom != null) {
        rememberDropletRefraction(water, backgroundTop, backgroundBottom)
    } else {
        null
    }

    Canvas(modifier = if (refraction != null) modifier.then(refraction) else modifier) {
        if (filled <= 0f) return@Canvas
        // El rebote mueve todo el nivel, no solo la ola: es lo que se ve al sacudirlo a
        // lo largo. Acotado para que no se salga de la tarjeta.
        val bobbed = (filled + water.bob.value * 0.07f).coerceIn(0f, 1f)
        val surfaceY = size.height * (1f - bobbed)
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

        // Las gotas se leen para que el dibujo se rehaga mientras haya alguna en el aire.
        water.splashTick.value
        for (drop in water.droplets) {
            // Nacen en la superficie: se parte del nivel y se le suma su propia caida.
            val originY = surfaceY - slope + slope * 2f * drop.x
            val centerY = if (drop.stuck) {
                originY + drop.glassY * size.height
            } else {
                originY + drop.y * size.height
            }
            val fade = drop.life.coerceIn(0f, 1f)
            val radius = drop.size * size.width

            if (drop.stuck) {
                // Rastro: una gota que resbala deja mojado el camino, y ese reguero es
                // lo que se reconoce como cristal con agua. Se afina hacia arriba.
                val trail = drop.trail * size.height
                if (trail > 1f) {
                    drawPath(
                        path = Path().apply {
                            moveTo(drop.x * size.width - radius * 0.45f, centerY)
                            lineTo(drop.x * size.width - radius * 0.12f, centerY - trail)
                            lineTo(drop.x * size.width + radius * 0.12f, centerY - trail)
                            lineTo(drop.x * size.width + radius * 0.45f, centerY)
                            close()
                        },
                        color = color.copy(alpha = 0.16f * fade)
                    )
                }
                drawCircle(
                    color = color.copy(alpha = 0.85f * fade),
                    radius = radius * 0.85f,
                    center = Offset(drop.x * size.width, centerY)
                )
            } else {
                // En el aire la gota se estira en la direccion en que va: una esfera
                // perfecta se lee como una burbuja, no como agua cayendo.
                val stretch = (1f + kotlin.math.abs(drop.vy) * 0.5f).coerceAtMost(2.2f)
                // Brillo pequeno arriba a la izquierda: es lo que hace que una mancha
                // clara se lea como una gota con volumen y no como un borron.
                drawCircle(
                    color = color.copy(alpha = 0.95f * fade),
                    radius = radius * 0.34f,
                    center = Offset(
                        drop.x * size.width - radius * 0.30f,
                        centerY - radius * 0.34f
                    )
                )
                drawOval(
                    color = color.copy(alpha = 0.72f * fade),
                    topLeft = Offset(
                        drop.x * size.width - radius,
                        centerY - radius * stretch
                    ),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f * stretch)
                )
            }
        }
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
    // Perfil de Gerstner y no una senoidal: ademas de subir y bajar, cada punto se
    // desplaza en horizontal hacia la cresta. Eso agrupa agua arriba y la vacia abajo,
    // de modo que las crestas salen puntiagudas y los valles anchos. Una senoidal tiene
    // picos y valles identicos, y es lo que hace que se lea como una tela y no como agua.
    val sharpness = 0.35f

    fun heightAt(t: Float): Float {
        val principal = sin(phase + t * 2f * PI.toFloat())
        val secundaria = sin(phase * 1.7f + t * 3.4f * PI.toFloat()) * 0.35f

        return (principal + secundaria) * amplitude
    }

    fun shiftAt(t: Float): Float {
        // El desplazamiento va con el coseno: maximo en las laderas, nulo en la cresta.
        val principal = kotlin.math.cos(phase + t * 2f * PI.toFloat())
        val secundaria = kotlin.math.cos(phase * 1.7f + t * 3.4f * PI.toFloat()) * 0.35f

        return (principal + secundaria) * amplitude * sharpness
    }

    fun surfaceAt(t: Float): Float = surfaceY - slope + slope * 2f * t + heightAt(t)

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

/**
 * Una gota que se separó de la masa.
 *
 * Posición y velocidad van en fracción de la tarjeta y no en píxeles: el estado se
 * actualiza en el sensor, que no sabe de qué tamaño se está dibujando.
 */
private class Droplet(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float = 1f,
    val size: Float,
    /** Pegada al cristal: ya no vuela, resbala. */
    var stuck: Boolean = false,
    /** Y absoluta en la tarjeta mientras está pegada, sin depender del nivel del agua. */
    var glassY: Float = 0f,
    /** Cuánto reguero mojado lleva detrás; crece al resbalar y se seca sola. */
    var trail: Float = 0f
)

/** Estado del líquido: hacia dónde se inclina y cuánto chapotea. */
private class WaterMotion {
    /** Inclinación actual de la superficie, de -1 a 1. */
    val tilt = mutableFloatStateOf(0f)
    /** De 0 a 1: cuánto queda del chapoteo. En 0 la superficie es una línea recta. */
    val energy = mutableFloatStateOf(0f)
    val phase = mutableFloatStateOf(0f)

    /** Velocidad con la que la superficie se mueve; es lo que la hace pasarse de largo. */
    var velocity = 0f

    /** Cuánto sube o baja el nivel al sacudir el teléfono a lo largo, de -1 a 1. */
    val bob = mutableFloatStateOf(0f)
    var bobVelocity = 0f

    /**
     * Hacia donde cae la gravedad, en coordenadas de la tarjeta y normalizada.
     *
     * Las gotas caian siempre hacia abajo de la pantalla: con el telefono inclinado eso
     * se ve mal de inmediato, porque el agua se inclina y las gotas no.
     */
    var gravityX = 0f
    var gravityY = 1f

    var lastEventNanos = 0L

    /**
     * Gotas en el aire. Acotadas porque cada una se dibuja aparte, y a partir de unas
     * pocas decenas no se distinguen pero sí se pagan.
     */
    val droplets = mutableListOf<Droplet>()
    /** Sube en cada paso: sin esto las gotas se congelarían al calmarse el chapoteo. */
    val splashTick = mutableFloatStateOf(0f)
}

private const val MAX_DROPLETS = 20

/**
 * Mueve las gotas y lanza nuevas cuando el chapoteo da para ello.
 *
 * Una salpicadura es masa que se separa, asi que no puede salir de la curva: son cuerpos
 * aparte con su propia gravedad. Nacen solo por encima de cierta agitacion, porque de
 * otro modo el agua estaria escupiendo gotas todo el rato y dejaria de leerse como agua.
 */
private fun advanceDroplets(water: WaterMotion, dt: Float, stirred: Float) {
    val gravity = 2.4f
    // Se cae hacia donde tira la gravedad de verdad, no hacia abajo de la tarjeta.
    val gx = water.gravityX
    val gy = water.gravityY
    val iterator = water.droplets.iterator()
    while (iterator.hasNext()) {
        val drop = iterator.next()

        if (drop.stuck) {
            // Pegada al cristal no cae libre: la tensión superficial la retiene y de
            // pronto cede. Ese avance a tirones es lo que delata una gota real; bajando
            // a velocidad constante parece un punto animado.
            val releases = Math.random().toFloat() < 0.06f
            if (releases) drop.vy = 0.05f + Math.random().toFloat() * 0.09f
            drop.vy *= 0.90f
            val slid = drop.vy * dt
            drop.glassY += slid
            drop.trail = (drop.trail + slid * 1.6f - dt * 0.05f).coerceIn(0f, 0.35f)
            // Se seca despacio: una gota en el vidrio dura mucho más que una en el aire.
            drop.life -= dt * 0.22f
            if (drop.life <= 0f || drop.glassY > 1.02f) iterator.remove()
            continue
        }

        drop.vx += gx * gravity * dt
        drop.vy += gy * gravity * dt
        drop.x += drop.vx * dt
        drop.y += drop.vy * dt
        drop.life -= dt * 0.75f

        // Al llegar arriba del arco la gota toca el cristal; unas se quedan pegadas y
        // otras siguen cayendo, que es lo que pasa al agitar una botella.
        // Toca el cristal al llegar arriba del arco: es cuando deja de subir contra la
        // gravedad y empieza a caer. Se mide sobre el vector, no sobre el eje vertical,
        // porque con el telefono girado "arriba" ya no es arriba de la pantalla.
        val alongGravity = drop.vx * gx + drop.vy * gy
        if (alongGravity > 0f && !drop.stuck && Math.random().toFloat() < 0.35f) {
            drop.stuck = true
            drop.glassY = drop.y
            drop.vy = 0f
            drop.life = 1f
        }

        if (drop.life <= 0f || drop.y > 1.05f) iterator.remove()
    }

    if (stirred > 0.55f && water.droplets.size < MAX_DROPLETS) {
        // Cuantas mas, cuanto mas fuerte el meneo; nunca mas de tres por lectura para
        // que un golpe seco no vacie el presupuesto de gotas de una vez.
        val count = (1 + (stirred * 2.5f).toInt()).coerceAtMost(3)
        repeat(count) {
            val fromLeft = Math.random().toFloat()
            water.droplets += Droplet(
                x = fromLeft,
                y = 0f,
                // Salen hacia donde va la superficie: si el agua sube por la derecha,
                // las gotas salen hacia la derecha.
                // Salen contra la gravedad, no hacia arriba de la pantalla: si el
                // telefono esta de lado, el agua salta hacia el lado.
                vx = -water.gravityX * (0.5f + stirred * 0.9f) * (0.7f + Math.random().toFloat() * 0.6f) +
                    (water.velocity * 0.35f) + (Math.random().toFloat() - 0.5f) * 0.35f,
                vy = -water.gravityY * (0.5f + stirred * 0.9f) * (0.7f + Math.random().toFloat() * 0.6f),
                size = 0.006f + Math.random().toFloat() * 0.010f
            )
        }
    }

    if (water.droplets.isNotEmpty()) water.splashTick.floatValue += dt
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
                // Componente lateral de la gravedad, normalizada por su magnitud en el
                // plano de la pantalla. Leer solo el eje X hacia que el agua dejara de
                // responder al girar el telefono: a noventa grados ese eje marca cero y
                // es el otro el que lleva la gravedad. Asi funciona en cualquier giro.
                val gx = event.values[0]
                val gy = event.values[1]
                val planar = kotlin.math.sqrt(gx * gx + gy * gy)
                val target = if (planar > 0.5f) (gx / planar).coerceIn(-1f, 1f) else 0f
                if (planar > 0.5f) {
                    water.gravityX = gx / planar
                    water.gravityY = gy / planar
                }

                // Resorte amortiguado en vez de perseguir la gravedad con retardo: la
                // superficie se pasa de largo y vuelve, que es lo que hace que se lea como
                // líquido y no como una barra que se acomoda. Subamortiguado a propósito.
                val stiffness = 20f
                val damping = 3.4f
                val acceleration = (target - water.tilt.floatValue) * stiffness - water.velocity * damping
                water.velocity += acceleration * dt
                water.tilt.floatValue += water.velocity * dt

                // Las olas viven de lo rápido que se mueve la superficie, no de dónde
                // está: quieto en diagonal queda inclinado y liso.
                // Sacudirlo a lo largo comprime el liquido contra el fondo y la
                // superficie rebota. El eje Y trae la gravedad cuando esta derecho, asi
                // que lo que importa es cuanto se aparta de ella, no su valor.
                // Sacudirlo comprime el liquido contra el fondo. Se mide la magnitud
                // total menos la gravedad: asi da igual como se sostenga el telefono.
                val gz = event.values[2]
                val magnitude = kotlin.math.sqrt(gx * gx + gy * gy + gz * gz)
                val verticalPush = ((magnitude - 9.8f) / 9.8f).coerceIn(-1f, 1f)
                val bobAcceleration = (verticalPush - water.bob.floatValue) * 30f - water.bobVelocity * 5f
                water.bobVelocity += bobAcceleration * dt
                water.bob.floatValue = (water.bob.floatValue + water.bobVelocity * dt).coerceIn(-1f, 1f)

                val stirred = maxOf(
                    kotlin.math.abs(water.velocity) * 0.5f,
                    kotlin.math.abs(water.bobVelocity) * 0.35f
                ).coerceAtMost(1f)
                val decay = kotlin.math.exp(-1.6f * dt)
                water.energy.floatValue = maxOf(water.energy.floatValue * decay, stirred)
                advanceDroplets(water, dt, stirred)

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

    return water
}

/**
 * Qué meta representa el agua.
 *
 * Sin metas es decorativa y llena hasta la mitad. Con metas se elige la más pequeña,
 * que es la que está más cerca de cumplirse y la que más motiva ver subir.
 */
fun waterLevelFor(goalProgress: Float?): Float = goalProgress?.coerceIn(0f, 1f) ?: 0.5f

/**
 * Capa que refracta el fondo a través de las gotas.
 *
 * Devuelve null donde no hay AGSL: por debajo de Android 13 las gotas se siguen pintando
 * a mano, que es peor pero funciona.
 *
 * El shader se crea una sola vez y solo se le reescriben los uniforms; recrearlo en cada
 * fotograma recompilaría el programa y costaría más que todo lo demás junto.
 */
@Composable
private fun rememberDropletRefraction(
    water: WaterMotion,
    backgroundTop: Color,
    backgroundBottom: Color
): Modifier? {
    if (!WaterRefraction.isSupported) return null
    val shader = remember { WaterRefraction.createShader() }

    return Modifier.drawWithCache {
        onDrawBehind {
            // Se lee para que la capa se rehaga mientras haya gotas moviéndose.
            water.splashTick.value
            val visible = water.droplets.take(WaterRefraction.MAX_DROPS)
            if (visible.isEmpty()) return@onDrawBehind

            shader.setFloatUniform("size", size.width, size.height)
            shader.setColorUniform("topColor", backgroundTop.toArgb())
            shader.setColorUniform("bottomColor", backgroundBottom.toArgb())
            shader.setFloatUniform("dropCount", visible.size.toFloat())

            val packed = FloatArray(WaterRefraction.MAX_DROPS * 4)
            visible.forEachIndexed { index, drop ->
                val base = index * 4
                packed[base] = drop.x * size.width
                packed[base + 1] = (if (drop.stuck) drop.glassY else drop.y) * size.height
                packed[base + 2] = drop.size * size.width * (if (drop.stuck) 0.85f else 1f)
                packed[base + 3] = drop.life.coerceIn(0f, 1f)
            }
            shader.setFloatUniform("drops", packed)

            drawRect(brush = ShaderBrush(shader))
        }
    }
}
