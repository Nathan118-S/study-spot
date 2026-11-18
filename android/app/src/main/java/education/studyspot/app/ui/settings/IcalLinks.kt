package education.studyspot.app.ui.settings

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import education.studyspot.app.model.IcalFeed
import education.studyspot.app.model.IcalImportResult
import education.studyspot.app.model.IcalKind
import java.io.InputStream
import java.time.Duration
import java.time.Instant

/**
 * Text and formatting for calendar links (a Google Calendar's public URL or
 * secret iCal address, or Blackboard's external calendar link;
 * server/src/functions/ical.js), shared by the guides ([IcalGuide]), the lists in
 * Settings > Google and Settings > Blackboard ([IcalLinksSection]) and the
 * calendar-file import ([IcalImportFileDialog]).
 */
object IcalLinks {
    /** Opened by step 1 of the Google guide. */
    const val GOOGLE_CALENDAR_SETTINGS_URL = "https://calendar.google.com/calendar/r/settings"

    /** Opened by the calendar-file import (Google Calendar > Settings > Import & export). */
    const val GOOGLE_CALENDAR_EXPORT_URL = "https://calendar.google.com/calendar/r/settings/export"

    const val URL_PLACEHOLDER = "https://calendar.google.com/calendar/embed?src=…"

    const val BLACKBOARD_URL_PLACEHOLDER = "https://…/webapps/calendar/calendarFeed/…/learn.ics"

    /** The largest file icalImportFile accepts. */
    const val MAX_IMPORT_BYTES = 10 * 1024 * 1024

    /** What the file picker offers for a calendar-file import. */
    val IMPORT_MIME_TYPES = arrayOf("text/calendar", "application/zip", "application/octet-stream")

    const val STEPS = 5

    /** The number of steps in each guide ("Step N of …"). */
    fun steps(kind: IcalKind): Int = when (kind) {
        IcalKind.Google -> STEPS
        IcalKind.Blackboard -> 3
    }

    fun guideTitle(kind: IcalKind): String = when (kind) {
        IcalKind.Google -> "Add your Google Calendar with a link"
        IcalKind.Blackboard -> "Add your Blackboard calendar with a link"
    }

    /** The second line of the guide's "You're all set" screen. */
    fun doneFooter(kind: IcalKind): String = when (kind) {
        IcalKind.Google -> "Study Spot checks this calendar for changes every couple of hours and whenever you sync. Add each class calendar the same way."
        IcalKind.Blackboard -> "Study Spot checks this calendar for changes every couple of hours and whenever you sync."
    }

    /** The links shown in Settings > Google or Settings > Blackboard. */
    fun feeds(all: List<IcalFeed>, kind: IcalKind): List<IcalFeed> = all.filter { it.feedKind == kind }

    /** The result of a calendar-file import: "Imported N assignments from A and B." or "No new assignments found." */
    fun importFileMessage(result: IcalImportResult): String {
        if (result.imported <= 0) return "No new assignments found."
        val names = result.calendars.filter { it.imported > 0 }.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
        val what = if (result.imported == 1) "1 assignment" else "${result.imported} assignments"
        return if (names.isEmpty()) "Imported $what." else "Imported $what from ${joinNames(names)}."
    }

    /** "A", "A and B", "A, B and C". */
    fun joinNames(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** Reads at most [max] bytes; null when the stream holds more (the file is too large). */
    fun readCapped(input: InputStream, max: Int = MAX_IMPORT_BYTES): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) return null
        }
        return out.toByteArray()
    }

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
