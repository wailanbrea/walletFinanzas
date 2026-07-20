package com.bsolutions.wallet.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bsolutions.wallet.core.common.MoneyFormat

/** Degradado verde de marca (drawer, splash y balance). */
val BrandGradientStart = Color(0xFF8BD08D)
val BrandGradientEnd = Color(0xFF3FA45B)

/**
 * Tarjeta resumen con degradado de marca, círculos decorativos, entrada suave
 * y monto animado. Reutilizada en Dashboard, Metas y Pagos planificados.
 */
@Composable
fun GradientSummaryCard(
    title: String,
    amount: Long,
    subtitle: String? = null,
    cornerRadius: Int = 20,
    hidden: Boolean = false,
    extraContent: @Composable () -> Unit = {}
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val appearScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.96f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "cardScale"
    )
    val appearAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "cardAlpha"
    )
    val animatedAmount by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "cardAmount"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = appearScale
                scaleY = appearScale
                alpha = appearAlpha
            }
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(cornerRadius.dp), spotColor = BrandGradientEnd)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(Brush.linearGradient(colors = listOf(BrandGradientStart, BrandGradientEnd)))
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 260.dp, y = (-30).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .size(70.dp)
                .offset(x = (-14).dp, y = 60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.09f))
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = MoneyFormat.format(animatedAmount.toLong()),
                modifier = Modifier.privacyBlur(hidden, radius = 14.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.privacyBlur(hidden, radius = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            extraContent()
        }
    }
}
