package education.studyspot.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import education.studyspot.app.R
import education.studyspot.app.ui.theme.StudySpotTheme

/** "#rrggbb" / "#rgb" / "#aarrggbb" -> Color, or [fallback]. */
fun parseHexColor(hex: String?, fallback: Color = Color(0xFF94A3B8)): Color {
    var s = hex?.trim()?.removePrefix("#") ?: return fallback
    if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
    val v = s.toLongOrNull(16) ?: return fallback
    return when (s.length) {
        6 -> Color(0xFF000000 or v)
        8 -> Color(v)
        else -> fallback
    }
}

/** The app logo: the graduation cap on the icon's cream tile. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.225f))
            .background(Color(0xFFFFFAF1)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.logo_cap), contentDescription = "Study Spot", modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Centered icon + title + message, for empty lists and errors. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = StudySpotTheme.colors.secondary.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        action?.invoke()
    }
}

/**
 * Placeholder body for screens another agent will build. Keep the signature
 * of the calling screen; replace the call to this.
 */
@Composable
fun StubContent(title: String, description: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(icon = Icons.Outlined.Construction, title = title, message = description)
    }
}

/** A small rounded label (source, "Overdue", type). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = StudySpotTheme.colors.secondary,
    filled: Boolean = false,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Bright fills (green, yellow) need dark text to stay readable.
        val fg = when {
            !filled -> color
            color.luminance() > 0.4f -> Color(0xFF10151F)
            else -> Color.White
        }
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** A dashboard-style number tile. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, tint: Color = StudySpotTheme.colors.secondary) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, StudySpotTheme.colors.border),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

/** Segmented buttons in brand colors: orange-tinted selection, navy labels (dashboard tabs, priority, timer phase). */
@Composable
fun brandSegmentedColors() = androidx.compose.material3.SegmentedButtonDefaults.colors(
    activeContainerColor = StudySpotTheme.colors.accent.copy(alpha = 0.16f),
    activeContentColor = StudySpotTheme.colors.secondary,
    inactiveContentColor = StudySpotTheme.colors.secondary,
)

/** Filter chips in brand colors, matching [brandSegmentedColors]. */
@Composable
fun brandFilterChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    selectedContainerColor = StudySpotTheme.colors.accent.copy(alpha = 0.16f),
    selectedLabelColor = StudySpotTheme.colors.secondary,
    selectedLeadingIconColor = StudySpotTheme.colors.accent,
    labelColor = StudySpotTheme.colors.secondary,
)
