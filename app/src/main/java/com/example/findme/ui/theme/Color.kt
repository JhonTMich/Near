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
    /** Text on [freeSurface]: the mint pills and the free-time bars. */
    val freeInk: Color,
    /** The bands of the top bar and the bottom navigation. */
    val bar: Color,
    /** The violet glow that lights the top of every screen. */
    val glow: Color,
    val heroBorder: Color,
    /** Light cards float on a soft shadow; dark ones only need their border. */
    val isLight: Boolean,
)

val DarkPalette = NearPalette(
    accent = Color(0xFFC8B5FF), sky = Color(0xFF9BE3A8), slate = Color(0xFFCDB8F5), orange = Color(0xFFF0C48E),
    background = Color(0xFF14121C), surface = Color(0xFF1C1928), card = Color(0xFF1F1B2C),
    cardBorder = Color(0xFF353049), inner = Color(0xFF282338),
    textPrimary = Color(0xFFF4F1FA), textSecondary = Color(0xFFCAC3DB), textMuted = Color(0xFF9D96B2),
    accentContainer = Color(0xFF3A2F5E), skyContainer = Color(0xFF233A2B), slateContainer = Color(0xFF2A2440),
    heroGradient = listOf(Color(0xFF3D3063), Color(0xFF2B2445)),
    statusGreen = Color(0xFF8EE09C), university = Color(0xFFC8B5FF),
    loginPanel = Color(0xFF1C1928),
    freeSurface = Color(0xFFCDEFD1), busySurface = Color(0xFFFFD7DD), busyInk = Color(0xFFC42B46),
    freeInk = Color(0xFF1C6232), bar = Color(0xFF1A1724), glow = Color(0xFF3A2A66),
    heroBorder = Color(0xFF51437E), isLight = false,
)

val LightPalette = NearPalette(
    accent = Color(0xFF5B3FD1), sky = Color(0xFF2E7D3D), slate = Color(0xFF6A4CC9), orange = Color(0xFF9A5A16),
    background = Color(0xFFF5F3FB), surface = Color(0xFFFBFAFF), card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE9E4F4), inner = Color(0xFFF3F0FB),
    textPrimary = Color(0xFF1E1A2D), textSecondary = Color(0xFF5B556D), textMuted = Color(0xFF857F98),
    accentContainer = Color(0xFFE7DFFD), skyContainer = Color(0xFFDDF3DF), slateContainer = Color(0xFFF1ECFD),
    heroGradient = listOf(Color(0xFFE9E0FD), Color(0xFFDCD0F8)),
    statusGreen = Color(0xFF2E8B40), university = Color(0xFF5B3FD1),
    loginPanel = Color(0xFFFBFAFF),
    freeSurface = Color(0xFFE0F4E1), busySurface = Color(0xFFFDE1E5), busyInk = Color(0xFFC42B46),
    freeInk = Color(0xFF256B34), bar = Color(0xFFFBFAFF), glow = Color(0xFFE4DAFC),
    heroBorder = Color(0xFFD8CCF6), isLight = true,
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
val FreeInk: Color @Composable @ReadOnlyComposable get() = palette.freeInk
val BarBg: Color @Composable @ReadOnlyComposable get() = palette.bar
val BackdropGlow: Color @Composable @ReadOnlyComposable get() = palette.glow
val HeroBorder: Color @Composable @ReadOnlyComposable get() = palette.heroBorder
val IsLightTheme: Boolean @Composable @ReadOnlyComposable get() = palette.isLight
