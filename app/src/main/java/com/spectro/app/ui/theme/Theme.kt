package com.spectro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class SpectroPalette(
    val key: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color = Color(0xFF000000),
    val surface: Color = Color(0xFF0E0E13),
    val surfaceAlt: Color = Color(0xFF17171E),
    val border: Color = Color(0xFF34343E),
    val text: Color = Color(0xFFFFFFFF),
    val textMuted: Color = Color(0xFFE4E4EC),
    val success: Color = Color(0xFF22E080),
    val warning: Color = Color(0xFFFF7A00),
    val danger: Color = Color(0xFFFF3B4F)
) {
    // I due colori fluo non vengono sovrapposti nei controlli principali:
    // fucsia = azione/selezione, arancio = attenzione/priorità.
    val heroBrush: Brush get() = Brush.linearGradient(listOf(primary, primary.copy(alpha = .84f)))
    val subtleBrush: Brush get() = Brush.linearGradient(listOf(primary.copy(alpha = .22f), primary.copy(alpha = .05f)))
}

private val SpectroNeon = SpectroPalette(
    "spectro",
    "Spectro Neon",
    primary = Color(0xFFFF2BD6),
    secondary = Color(0xFFFF7A00),
    tertiary = Color(0xFFFFA21A)
)

val SpectroPalettes = listOf(
    SpectroNeon,
    SpectroPalette("electric", "Electric Cyan", Color(0xFF00E5FF), Color(0xFFFF7A00), Color(0xFFFF2BD6)),
    SpectroPalette("violet", "Spectro Neon (legacy)", Color(0xFFFF2BD6), Color(0xFFFF7A00), Color(0xFFFFA21A)),
    SpectroPalette("emerald", "Emerald Flow", Color(0xFF10E8A1), Color(0xFFFF7A00), Color(0xFFFF2BD6))
)

fun paletteFor(key: String): SpectroPalette = when (key) {
    // Le installazioni precedenti che avevano il tema viola vengono automaticamente
    // portate al nuovo tema fucsia/arancio senza modificare il database.
    "violet" -> SpectroNeon
    else -> SpectroPalettes.firstOrNull { it.key == key } ?: SpectroNeon
}

@Composable
fun SpectroTheme(themeKey: String, content: @Composable () -> Unit) {
    val p = paletteFor(themeKey)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = p.primary,
            secondary = p.secondary,
            tertiary = p.tertiary,
            background = p.background,
            surface = p.surface,
            surfaceVariant = p.surfaceAlt,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onTertiary = Color.Black,
            onBackground = p.text,
            onSurface = p.text,
            onSurfaceVariant = p.textMuted,
            outline = Color(0xFF9A9AA6),
            outlineVariant = Color(0xFF555560),
            error = p.danger
        ),
        content = content
    )
}
