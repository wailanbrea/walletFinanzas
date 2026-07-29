package com.bsolutions.wallet.presentation.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modo privacidad: desenfoca el contenido (típicamente un monto) en vez de ocultarlo.
 * Se ve que hay una cifra, pero borrosa e ilegible. No-op si [hidden] es false.
 *
 * Alcance deliberado: solo el Balance Total y los Ingresos. Los saldos por cuenta, los
 * gastos y los movimientos se ven siempre — lo sensible frente a una mirada ajena es
 * cuánto se tiene y cuánto se gana, no en qué se gastó.
 *
 * Nota: Modifier.blur solo rinde en Android 12+ (API 31); en versiones previas
 * degrada a mostrar el contenido nítido.
 */
fun Modifier.privacyBlur(hidden: Boolean, radius: Dp = 12.dp): Modifier =
    if (hidden) this.blur(radius) else this
