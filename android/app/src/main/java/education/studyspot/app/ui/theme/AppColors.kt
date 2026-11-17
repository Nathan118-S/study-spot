package education.studyspot.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand colors beyond Material roles, for the 60/20/20 rule. Read them with
 * `StudySpotTheme.colors` (or `LocalAppColors.current`):
 *
 * - [base]: 60%, the canvas behind everything (== colorScheme.background)
 * - [surface]: cards and rows, lifted from the base (== colorScheme.surface)
 * - [secondary]: 20%, navy for structure: titles, icons, chips, nav
 * - [accent]: 20%, orange for actions and highlights (== colorScheme.primary)
 */
@Immutable
data class AppColors(
    val base: Color,
    val surface: Color,
    val secondary: Color,
    val accent: Color,
    val onAccent: Color,
    /** Navy tint for chips / pills / secondary buttons. */
    val secondaryTint: Color,
    val text: Color,
    val mutedText: Color,
    val border: Color,
    val danger: Color,
    val success: Color,
    val dueToday: Color,
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    base = BaseLight, surface = SurfaceLight, secondary = NavyLight, accent = Orange, onAccent = Color.White,
    secondaryTint = NavyTintLight, text = InkLight, mutedText = MutedInkLight, border = BorderLight,
    danger = RedLight, success = GreenLight, dueToday = DueTodayLight, isDark = false,
)

val DarkAppColors = AppColors(
    base = BaseDark, surface = SurfaceDark, secondary = NavyDark, accent = Orange, onAccent = Color.White,
    secondaryTint = NavyTintDark, text = InkDark, mutedText = MutedInkDark, border = BorderDark,
    danger = RedDark, success = GreenDark, dueToday = DueTodayDark, isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
