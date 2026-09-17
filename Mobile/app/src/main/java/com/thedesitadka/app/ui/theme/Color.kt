package com.thedesitadka.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Cinematic Palette
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

val DarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color.Black,
    secondary = NeonAzure,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark
)

val LightColorScheme = lightColorScheme(
    primary = AmberPrimary,
    onPrimary = Color.White,
    secondary = NeonAzure,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight
)
