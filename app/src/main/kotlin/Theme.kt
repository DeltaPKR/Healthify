package com.healthify.app.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Brand Colors ─────────────────────────────────────────────────────────────
val Green         = Color(0xFF1AD9A0)
val GreenDim      = Color(0x201AD9A0)
val GreenDeep     = Color(0xFF0DB888)
val Coral         = Color(0xFFFF7B6E)
val CoralDim      = Color(0x20FF7B6E)
val Gold          = Color(0xFFFFD166)
val GoldDim       = Color(0x20FFD166)
val Lavender      = Color(0xFFA78BFA)
val LavenderDim   = Color(0x20A78BFA)
val Sky           = Color(0xFF5EC4FF)
val SkyDim        = Color(0x205EC4FF)
val Amber         = Color(0xFFFFA24C)
val Rose          = Color(0xFFF472B6)
val Indigo        = Color(0xFF6366F1)
val Teal          = Color(0xFF2DD4BF)
val BgDark        = Color(0xFF070D1A)
val SurfaceCard   = Color(0xFF0D1626)
val SurfaceCard2  = Color(0xFF111E30)
val TextPrimary   = Color(0xFFEDF5FF)
val TextMuted     = Color(0x85EDF5FF)
val TextDim       = Color(0x45EDF5FF)
val Divider       = Color(0x14FFFFFF)

// ── Glass surfaces ───────────────────────────────────────────────────────────
// Translucent fills laid over the aurora. There is no backdrop blur (it would
// need API 31+); the aurora is already soft, so a light wash plus a bright
// top hairline reads as frosted glass on every API level.
val GlassFillTop      = Color(0x17FFFFFF)
val GlassFillBottom   = Color(0x08FFFFFF)
val GlassBorderTop    = Color(0x33FFFFFF)
val GlassBorderBottom = Color(0x0AFFFFFF)
/** Opaque-ish glass for chrome that sits over scrolling content (bottom nav). */
val GlassChromeTop    = Color(0xF5131E33)
val GlassChromeBottom = Color(0xFC0A1322)

// ── Aurora palettes (time of day) ────────────────────────────────────────────
data class AuroraPalette(val a: Color, val b: Color, val c: Color, val d: Color)

enum class TimeOfDay {
    MORNING, DAY, EVENING, NIGHT;

    companion object {
        fun from(hour: Int): TimeOfDay = when (hour) {
            in 5..10  -> MORNING
            in 11..16 -> DAY
            in 17..20 -> EVENING
            else      -> NIGHT
        }
        fun now(): TimeOfDay = from(java.time.LocalTime.now().hour)
    }
}

fun auroraFor(t: TimeOfDay): AuroraPalette = when (t) {
    TimeOfDay.MORNING -> AuroraPalette(a = Sky,    b = Coral,    c = Gold,  d = Green)
    TimeOfDay.DAY     -> AuroraPalette(a = Green,  b = Sky,      c = Teal,  d = Lavender)
    TimeOfDay.EVENING -> AuroraPalette(a = Rose,   b = Lavender, c = Indigo, d = Coral)
    TimeOfDay.NIGHT   -> AuroraPalette(a = Indigo, b = Lavender, c = Sky,   d = Teal)
}

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
    val assets = LocalContext.current.assets
    val typography = remember(assets) { appTypography(jakartaFamily(assets)) }
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = typography,
        content     = content
    )
}

// ── Typography ────────────────────────────────────────────────────────────────
// Plus Jakarta Sans ships as one variable font (OFL — licence in
// docs/licenses). It lives in assets/, not res/font: Compose applies
// variation settings to asset fonts (API 26+, our minSdk) but loads res
// fonts at their default instance, which rendered every weight as Regular.
private const val JAKARTA_ASSET = "fonts/plus_jakarta_sans.ttf"

@OptIn(ExperimentalTextApi::class)
fun jakartaFamily(assets: AssetManager): FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            path = JAKARTA_ASSET,
            assetManager = assets,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w))
        )
    }
)

/** Tabular figures so animated counters don't jitter as digits change width. */
const val TABULAR = "tnum"

fun appTypography(family: FontFamily) = Typography(
    displayLarge   = TextStyle(fontFamily = family, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp, color = TextPrimary, fontFeatureSettings = TABULAR),
    headlineLarge  = TextStyle(fontFamily = family, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, color = TextPrimary),
    headlineMedium = TextStyle(fontFamily = family, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = TextPrimary),
    headlineSmall  = TextStyle(fontFamily = family, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, color = TextPrimary),
    titleLarge     = TextStyle(fontFamily = family, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    titleMedium    = TextStyle(fontFamily = family, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleSmall     = TextStyle(fontFamily = family, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    bodyLarge      = TextStyle(fontFamily = family, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodyMedium     = TextStyle(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.Normal, color = TextMuted),
    bodySmall      = TextStyle(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.Normal, color = TextDim),
    labelLarge     = TextStyle(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium    = TextStyle(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall     = TextStyle(fontFamily = family, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = TextDim)
)
