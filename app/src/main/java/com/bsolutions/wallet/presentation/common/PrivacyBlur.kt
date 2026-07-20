package com.bsolutions.wallet.presentation.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modo privacidad: desenfoca el contenido (típicamente un monto) en vez de ocultarlo.
 * Se ve que hay una cifra, pero borrosa e ilegible. No-op si [hidden] es false.
 *
 * Nota: Modifier.blur solo rinde en Android 12+ (API 31); en versiones previas
 * degrada a mostrar el contenido nítido.
 */
fun Modifier.privacyBlur(hidden: Boolean, radius: Dp = 12.dp): Modifier =
    if (hidden) this.blur(radius) else this
