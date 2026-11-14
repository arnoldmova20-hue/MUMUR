package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Official MURMUR Palette
val MurmurBgPrimary = Color(0xFF090A0D)
val MurmurBgSecondary = Color(0xFF111319)
val MurmurCardDark = Color(0xFF171A22)
val MurmurTextPrimary = Color(0xFFF5F5F7)
val MurmurTextSecondary = Color(0xFF9296A3)
val MurmurPurplePrimary = Color(0xFFA78BFA)
val MurmurVioletSecondary = Color(0xFF7657E8)
val MurmurBlueAccent = Color(0xFF91BCEB)

// Supporting Semantic & Border Tokens (Dark)
val MurmurBorderDark = Color(0xFF252936)
val MurmurSurfaceVariantDark = Color(0xFF1E222D)
val MurmurErrorDark = Color(0xFFF87171)
val MurmurSuccessDark = Color(0xFF34D399)
val MurmurWarningDark = Color(0xFFFBBF24)

// Light Mode Surface Tokens (coherent with MURMUR identity)
val MurmurLightBgPrimary = Color(0xFFF7F7FA)
val MurmurLightBgSecondary = Color(0xFFEEEEF5)
val MurmurLightCard = Color(0xFFFFFFFF)
val MurmurLightTextPrimary = Color(0xFF12131A)
val MurmurLightTextSecondary = Color(0xFF5D6273)
val MurmurLightPurplePrimary = Color(0xFF6D4CE0)
val MurmurLightVioletSecondary = Color(0xFF5636C7)
val MurmurLightBlueAccent = Color(0xFF3B82C4)
val MurmurBorderLight = Color(0xFFE0E2EC)

enum class MurmurAccentTheme(
    val key: String,
    val labelEs: String,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkSecondary: Color,
    val darkTertiary: Color,
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightTertiary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color
) {
    PURPLE(
        key = "purple",
        labelEs = "Morado",
        darkPrimary = MurmurPurplePrimary,
        darkOnPrimary = Color(0xFF0E0B1A),
        darkSecondary = MurmurVioletSecondary,
        darkTertiary = MurmurBlueAccent,
        lightPrimary = MurmurLightPurplePrimary,
        lightSecondary = MurmurLightVioletSecondary,
        lightTertiary = MurmurLightBlueAccent,
        lightContainer = Color(0xFFEDE9FE),
        lightOnContainer = Color(0xFF2B186B)
    ),
    OCEAN(
        key = "ocean",
        labelEs = "Azul",
        darkPrimary = Color(0xFF60A5FA),
        darkOnPrimary = Color(0xFF07162E),
        darkSecondary = Color(0xFF2563EB),
        darkTertiary = Color(0xFF38BDF8),
        lightPrimary = Color(0xFF2563EB),
        lightSecondary = Color(0xFF1D4ED8),
        lightTertiary = Color(0xFF0284C7),
        lightContainer = Color(0xFFDBEAFE),
        lightOnContainer = Color(0xFF1E3A8A)
    ),
    ROSE(
        key = "rose",
        labelEs = "Rosa",
        darkPrimary = Color(0xFFF472B6),
        darkOnPrimary = Color(0xFF2B0719),
        darkSecondary = Color(0xFFDB2777),
        darkTertiary = Color(0xFFA78BFA),
        lightPrimary = Color(0xFFDB2777),
        lightSecondary = Color(0xFFBE185D),
        lightTertiary = Color(0xFF7C3AED),
        lightContainer = Color(0xFFFCE7F3),
        lightOnContainer = Color(0xFF831843)
    ),
    EMERALD(
        key = "emerald",
        labelEs = "Esmeralda",
        darkPrimary = Color(0xFF34D399),
        darkOnPrimary = Color(0xFF042217),
        darkSecondary = Color(0xFF059669),
        darkTertiary = Color(0xFF2DD4BF),
        lightPrimary = Color(0xFF059669),
        lightSecondary = Color(0xFF047857),
        lightTertiary = Color(0xFF0D9488),
        lightContainer = Color(0xFFD1FAE5),
        lightOnContainer = Color(0xFF064E3B)
    ),
    AMBER(
        key = "amber",
        labelEs = "Ámbar",
        darkPrimary = Color(0xFFFBBF24),
        darkOnPrimary = Color(0xFF261802),
        darkSecondary = Color(0xFFD97706),
        darkTertiary = Color(0xFFFB923C),
        lightPrimary = Color(0xFFD97706),
        lightSecondary = Color(0xFFB45309),
        lightTertiary = Color(0xFFEA580C),
        lightContainer = Color(0xFFFEF3C7),
        lightOnContainer = Color(0xFF78350F)
    ),
    CORAL(
        key = "coral",
        labelEs = "Coral",
        darkPrimary = Color(0xFFFB7185),
        darkOnPrimary = Color(0xFF2D060D),
        darkSecondary = Color(0xFFE11D48),
        darkTertiary = Color(0xFFF472B6),
        lightPrimary = Color(0xFFE11D48),
        lightSecondary = Color(0xFFBE123C),
        lightTertiary = Color(0xFFDB2777),
        lightContainer = Color(0xFFFFE4E6),
        lightOnContainer = Color(0xFF881337)
    );

    companion object {
        fun fromKey(key: String?): MurmurAccentTheme =
            entries.firstOrNull { it.key == key } ?: PURPLE
    }
}

enum class MurmurNavBarStyle(
    val key: String,
    val labelEs: String,
    val descriptionEs: String
) {
    DOCKED(
        key = "original",
        labelEs = "Original (pegada)",
        descriptionEs = "Barra fija anclada al borde inferior de la pantalla"
    ),
    FLOATING(
        key = "floating",
        labelEs = "Flotante (estilo iOS)",
        descriptionEs = "Barra en cápsula despegada de los bordes con sombra suave"
    );

    companion object {
        fun fromKey(key: String?): MurmurNavBarStyle =
            entries.firstOrNull { it.key == key } ?: DOCKED
    }
}
