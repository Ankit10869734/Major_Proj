package com.moodmix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.moodmix.app.domain.model.OccasionType
import com.moodmix.app.domain.model.RegionalThemeState

private val MoodMixDarkColorScheme = darkColorScheme(
    primary = MintPrimary,
    onPrimary = BackgroundDark,
    primaryContainer = Color(0xFF243310),
    onPrimaryContainer = MintPrimary,
    secondary = BrandPurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF33173A),
    onSecondaryContainer = BrandPurpleAccent,
    tertiary = MintPrimaryDark,
    onTertiary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = ErrorRed,
    onError = Color.White,
    surfaceTint = MintPrimary
)

private val DiwaliColorScheme = darkColorScheme(
    primary = DiwaliGold,
    onPrimary = BackgroundDark,
    primaryContainer = Color(0xFF4E3800),
    onPrimaryContainer = DiwaliGold,
    secondary = DiwaliOrange,
    onSecondary = BackgroundDark,
    secondaryContainer = Color(0xFF4E2200),
    onSecondaryContainer = DiwaliOrange,
    tertiary = DiwaliWarmPink,
    onTertiary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = ErrorRed,
    onError = Color.White,
    surfaceTint = DiwaliGold
)

@Composable
fun MoodMixTheme(
    regionalThemeState: RegionalThemeState = RegionalThemeState(),
    content: @Composable () -> Unit
) {
    val activeColorScheme = when {
        regionalThemeState.isFestiveActive -> {
            when (regionalThemeState.activeOccasion?.type) {
                OccasionType.DIWALI -> DiwaliColorScheme
                else -> MoodMixDarkColorScheme
            }
        }
        else -> MoodMixDarkColorScheme
    }

    MaterialTheme(
        colorScheme = activeColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
