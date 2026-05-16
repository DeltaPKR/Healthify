package com.healthify.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Brand Colors ─────────────────────────────────────────────────────────────
val Green         = Color(0xFF1AD9A0)
val GreenDim      = Color(0x201AD9A0)
val Coral         = Color(0xFFFF7B6E)
val CoralDim      = Color(0x20FF7B6E)
val Gold          = Color(0xFFFFD166)
val GoldDim       = Color(0x20FFD166)
val Lavender      = Color(0xFFA78BFA)
val LavenderDim   = Color(0x20A78BFA)
val Sky           = Color(0xFF5EC4FF)
val SkyDim        = Color(0x205EC4FF)
val BgDark        = Color(0xFF070D1A)
val SurfaceCard   = Color(0xFF0D1626)
val SurfaceCard2  = Color(0xFF111E30)
val TextPrimary   = Color(0xFFEDF5FF)
val TextMuted     = Color(0x85EDF5FF)
val TextDim       = Color(0x45EDF5FF)
val Divider       = Color(0x14FFFFFF)

// ── Dark colour scheme ────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary          = Green,
    onPrimary        = Color(0xFF05100C),
    primaryContainer = GreenDim,
    secondary        = Lavender,
    background       = BgDark,
    surface          = SurfaceCard,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    error            = Coral,
    outline          = Divider
)

@Composable
fun HealthifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AppTypography,
        content     = content
    )
}

// ── Typography (system fonts – swap for custom if assets added) ───────────────
val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, color = TextMuted),
    bodySmall = TextStyle(fontSize = 12.sp, color = TextDim),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.sp, color = TextDim)
)
