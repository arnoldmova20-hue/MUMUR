package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun buildDarkColorScheme(accent: MurmurAccentTheme): ColorScheme = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    primaryContainer = accent.darkSecondary,
    onPrimaryContainer = MurmurTextPrimary,
    secondary = accent.darkSecondary,
    onSecondary = MurmurTextPrimary,
    secondaryContainer = MurmurSurfaceVariantDark,
    onSecondaryContainer = accent.darkTertiary,
    tertiary = accent.darkTertiary,
    onTertiary = Color(0xFF09131E),
    background = MurmurBgPrimary,
    onBackground = MurmurTextPrimary,
    surface = MurmurBgSecondary,
    onSurface = MurmurTextPrimary,
    surfaceVariant = MurmurCardDark,
    onSurfaceVariant = MurmurTextSecondary,
    surfaceContainer = MurmurCardDark,
    surfaceContainerHigh = MurmurSurfaceVariantDark,
    surfaceContainerLow = MurmurBgSecondary,
    outline = MurmurBorderDark,
    outlineVariant = MurmurBorderDark.copy(alpha = 0.6f),
    error = MurmurErrorDark,
    onError = Color(0xFF1A0505)
)

private fun buildLightColorScheme(accent: MurmurAccentTheme): ColorScheme = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = Color.White,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = accent.lightOnContainer,
    secondary = accent.lightSecondary,
    onSecondary = Color.White,
    secondaryContainer = accent.lightContainer.copy(alpha = 0.7f),
    onSecondaryContainer = accent.lightOnContainer,
    tertiary = accent.lightTertiary,
    onTertiary = Color.White,
    background = MurmurLightBgPrimary,
    onBackground = MurmurLightTextPrimary,
    surface = MurmurLightBgSecondary,
    onSurface = MurmurLightTextPrimary,
    surfaceVariant = MurmurLightCard,
    onSurfaceVariant = MurmurLightTextSecondary,
    surfaceContainer = MurmurLightCard,
    surfaceContainerHigh = Color(0xFFF3F4FA),
    surfaceContainerLow = MurmurLightBgSecondary,
    outline = MurmurBorderLight,
    outlineVariant = MurmurBorderLight.copy(alpha = 0.7f),
    error = Color(0xFFDC2626),
    onError = Color.White
)

val MurmurShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MurmurTheme(
    darkTheme: Boolean = true,
    accentTheme: MurmurAccentTheme = MurmurAccentTheme.PURPLE,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        buildDarkColorScheme(accentTheme)
    } else {
        buildLightColorScheme(accentTheme)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MurmurTypography,
        shapes = MurmurShapes,
        content = content
    )
}
