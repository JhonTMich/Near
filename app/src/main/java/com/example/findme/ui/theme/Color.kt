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
)

val DarkPalette = NearPalette(
    accent = Color(0xFF8FA6D6), sky = Color(0xFF7FD1B9), slate = Color(0xFFB79BEA), orange = Color(0xFFF2B36B),
    background = Color(0xFF1E2140), surface = Color(0xFF262A4C), card = Color(0xFF2B3157),
    cardBorder = Color(0xFF3B4470), inner = Color(0xFF343B66),
    textPrimary = BrandInk, textSecondary = Color(0xFFC9CCE0), textMuted = Color(0xFF9AA0BD),
    accentContainer = Color(0xFF34406A), skyContainer = Color(0xFF224A44), slateContainer = Color(0xFF3E3366),
    heroGradient = listOf(Color(0xFF34406E), Color(0xFF3B3F78), Color(0xFF2B3157)),
    statusGreen = Color(0xFF7FD8A0), university = Color(0xFF8FB8F0),
)

// Light theme: warm paper background, white cards and darker accents so text keeps its contrast.
val LightPalette = NearPalette(
    accent = Color(0xFF4A62A3), sky = Color(0xFF1F7A63), slate = Color(0xFF6B4FB3), orange = Color(0xFFB0640F),
    background = Color(0xFFF5F3EE), surface = Color(0xFFFFFFFF), card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFDDDFEA), inner = Color(0xFFEEF0F7),
    textPrimary = BrandNight, textSecondary = Color(0xFF4A4F6E), textMuted = Color(0xFF6C7190),
    accentContainer = Color(0xFFDCE4F7), skyContainer = Color(0xFFD3EFE6), slateContainer = Color(0xFFE9E0FA),
    heroGradient = listOf(Color(0xFFE2E8F7), Color(0xFFE8E3F7), Color(0xFFF4F5FB)),
    statusGreen = Color(0xFF237A47), university = Color(0xFF2F68B0),
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
val HeroGradient: List<Color> @Composable @ReadOnlyComposable get() = palette.heroGradient

val StatusGreenText: Color @Composable @ReadOnlyComposable get() = palette.statusGreen
// Same scene colours as the widget: university blue, free orange, work lavender.
val AccentUniversity: Color @Composable @ReadOnlyComposable get() = palette.university
val AccentFree: Color @Composable @ReadOnlyComposable get() = palette.orange
val AccentWork: Color @Composable @ReadOnlyComposable get() = palette.slate
