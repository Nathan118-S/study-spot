package education.studyspot.app.ui.settings

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import education.studyspot.app.model.IcalFeed
import java.time.Duration
import java.time.Instant

/**
 * Text and formatting for Google Calendar links (the calendar's public URL,
 * or its secret iCal address; server/src/functions/ical.js), shared by the guide
 * ([IcalGuide]) and the list in Settings > Google ([IcalLinksSection]).
 */
object IcalLinks {
    /** Opened by step 1 of the guide. */
    const val GOOGLE_CALENDAR_SETTINGS_URL = "https://calendar.google.com/calendar/r/settings"

    const val URL_PLACEHOLDER = "https://calendar.google.com/calendar/embed?src=…"

    const val STEPS = 5

    /** A link's secondary line in Settings: the last sync, or its error in red. */
    data class Status(val text: String, val error: Boolean)

    fun status(feed: IcalFeed, now: Instant = Instant.now()): Status {
        feed.lastError?.trim()?.takeIf { it.isNotEmpty() }?.let { return Status("Couldn't sync: $it", error = true) }
        val synced = feed.lastSyncedAt ?: return Status("Not synced yet", error = false)
        val text = buildString {
            append("Synced ").append(ago(synced, now))
            if (feed.lastImported > 0) append(" · ").append(feed.lastImported).append(" new")
        }
        return Status(text, error = false)
    }

    /** "just now", "5 min ago", "3 hr ago", "1 day ago", "4 days ago". */
    fun ago(then: Instant, now: Instant): String {
        val minutes = Duration.between(then, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} hr ago"
            else -> (minutes / 60 / 24).let { d -> if (d == 1L) "1 day ago" else "$d days ago" }
        }
    }

    /** The first line of the guide's "You're all set" screen. */
    fun importedMessage(imported: Int, label: String): String = when {
        imported <= 0 -> "No assignments found yet — new ones will appear automatically."
        imported == 1 -> "Imported 1 assignment from $label."
        else -> "Imported $imported assignments from $label."
    }

    /** "Choose **Settings and sharing**" -> the starred parts in semibold. */
    fun bold(markup: String): AnnotatedString = buildAnnotatedString {
        markup.split("**").forEachIndexed { i, part ->
            if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
        }
    }
}
