package education.studyspot.app.ui.theme

import androidx.compose.ui.graphics.Color

// Study Spot 60 / 20 / 20 palette (ios/StudySpot/State/ThemeStore.swift, src/index.css).
//
// 60% dominant:  warm neutral backgrounds (Base, with Surface for cards)
// 20% secondary: deep navy "ink" for structure: titles, icons, chips,
//                secondary buttons, navigation (Navy)
// 20% accent:    Study Spot orange for actions and highlights: main buttons,
//                selection, progress, switches (Orange)
// Status colors (overdue red, done green, due-today orange) are semantic and
// sit outside the split.

/** The brand accent. Same in light and dark. */
val Orange = Color(0xFFF19D37)
val OrangeDeep = Color(0xFFE07F22)
val OrangeLight = Color(0xFFFFC857)

val BaseLight = Color(0xFFFEFDFB)
val BaseDark = Color(0xFF15120F)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF221D18)
val NavyLight = Color(0xFF1F2B47)
val NavyDark = Color(0xFFA9B8DC)

// Text
val InkLight = Color(0xFF151B28)
val InkDark = Color(0xFFF8F6F3)
val MutedInkLight = Color(0xFF656F86)
val MutedInkDark = Color(0xFF9198AC)

// Navy tints (chips, secondary containers) and borders
val NavyTintLight = Color(0xFFEBEEF4)
val NavyTintDark = Color(0xFF262A36)
val BorderLight = Color(0xFFE0E3EB)
val BorderDark = Color(0xFF2E3138)

// Status
val RedLight = Color(0xFFFF3B30)
val RedDark = Color(0xFFFF453A)
val GreenLight = Color(0xFF34C759)
val GreenDark = Color(0xFF30D158)
/** "Due today" — the system orange, distinct from the brand accent. */
val DueTodayLight = Color(0xFFFF9500)
val DueTodayDark = Color(0xFFFF9F0A)
