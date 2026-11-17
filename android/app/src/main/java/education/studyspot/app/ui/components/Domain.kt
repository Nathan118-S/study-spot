package education.studyspot.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import education.studyspot.app.logic.StreakStatus
import education.studyspot.app.model.Priority
import education.studyspot.app.ui.theme.StudySpotTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Domain styling shared by the main screens (ios/StudySpot/Components/Components.swift: Domain styling).

/** High = red, medium = due-today orange, low = green (status colors, outside the 60/20/20 split). */
@Composable
fun priorityColor(p: Priority): Color {
    val c = StudySpotTheme.colors
    return when (p) {
        Priority.High -> c.danger
        Priority.Medium -> c.dueToday
        Priority.Low -> c.success
    }
}

/** Letter-grade tint: A green, B teal, C/D orange, F red. */
@Composable
fun gradeColor(letter: String?): Color {
    val c = StudySpotTheme.colors
    return when (letter?.firstOrNull()?.uppercaseChar()) {
        'A' -> c.success
        'B' -> if (c.isDark) Color(0xFF63E6E2) else Color(0xFF00A39B)
        'C', 'D' -> c.dueToday
        'F' -> c.danger
        else -> c.mutedText
    }
}

@Composable
fun StreakStatus.color(): Color {
    val c = StudySpotTheme.colors
    return when (this) {
        StreakStatus.Active -> c.success
        StreakStatus.AtRisk -> c.dueToday
        StreakStatus.Broken -> c.danger
        StreakStatus.Empty -> c.mutedText
    }
}

@Composable
fun StreakStatus.flameColor(): Color {
    val c = StudySpotTheme.colors
    return when (this) {
        StreakStatus.Active -> c.accent
        StreakStatus.AtRisk -> Color(0xFFFFC857)
        else -> c.mutedText
    }
}

/** A thin rounded progress bar (0–100), accent filled, green when complete. */
@Composable
fun ProgressBar(percent: Double, modifier: Modifier = Modifier, color: Color? = null) {
    val c = StudySpotTheme.colors
    val target = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val fraction by animateFloatAsState(
        target,
        animationSpec = if (StudySpotTheme.animations) tween(350) else tween(0),
        label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(c.secondaryTint)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(CircleShape)
                .background(color ?: if (percent >= 100) c.success else c.accent),
        )
    }
}

/** A surface card with an optional navy section title, for form-like screens. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null || trailing != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = StudySpotTheme.colors.secondary,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, StudySpotTheme.colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                if (contentPadding) Modifier.padding(14.dp) else Modifier,
                verticalArrangement = Arrangement.spacedBy(if (contentPadding) 10.dp else 0.dp),
                content = content,
            )
        }
    }
}

/** The UI locale, read so composables update when it changes. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT

/** "Tuesday, Nov 17, 3:00 PM" (iOS DateText.long). */
fun DateText.long(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (instant == null) return "No due date"
    val dt = instant.atZone(zone)
    val day = dt.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    return "$day, ${dt.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}"
}

/** "just now", "5m ago", "3h ago", "2d ago" (iOS DateText.ago). */
fun DateText.ago(instant: Instant?, now: Instant = Instant.now()): String {
    if (instant == null) return ""
    val m = (now.epochSecond - instant.epochSecond) / 60
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 60 * 24 -> "${m / 60}h ago"
        else -> "${m / 60 / 24}d ago"
    }
}

fun DateText.time(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
    instant?.atZone(zone)?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).orEmpty()

/** A stat tile with a tinted leading icon (iOS StatCard with a symbol). */
@Composable
fun IconStatTile(label: String, value: String, icon: ImageVector, iconTint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, StudySpotTheme.colors.border),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp)) }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StudySpotTheme.colors.secondary)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
