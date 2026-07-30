package com.bsolutions.wallet.presentation.common

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Refracción de las gotas sobre el fondo de la tarjeta.
 *
 * Una gota real no se ve porque tenga color: se ve porque desvía la luz que pasa por
 * ella, así que lo de detrás aparece corrido y con un brillo en el borde. Dibujarla como
 * un círculo translúcido, que es lo que hay hoy, la deja en pegatina.
 *
 * No hace falta capturar lo que hay detrás. La técnica habitual de lluvia sobre cristal
 * lee el fotograma ya dibujado, pero aquí el fondo es un degradado que pintamos nosotros:
 * se conoce por fórmula, así que el shader lo recalcula y desvía las coordenadas antes de
 * evaluarlo. Eso evita la parte cara y frágil del asunto.
 *
 * Requiere AGSL, que existe desde Android 13. Por debajo se sigue dibujando a mano.
 */
object WaterRefraction {

    /** Cuántas gotas caben en los uniforms. Debe coincidir con el shader. */
    const val MAX_DROPS = 12

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Cada gota entra como (x, y, radio, intensidad) en píxeles de la tarjeta.
     *
     * La normal se deduce de la distancia al centro: en el borde la superficie está más
     * inclinada y desvía más, y en el centro no desvía nada. Es la aproximación de una
     * lente esférica y basta a este tamaño.
     */
    private const val SOURCE = """
        uniform float2 size;
        layout(color) uniform half4 topColor;
        layout(color) uniform half4 bottomColor;
        uniform float dropCount;
        uniform float4 drops[12];

        half4 main(float2 coord) {
            float2 displaced = coord;
            float highlight = 0.0;

            for (int i = 0; i < 12; i++) {
                if (float(i) >= dropCount) break;
                float4 drop = drops[i];
                float radius = drop.z;
                if (radius <= 0.0) continue;

                float2 toCenter = coord - drop.xy;
                float distance = length(toCenter);
                if (distance >= radius) continue;

                // Normal de una esfera vista de frente: plana en el centro, muy
                // inclinada en el borde. La raiz es lo que curva el perfil.
                float normalized = distance / radius;
                float bend = sqrt(1.0 - normalized * normalized);
                float2 direction = distance > 0.0 ? toCenter / distance : float2(0.0);

                // Se desvia hacia fuera: la gota actua como lupa y separa lo de detras.
                displaced -= direction * (1.0 - bend) * radius * 0.55 * drop.w;

                // Brillo en el borde superior, que es por donde entra la luz.
                float rim = smoothstep(0.55, 1.0, normalized);
                float lit = clamp(-direction.y, 0.0, 1.0);
                highlight += rim * lit * 0.45 * drop.w;
            }

            // El degradado se recalcula en la coordenada ya desviada: eso es lo que hace
            // que se vea el fondo corrido a traves de la gota.
            float t = clamp(displaced.y / size.y, 0.0, 1.0);
            half4 background = mix(topColor, bottomColor, half(t));

            return half4(background.rgb + half3(half(clamp(highlight, 0.0, 0.6))), background.a);
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createShader(): RuntimeShader = RuntimeShader(SOURCE)
}
