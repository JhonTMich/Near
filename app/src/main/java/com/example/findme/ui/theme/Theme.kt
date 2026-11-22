package com.example.findme.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.findme.data.local.AppTheme

private fun darkColors(p: NearPalette) = darkColorScheme(
    primary = p.accent, onPrimary = BrandNight,
    primaryContainer = p.accentContainer, onPrimaryContainer = Color(0xFFF7F0E5),
    secondary = p.sky, onSecondary = Color(0xFF0E2B25),
    secondaryContainer = p.skyContainer, onSecondaryContainer = Color(0xFFDAEDA0),
    tertiary = p.slate, onTertiary = Color(0xFF241A45),
    tertiaryContainer = p.slateContainer, onTertiaryContainer = Color(0xFFE8DDFF),
    background = p.background, onBackground = p.textPrimary,
    surface = p.surface, onSurface = p.textPrimary,
    surfaceVariant = p.inner, onSurfaceVariant = p.textSecondary,
    surfaceContainerLowest = Color(0xFF17131F),
    surfaceContainerLow = Color(0xFF211B2A),
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.card,
    surfaceContainerHighest = p.inner,
    outline = Color(0xFF9585A4), outlineVariant = p.cardBorder,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = BrandCream, inverseOnSurface = BrandNight, inversePrimary = BrandIconBlue,
    surfaceTint = p.accent
)

private fun lightColors(p: NearPalette) = lightColorScheme(
    primary = p.accent, onPrimary = Color.White,
    primaryContainer = p.accentContainer, onPrimaryContainer = Color(0xFF332149),
    secondary = p.sky, onSecondary = Color.White,
    secondaryContainer = p.skyContainer, onSecondaryContainer = Color(0xFF344B12),
    tertiary = p.slate, onTertiary = Color.White,
    tertiaryContainer = p.slateContainer, onTertiaryContainer = Color(0xFF2C1A5C),
    background = p.background, onBackground = p.textPrimary,
    surface = p.surface, onSurface = p.textPrimary,
    surfaceVariant = p.inner, onSurfaceVariant = p.textSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9F6),
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.card,
    surfaceContainerHighest = p.inner,
    outline = Color(0xFF84758F), outlineVariant = p.cardBorder,
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    inverseSurface = BrandNight, inverseOnSurface = BrandCream, inversePrimary = Color(0xFFB9A0FA),
    surfaceTint = p.accent
)

@Composable
fun NearTheme(
    theme: AppTheme = AppTheme.Dark,
    content: @Composable () -> Unit
) {
    val palette = if (theme == AppTheme.Light) LightPalette else DarkPalette
    val colors: ColorScheme = if (theme == AppTheme.Light) lightColors(palette) else darkColors(palette)
    CompositionLocalProvider(LocalNearPalette provides palette) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)),
            content = content
        )
    }
}
