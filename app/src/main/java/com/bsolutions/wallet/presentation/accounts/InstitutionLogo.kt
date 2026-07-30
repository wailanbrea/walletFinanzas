package com.bsolutions.wallet.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.bsolutions.wallet.core.financial.FinancialInstitutions
import java.text.Normalizer

@Composable
fun InstitutionLogo(
    institutionName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onDarkBackground: Boolean = false
) {
    val institution = FinancialInstitutions.findByName(institutionName)
    val fallback: @Composable (Modifier) -> Unit = { fallbackModifier ->
        InstitutionMonogram(institutionName, fallbackModifier, size, onDarkBackground)
    }
    val logoUrl = institution?.logoUrl
    if (logoUrl == null) {
        fallback(modifier)
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(logoUrl)
            // La clave incluye la URL: si la SB republica el activo, la caché se invalida sola.
            .memoryCacheKey("institution-logo:${institution.id}:$logoUrl")
            .diskCacheKey("institution-logo:${institution.id}:$logoUrl")
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        loading = { fallback(Modifier) },
        error = { fallback(Modifier) },
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            // Siempre blanco, también en modo oscuro: los logos de la SB son marcas de
            // tinta oscura sobre transparente y sobre una superficie negra desaparecen.
            .background(Color.White)
            // Son marcas anchas: sin margen quedan pegadas al borde.
            .padding(size / 10)
    )
}

@Composable
private fun InstitutionMonogram(
    institutionName: String?,
    modifier: Modifier,
    size: Dp,
    onDarkBackground: Boolean
) {
    val name = institutionName?.trim().orEmpty()
    val initials = name.split(Regex("\\s+"))
        .filter { it.isNotEmpty() && it.lowercase() !in setOf("banco", "de", "del", "y") }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
    val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
    val palette = listOf(0xFF1D4E89, 0xFF0B6E69, 0xFF7A3E9D, 0xFF9A4F14, 0xFF3D5A80)
    val color = Color(palette[(normalized.hashCode() and Int.MAX_VALUE) % palette.size])

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(if (onDarkBackground) Color.White.copy(alpha = 0.18f) else color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
