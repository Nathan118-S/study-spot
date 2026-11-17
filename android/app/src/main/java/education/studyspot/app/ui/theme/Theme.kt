package education.studyspot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import education.studyspot.app.data.ThemeSettings

// Dynamic color is deliberately OFF: the app always uses the Study Spot palette.

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDEBD3),
    onPrimaryContainer = Color(0xFF5A3300),
    inversePrimary = Color(0xFFFFB866),
    secondary = NavyLight,
    onSecondary = Color.White,
    secondaryContainer = NavyTintLight,
    onSecondaryContainer = NavyLight,
    tertiary = NavyLight,
    onTertiary = Color.White,
    tertiaryContainer = NavyTintLight,
    onTertiaryContainer = NavyLight,
    background = BaseLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = NavyTintLight,
    onSurfaceVariant = MutedInkLight,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2B2622),
    inverseOnSurface = Color(0xFFF8F6F3),
    error = RedLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFE3E1),
    onErrorContainer = Color(0xFF8A1710),
    outline = Color(0xFFB9BFCC),
    outlineVariant = BorderLight,
    scrim = Color.Black,
    surfaceBright = SurfaceLight,
    surfaceDim = Color(0xFFEDEAE6),
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = Color(0xFFFCFAF7),
    surfaceContainer = Color(0xFFF8F6F2),
    surfaceContainerHigh = Color(0xFFF3F1ED),
    surfaceContainerHighest = Color(0xFFEDEBE7),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2E0C),
    onPrimaryContainer = Color(0xFFFFD9A8),
    inversePrimary = OrangeDeep,
    secondary = NavyDark,
    onSecondary = Color(0xFF14203A),
    secondaryContainer = NavyTintDark,
    onSecondaryContainer = NavyDark,
    tertiary = NavyDark,
    onTertiary = Color(0xFF14203A),
    tertiaryContainer = NavyTintDark,
    onTertiaryContainer = NavyDark,
    background = BaseDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = NavyTintDark,
    onSurfaceVariant = MutedInkDark,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFEDE9E4),
    inverseOnSurface = Color(0xFF221D18),
    error = RedDark,
    onError = Color.White,
    errorContainer = Color(0xFF5C1511),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF5A5F6B),
    outlineVariant = BorderDark,
    scrim = Color.Black,
    surfaceBright = Color(0xFF3A332D),
    surfaceDim = BaseDark,
    surfaceContainerLowest = Color(0xFF110E0C),
    surfaceContainerLow = Color(0xFF1B1714),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = Color(0xFF2A2420),
    surfaceContainerHighest = Color(0xFF332C27),
)

/** Whether animations are enabled in Settings > Appearance. */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/** Resolves the effective dark mode from the in-app setting (null = follow the system). */
@Composable
fun ThemeSettings.isDark(): Boolean = forcedDark ?: isSystemInDarkTheme()

@Composable
fun StudySpotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    animations: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors,
        LocalAnimationsEnabled provides animations,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = StudySpotTypography,
            shapes = StudySpotShapes,
            content = content,
        )
    }
}

/** Theme accessors: `StudySpotTheme.colors.secondary`, etc. */
object StudySpotTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val animations: Boolean
        @Composable @ReadOnlyComposable get() = LocalAnimationsEnabled.current
}
