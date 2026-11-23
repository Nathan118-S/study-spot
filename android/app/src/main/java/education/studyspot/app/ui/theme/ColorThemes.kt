package education.studyspot.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colour themes (Settings > Appearance > Theme), mirroring src/lib/colorThemes.js
 * and ios/StudySpot/State/ColorTheme.swift. [StudySpot] is the default and uses
 * the original palette (Color.kt / Theme.kt) unchanged. The others are derived
 * the same way: a near-white base tinted toward the theme (dark: near-black), a
 * deep ink for structure, and an accent for actions. The choice is stored
 * on-device and in user.data.theme so the website and iOS match.
 */
enum class ColorTheme(
    val id: String,
    val label: String,
    val accentLight: Color,
    val accentDark: Color,
    val onAccentDark: Color,
    /** Light ink (structure, the default's navy) and its dark-mode counterpart. */
    val inkLight: Color,
    val inkDark: Color,
    val baseLight: Color,
    val baseDark: Color,
    val surfaceDark: Color,
    /** HSL inputs (hue in degrees, saturation 0–1) for the derived tints. */
    val accentHue: Float,
    val inkHue: Float,
    val inkSat: Float,
    val baseHue: Float,
    val baseSat: Float,
    val baseDarkHue: Float,
    val baseDarkSat: Float,
) {
    StudySpot("studyspot", "Study Spot", Orange, Orange, Color.White, NavyLight, NavyDark, BaseLight, BaseDark, SurfaceDark,
        33f, 222f, 0.39f, 40f, 0.60f, 30f, 0.17f),
    Ocean("ocean", "Ocean", Color(0xFF2581E4), Color(0xFF3C91EC), Color.White, Color(0xFF17354F), Color(0xFF9DC3E7),
        Color(0xFFFBFDFE), Color(0xFF0F1215), Color(0xFF171C21), 211f, 208f, 0.55f, 205f, 0.60f, 212f, 0.18f),
    Forest("forest", "Forest", Color(0xFF309161), Color(0xFF40B576), Color.White, Color(0xFF204639), Color(0xFFA8DCCB),
        Color(0xFFFDFEFB), Color(0xFF101410), Color(0xFF191F19), 150f, 160f, 0.38f, 80f, 0.45f, 120f, 0.12f),
    Grape("grape", "Grape", Color(0xFF8C56D2), Color(0xFFA674E7), Color.White, Color(0xFF291F47), Color(0xFFB6A6DD),
        Color(0xFFFDFBFE), Color(0xFF120F14), Color(0xFF1C1820), 266f, 256f, 0.40f, 275f, 0.50f, 270f, 0.14f),
    Rose("rose", "Rose", Color(0xFFE74B7A), Color(0xFFEE638D), Color.White, Color(0xFF452132), Color(0xFFDBA9BF),
        Color(0xFFFEFBFB), Color(0xFF150F11), Color(0xFF20181A), 342f, 332f, 0.36f, 350f, 0.60f, 345f, 0.15f),
    Graphite("graphite", "Graphite", Color(0xFF464A53), Color(0xFFCDD0D6), Color(0xFF15171A), Color(0xFF2D3139), Color(0xFFBABFCA),
        Color(0xFFFCFCFD), Color(0xFF111113), Color(0xFF1A1B1E), 220f, 220f, 0.12f, 220f, 0.10f, 220f, 0.06f);

    fun accent(dark: Boolean) = if (dark) accentDark else accentLight
    fun onAccent(dark: Boolean) = if (dark) onAccentDark else Color.White
    fun ink(dark: Boolean) = if (dark) inkDark else inkLight
    fun base(dark: Boolean) = if (dark) baseDark else baseLight
    fun surface(dark: Boolean) = if (dark) surfaceDark else SurfaceLight

    companion object {
        val Default = StudySpot
        fun fromId(id: String?): ColorTheme = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** An ink shade; saturation scales like the website's (relative to the default ink's 39%). */
private fun ColorTheme.inkShade(sat: Float, lightness: Float, dark: Boolean) =
    Color.hsl((inkHue + if (dark) 1f else 0f) % 360f, (sat * inkSat / 0.39f).coerceIn(0f, 1f), lightness)

/** A base-tinted neutral for Material's surface containers. */
private fun ColorTheme.baseShade(satScale: Float, lightness: Float, dark: Boolean) =
    if (dark) Color.hsl(baseDarkHue, (baseDarkSat * satScale).coerceIn(0f, 1f), lightness)
    else Color.hsl(baseHue, (baseSat * satScale).coerceIn(0f, 1f), lightness)

/** Brand colours beyond Material roles for this theme. */
fun ColorTheme.appColors(dark: Boolean): AppColors {
    val default = if (dark) DarkAppColors else LightAppColors
    if (this == ColorTheme.StudySpot) return default
    return default.copy(
        base = base(dark),
        surface = surface(dark),
        secondary = ink(dark),
        accent = accent(dark),
        onAccent = onAccent(dark),
        secondaryTint = if (dark) inkShade(0.18f, 0.18f, true) else inkShade(0.30f, 0.94f, false),
        text = if (dark) InkDark else inkShade(0.30f, 0.12f, false),
        mutedText = if (dark) inkShade(0.14f, 0.62f, true) else inkShade(0.14f, 0.46f, false),
        border = if (dark) inkShade(0.10f, 0.20f, true) else inkShade(0.20f, 0.90f, false),
    )
}

/** The Material scheme for this theme: the default one with the theme's roles swapped in. */
fun ColorTheme.colorScheme(default: ColorScheme, dark: Boolean): ColorScheme {
    if (this == ColorTheme.StudySpot) return default
    val c = appColors(dark)
    val containerSat = if (this == ColorTheme.Graphite) 0.08f else 0.7f
    val onInk = if (dark) inkShade(0.39f, 0.15f, true) else Color.White
    return default.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = Color.hsl(accentHue, containerSat, if (dark) 0.17f else 0.91f),
        onPrimaryContainer = Color.hsl(accentHue, containerSat, if (dark) 0.83f else 0.20f),
        inversePrimary = if (dark) accentLight else accentDark,
        secondary = c.secondary,
        onSecondary = onInk,
        secondaryContainer = c.secondaryTint,
        onSecondaryContainer = c.secondary,
        tertiary = c.secondary,
        onTertiary = onInk,
        tertiaryContainer = c.secondaryTint,
        onTertiaryContainer = c.secondary,
        background = c.base,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.secondaryTint,
        onSurfaceVariant = c.mutedText,
        outline = if (dark) inkShade(0.10f, 0.39f, true) else inkShade(0.20f, 0.76f, false),
        outlineVariant = c.border,
        surfaceBright = if (dark) baseShade(0.8f, 0.20f, true) else c.surface,
        surfaceDim = if (dark) c.base else baseShade(0.27f, 0.92f, false),
        surfaceContainerLowest = if (dark) baseShade(1f, 0.06f, true) else c.surface,
        surfaceContainerLow = if (dark) baseShade(0.9f, 0.09f, true) else baseShade(0.75f, 0.98f, false),
        surfaceContainer = if (dark) c.surface else baseShade(0.55f, 0.96f, false),
        surfaceContainerHigh = if (dark) baseShade(0.85f, 0.15f, true) else baseShade(0.35f, 0.94f, false),
        surfaceContainerHighest = if (dark) baseShade(0.8f, 0.18f, true) else baseShade(0.25f, 0.92f, false),
    )
}
