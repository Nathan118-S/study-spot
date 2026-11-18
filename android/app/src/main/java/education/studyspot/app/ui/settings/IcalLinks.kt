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
 * secret iCal address, or the calendar link from Blackboard, Canvas, Schoology or
 * Brightspace; server/src/functions/ical.js), shared by the guides ([IcalGuide]),
 * the lists in Settings > Google, Settings > Blackboard ([IcalLinksSection]) and
 * Settings > Canvas, Schoology & Brightspace ([LmsSettingsScreen]) and the
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

    const val CANVAS_URL_PLACEHOLDER = "https://…/feeds/calendars/user_….ics"

    const val SCHOOLOGY_URL_PLACEHOLDER = "…/calendar/feed/ical/…/ical.ics"

    const val BRIGHTSPACE_URL_PLACEHOLDER = "…/d2l/le/calendar/feed/user/feed.ics?token=…"

    const val STEPS = 5

    /** The number of steps in each guide ("Step N of …"). */
    fun steps(kind: IcalKind): Int = when (kind) {
        IcalKind.Google -> STEPS
        else -> 3
    }

    fun guideTitle(kind: IcalKind): String = when (kind) {
        IcalKind.Google -> "Add your Google Calendar with a link"
        else -> "Add your ${kind.label} calendar with a link"
    }

    /** The second line of the guide's "You're all set" screen. */
    fun doneFooter(kind: IcalKind): String = when (kind) {
        IcalKind.Google -> "Study Spot checks this calendar for changes every couple of hours and whenever you sync. Add each class calendar the same way."
        else -> "Study Spot checks this calendar for changes every couple of hours and whenever you sync."
    }

    /** The paste field's label. */
    fun urlLabel(kind: IcalKind): String = when (kind) {
        IcalKind.Google -> "Calendar link"
        else -> "${kind.label} calendar link"
    }

    fun urlPlaceholder(kind: IcalKind): String = when (kind) {
        IcalKind.Google -> URL_PLACEHOLDER
        IcalKind.Blackboard -> BLACKBOARD_URL_PLACEHOLDER
        IcalKind.Canvas -> CANVAS_URL_PLACEHOLDER
        IcalKind.Schoology -> SCHOOLOGY_URL_PLACEHOLDER
        IcalKind.Brightspace -> BRIGHTSPACE_URL_PLACEHOLDER
    }

    /** The error when "Add calendar" is pressed with an empty field. */
    fun emptyUrlError(kind: IcalKind): String = when (kind) {
        IcalKind.Google -> "Paste your calendar's link first."
        else -> "Paste your ${kind.label} calendar link first."
    }

    /** The paste step's "auto" import choice. */
    fun autoModeTitle(kind: IcalKind): String = when (kind) {
        IcalKind.Canvas, IcalKind.Brightspace -> "Only assignments and due dates"
        else -> "Only events that look like assignments"
    }

    /** The note under the paste step (and the copy-link step). */
    fun privacyNote(kind: IcalKind): String = when (kind) {
        IcalKind.Google -> "Study Spot only reads this calendar. If you turn off public access later, Study Spot stops getting updates from it."
        IcalKind.Blackboard -> "Keep this link private. Anyone with it can see your Blackboard calendar. If you get a new link in Blackboard, add the new one here."
        else -> "Keep this link private. Anyone with it can see your ${kind.label} calendar. If you reset it in ${kind.label}, add the new one here."
    }

    /** The links shown in Settings > Google or Settings > Blackboard. */
    fun feeds(all: List<IcalFeed>, kind: IcalKind): List<IcalFeed> = all.filter { it.feedKind == kind }

    /** The links shown in Settings > Canvas, Schoology & Brightspace. */
    fun lmsFeeds(all: List<IcalFeed>): List<IcalFeed> = all.filter { it.feedKind.isLms }

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

    /** With [showKind], prefixed with the system's name: "Canvas · Synced 5 min ago". */
    fun status(feed: IcalFeed, now: Instant = Instant.now(), showKind: Boolean = false): Status {
        val prefix = if (showKind) "${feed.feedKind.label} · " else ""
        feed.lastError?.trim()?.takeIf { it.isNotEmpty() }?.let { return Status("${prefix}Couldn't sync: $it", error = true) }
        val synced = feed.lastSyncedAt ?: return Status("${prefix}Not synced yet", error = false)
        val text = buildString {
            append(prefix).append("Synced ").append(ago(synced, now))
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
