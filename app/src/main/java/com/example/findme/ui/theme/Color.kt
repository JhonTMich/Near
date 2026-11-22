package com.example.findme.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Soft night-blue palette taken from the Near icon: the night-blue ledge as background, slate-blue
// cards, the mascot's cream as ink and the icon's sky blue (lightened) as the main accent.
val BrandNight = Color(0xFF20233F)
val BrandIconBlue = Color(0xFF566D91)
val BrandInk = Color(0xFFFFF5E8)
val BrandCream = Color(0xFFFFF5E8)

/** Every colour the screens use directly, so the whole app follows the chosen theme. */
@Immutable
data class NearPalette(
    val accent: Color, val sky: Color, val slate: Color, val orange: Color,
    val background: Color, val surface: Color, val card: Color, val cardBorder: Color, val inner: Color,
    val textPrimary: Color, val textSecondary: Color, val textMuted: Color,
    val accentContainer: Color, val skyContainer: Color, val slateContainer: Color,
    val heroGradient: List<Color>, val statusGreen: Color, val university: Color,
    /** The arched panel under the sign-in forms. */
    val loginPanel: Color,
    val freeSurface: Color, val busySurface: Color, val busyInk: Color,
)

val DarkPalette = NearPalette(
    accent = Color(0xFFB9A0FA), sky = Color(0xFFDBECA7), slate = Color(0xFFCEB7EF), orange = Color(0xFFECC48F),
    background = Color(0xFF1C1824), surface = Color(0xFF25202E), card = Color(0xFF30283C),
    cardBorder = Color(0xFF51445E), inner = Color(0xFF3B3049),
    textPrimary = Color(0xFFF7F0E5), textSecondary = Color(0xFFD7CBDE), textMuted = Color(0xFFB7A9C3),
    accentContainer = Color(0xFF44335E), skyContainer = Color(0xFF303B28), slateContainer = Color(0xFF3E304F),
    heroGradient = listOf(Color(0xFF44335E), Color(0xFF382946)),
    statusGreen = Color(0xFFDAEDA0), university = Color(0xFFB4CCF5),
    loginPanel = Color(0xFF25202E),
    freeSurface = Color(0xFF303B28), busySurface = Color(0xFF4D3038), busyInk = Color(0xFFFFB7A9),
)

val LightPalette = NearPalette(
    accent = Color(0xFF6547C7), sky = Color(0xFF3B541A), slate = Color(0xFF654695), orange = Color(0xFF88511D),
    background = Color(0xFFF6F2E9), surface = Color(0xFFFFFBF4), card = Color(0xFFFFFBF4),
    cardBorder = Color(0xFFD7CBD9), inner = Color(0xFFF0E8F2),
    textPrimary = Color(0xFF282137), textSecondary = Color(0xFF554963), textMuted = Color(0xFF675B72),
    accentContainer = Color(0xFFE7D9F6), skyContainer = Color(0xFFE0EDAE), slateContainer = Color(0xFFEBDDF5),
    heroGradient = listOf(Color(0xFFDFC9F1), Color(0xFFEADBF5)),
    statusGreen = Color(0xFF344B12), university = Color(0xFF355D8B),
    loginPanel = Color(0xFFFFFBF4),
    freeSurface = Color(0xFFE0EDAE), busySurface = Color(0xFFF6D6CE), busyInk = Color(0xFF812D29),
)

val LocalNearPalette = staticCompositionLocalOf { DarkPalette }

private val palette: NearPalette @Composable @ReadOnlyComposable get() = LocalNearPalette.current

val BrandAccent: Color @Composable @ReadOnlyComposable get() = palette.accent
val BrandSky: Color @Composable @ReadOnlyComposable get() = palette.sky
val BrandSlate: Color @Composable @ReadOnlyComposable get() = palette.slate
val BrandBlue: Color @Composable @ReadOnlyComposable get() = palette.orange

val AppBackground: Color @Composable @ReadOnlyComposable get() = palette.background
val AppSurface: Color @Composable @ReadOnlyComposable get() = palette.surface
val CardBg: Color @Composable @ReadOnlyComposable get() = palette.card
val CardBorder: Color @Composable @ReadOnlyComposable get() = palette.cardBorder
val InnerBg: Color @Composable @ReadOnlyComposable get() = palette.inner
val TextPrimary: Color @Composable @ReadOnlyComposable get() = palette.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = palette.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = palette.textMuted

val AccentContainer: Color @Composable @ReadOnlyComposable get() = palette.accentContainer
val SkyContainer: Color @Composable @ReadOnlyComposable get() = palette.skyContainer
val SlateContainer: Color @Composable @ReadOnlyComposable get() = palette.slateContainer
val LoginPanel: Color @Composable @ReadOnlyComposable get() = palette.loginPanel
val HeroGradient: List<Color> @Composable @ReadOnlyComposable get() = palette.heroGradient

val StatusGreenText: Color @Composable @ReadOnlyComposable get() = palette.statusGreen
// Same scene colours as the widget: university blue, free orange, work lavender.
val AccentUniversity: Color @Composable @ReadOnlyComposable get() = palette.university
val AccentFree: Color @Composable @ReadOnlyComposable get() = palette.orange
val AccentWork: Color @Composable @ReadOnlyComposable get() = palette.slate

val FreeSurface: Color @Composable @ReadOnlyComposable get() = palette.freeSurface
val BusySurface: Color @Composable @ReadOnlyComposable get() = palette.busySurface
val BusyInk: Color @Composable @ReadOnlyComposable get() = palette.busyInk
