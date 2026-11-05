package com.thedesitadka.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Cinematic Palette Defaults (Backwards compatible)
val AmberPrimary = Color(0xFFFF9E0B)
val AmberSecondary = Color(0xFFFBBF24)
val NeonAzure = Color(0xFF38BDF8)
val CoralSunset = Color(0xFFF43F5E)

val DarkBackground = Color(0xFF0D0E15)
val DarkSurface = Color(0xFF161822)
val DarkSurfaceVariant = Color(0xFF222533)
val DarkCardSurface = Color(0xFF1C1E2B)
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)

val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF64748B)

val StatusGreen = Color(0xFF10B981)
val StatusYellow = Color(0xFFF59E0B)
val StatusRed = Color(0xFFEF4444)

// ─────────────────────────────────────────────────────────────────────────────
// Dynamic Visual Color Palettes & Glassmorphism System
// ─────────────────────────────────────────────────────────────────────────────

data class AppColorPalette(
    val id: String,
    val name: String,
    val description: String,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardSurface: Color,
    val glassSurface: Color,
    val glassBorder: Brush,
    val glowColor: Color,
    val gradientBrush: Brush,
    val previewColors: List<Color>
) {
    fun toDarkColorScheme(): ColorScheme = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        tertiary = tertiary,
        background = background,
        onBackground = TextPrimaryDark,
        surface = surface,
        onSurface = TextPrimaryDark,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = TextSecondaryDark
    )

    fun toLightColorScheme(): ColorScheme = lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        secondary = secondary,
        onSecondary = Color.White,
        tertiary = tertiary,
        background = LightBackground,
        onBackground = TextPrimaryLight,
        surface = LightSurface,
        onSurface = TextPrimaryLight,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = TextSecondaryLight
    )
}

// 1. Sunset Amber (Warm Cinematic Gold & Rose Flame)
val SunsetAmberPalette = AppColorPalette(
    id = "sunset_amber",
    name = "Sunset Amber",
    description = "Warm cinematic amber & fiery coral glow",
    primary = Color(0xFFFF9E0B),
    onPrimary = Color.Black,
    secondary = Color(0xFFF43F5E),
    onSecondary = Color.White,
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0D0E15),
    surface = Color(0xFF151824),
    surfaceVariant = Color(0xFF1F2336),
    cardSurface = Color(0xFF171A29),
    glassSurface = Color(0xD9141829),
    glassBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.25f),
            Color(0xFFFF9E0B).copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.05f)
        )
    ),
    glowColor = Color(0xFFFF9E0B).copy(alpha = 0.40f),
    gradientBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFF9E0B), Color(0xFFF43F5E))
    ),
    previewColors = listOf(Color(0xFFFF9E0B), Color(0xFFF43F5E), Color(0xFFFBBF24))
)

// 2. Cyber Neon (Vibrant Electric Cyan & Acid Magenta)
val CyberNeonPalette = AppColorPalette(
    id = "cyber_neon",
    name = "Cyber Neon",
    description = "Futuristic electric cyan & high-voltage ultraviolet",
    primary = Color(0xFF00E5FF),
    onPrimary = Color.Black,
    secondary = Color(0xFFD946EF),
    onSecondary = Color.White,
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF0A0D16),
    surface = Color(0xFF101626),
    surfaceVariant = Color(0xFF19233D),
    cardSurface = Color(0xFF131B30),
    glassSurface = Color(0xD90F1626),
    glassBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.30f),
            Color(0xFF00E5FF).copy(alpha = 0.40f),
            Color.White.copy(alpha = 0.05f)
        )
    ),
    glowColor = Color(0xFF00E5FF).copy(alpha = 0.45f),
    gradientBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF00E5FF), Color(0xFFD946EF))
    ),
    previewColors = listOf(Color(0xFF00E5FF), Color(0xFFD946EF), Color(0xFF38BDF8))
)

// 3. Oceanic Aurora (Deep Oceanic Teal & Royal Azure)
val OceanicAuroraPalette = AppColorPalette(
    id = "oceanic_aurora",
    name = "Oceanic Aurora",
    description = "Deep sea sapphire & luminous arctic cyan",
    primary = Color(0xFF06B6D4),
    onPrimary = Color.Black,
    secondary = Color(0xFF3B82F6),
    onSecondary = Color.White,
    tertiary = Color(0xFF2DD4BF),
    background = Color(0xFF080F1A),
    surface = Color(0xFF0E1A2C),
    surfaceVariant = Color(0xFF162842),
    cardSurface = Color(0xFF112036),
    glassSurface = Color(0xD90C192B),
    glassBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color(0xFF06B6D4).copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.05f)
        )
    ),
    glowColor = Color(0xFF06B6D4).copy(alpha = 0.40f),
    gradientBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
    ),
    previewColors = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF2DD4BF))
)

// 4. Emerald Matrix (Lush Cyber Emerald & Electric Lime)
val EmeraldMatrixPalette = AppColorPalette(
    id = "emerald_matrix",
    name = "Emerald Matrix",
    description = "Hyper cybernetic emerald & energetic lime highlights",
    primary = Color(0xFF10B981),
    onPrimary = Color.Black,
    secondary = Color(0xFF84CC16),
    onSecondary = Color.Black,
    tertiary = Color(0xFF34D399),
    background = Color(0xFF07120E),
    surface = Color(0xFF0D1E17),
    surfaceVariant = Color(0xFF142E24),
    cardSurface = Color(0xFF0F241C),
    glassSurface = Color(0xD90C1E17),
    glassBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.25f),
            Color(0xFF10B981).copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.05f)
        )
    ),
    glowColor = Color(0xFF10B981).copy(alpha = 0.40f),
    gradientBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF10B981), Color(0xFF84CC16))
    ),
    previewColors = listOf(Color(0xFF10B981), Color(0xFF84CC16), Color(0xFF34D399))
)

// 5. Crimson Royale (Ruby Red & Royal Violet Velvet)
val CrimsonRoyalePalette = AppColorPalette(
    id = "crimson_royale",
    name = "Crimson Royale",
    description = "Luxurious ruby red & majestic violet velvet",
    primary = Color(0xFFF43F5E),
    onPrimary = Color.White,
    secondary = Color(0xFFA855F7),
    onSecondary = Color.White,
    tertiary = Color(0xFFFB7185),
    background = Color(0xFF120A13),
    surface = Color(0xFF1F1020),
    surfaceVariant = Color(0xFF301932),
    cardSurface = Color(0xFF241326),
    glassSurface = Color(0xD91C0E1E),
    glassBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color(0xFFF43F5E).copy(alpha = 0.38f),
            Color.White.copy(alpha = 0.05f)
        )
    ),
    glowColor = Color(0xFFF43F5E).copy(alpha = 0.40f),
    gradientBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFFF43F5E), Color(0xFFA855F7))
    ),
    previewColors = listOf(Color(0xFFF43F5E), Color(0xFFA855F7), Color(0xFFFB7185))
)

val AvailablePalettes = listOf(
    SunsetAmberPalette,
    CyberNeonPalette,
    OceanicAuroraPalette,
    EmeraldMatrixPalette,
    CrimsonRoyalePalette
)

fun getPaletteById(id: String): AppColorPalette {
    return AvailablePalettes.find { it.id.equals(id, ignoreCase = true) } ?: SunsetAmberPalette
}

val DarkColorScheme = SunsetAmberPalette.toDarkColorScheme()
val LightColorScheme = SunsetAmberPalette.toLightColorScheme()
