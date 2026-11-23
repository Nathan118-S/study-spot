package education.studyspot.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.ui.theme.StudySpotTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The small offline pill under the main screens' top bar: "Offline — changes
 * will sync", how many changes are waiting, and when the data was last
 * updated (while showing cached data). Hidden when online and up to date.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val repo = LocalAppContainer.current.repository
    val offline by repo.isOffline.collectAsStateWithLifecycle()
    val pending by repo.pendingChanges.collectAsStateWithLifecycle()
    val cached by repo.showingCachedData.collectAsStateWithLifecycle()
    val lastUpdated by repo.lastUpdated.collectAsStateWithLifecycle()
    val user by repo.user.collectAsStateWithLifecycle()

    val text = offlineBannerText(offline, pending, cached, lastUpdated)
    AnimatedVisibility(visible = text != null && user?.isDemo != true, modifier = modifier) {
        val colors = StudySpotTheme.colors
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.secondary.copy(alpha = 0.1f),
                contentColor = colors.secondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (offline) Icons.Outlined.CloudOff else Icons.Outlined.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The banner's text, or null when there's nothing to say. */
internal fun offlineBannerText(
    offline: Boolean,
    pending: Int,
    cached: Boolean,
    lastUpdated: Instant?,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val parts = buildList {
        if (offline) add("Offline — changes will sync")
        if (pending > 0) add(if (pending == 1) "1 change waiting to sync" else "$pending changes waiting to sync")
        if ((offline || cached) && lastUpdated != null) add("Last updated ${formatUpdated(lastUpdated, now, zone)}")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatUpdated(at: Instant, now: Instant, zone: ZoneId): String {
    val time = at.atZone(zone)
    val clock = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val today: LocalDate = now.atZone(zone).toLocalDate()
    return when (time.toLocalDate()) {
        today -> clock
        today.minusDays(1) -> "yesterday $clock"
        else -> time.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}
