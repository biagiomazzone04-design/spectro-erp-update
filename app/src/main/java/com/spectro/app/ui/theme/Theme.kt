package com.spectro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
    val background: Color = Color(0xFF060608),
    val surface: Color = Color(0xFF101014),
    val surfaceAlt: Color = Color(0xFF17171D),
    val border: Color = Color(0xFF2A2A31),
    val text: Color = Color(0xFFF8F8FA),
    val textMuted: Color = Color(0xFFD8D8E0),
    val success: Color = Color(0xFF22C55E),
    val warning: Color = Color(0xFFF59E0B),
    val danger: Color = Color(0xFFFF3B3B)
) {
    val heroBrush: Brush get() = Brush.linearGradient(listOf(primary, secondary, tertiary))
    val subtleBrush: Brush get() = Brush.linearGradient(listOf(primary.copy(alpha = .22f), secondary.copy(alpha = .08f)))
}

val SpectroPalettes = listOf(
    SpectroPalette("spectro", "Spectro Flame", Color(0xFFFF5A1F), Color(0xFFFF00A8), Color(0xFFFF7A18)),
    SpectroPalette("electric", "Electric Cyan", Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF7C4DFF)),
    SpectroPalette("violet", "Violet Shift", Color(0xFF8B5CF6), Color(0xFFD946EF), Color(0xFFFF4D8D)),
    SpectroPalette("emerald", "Emerald Flow", Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6))
)

fun paletteFor(key: String): SpectroPalette = SpectroPalettes.firstOrNull { it.key == key } ?: SpectroPalettes.first()

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
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onBackground = p.text,
            onSurface = p.text,
            onSurfaceVariant = p.textMuted,
            outline = Color(0xFF858590),
            outlineVariant = Color(0xFF4C4C56),
            error = p.danger
        ),
        content = content
    )
}
