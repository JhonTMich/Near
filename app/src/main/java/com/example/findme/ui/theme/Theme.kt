package com.example.findme.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.findme.data.local.AppAccent

@Composable
fun brandAccent(accent: AppAccent): Color = when (accent) {
    AppAccent.Indigo -> BrandAccent
    AppAccent.Mint -> BrandSky
    AppAccent.Rose -> BrandSlate
}

private fun brandColors(p: NearPalette) = darkColorScheme(
    primary = p.accent, onPrimary = BrandNight,
    primaryContainer = p.accentContainer, onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = p.sky, onSecondary = Color(0xFF0E2B25),
    secondaryContainer = p.skyContainer, onSecondaryContainer = Color(0xFFC8F2E6),
    tertiary = p.slate, onTertiary = Color(0xFF241A45),
    tertiaryContainer = p.slateContainer, onTertiaryContainer = Color(0xFFE8DDFF),
    background = p.background, onBackground = p.textPrimary,
    surface = p.surface, onSurface = p.textPrimary,
    surfaceVariant = p.inner, onSurfaceVariant = p.textSecondary,
    surfaceContainerLowest = Color(0xFF1A1C36),
    surfaceContainerLow = Color(0xFF232747),
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.card,
    surfaceContainerHighest = p.inner,
    outline = Color(0xFF6E78A6), outlineVariant = p.cardBorder,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = BrandCream, inverseOnSurface = BrandNight, inversePrimary = BrandIconBlue,
    surfaceTint = p.accent
)

@Composable
fun NearTheme(
    accent: AppAccent = AppAccent.Indigo,
    content: @Composable () -> Unit
) {
    // Persisted enum names remain compatible with the previous appearance settings.
    val primary by animateColorAsState(brandAccent(accent), tween(250), label = "themeAccent")
    val container by animateColorAsState(when (accent) {
        AppAccent.Indigo -> AccentContainer
        AppAccent.Mint -> SkyContainer
        AppAccent.Rose -> SlateContainer
    }, tween(250), label = "themeContainer")
    val palette = DarkPalette
    CompositionLocalProvider(LocalNearPalette provides palette) {
        MaterialTheme(
            colorScheme = brandColors(palette).copy(primary = primary, primaryContainer = container),
            typography = Typography,
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)),
            content = content
        )
    }
}
