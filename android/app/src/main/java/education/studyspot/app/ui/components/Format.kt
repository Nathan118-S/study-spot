package education.studyspot.app.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Date formatting shared by the screens (the iOS app's DateText). */
object DateText {
    private val dayMonth get() = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val dayMonthYear get() = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** "Today, 3:00 PM", "Tomorrow, …", "Mar 4, 3:00 PM", or "No due date". */
    fun short(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (instant == null) return "No due date"
        val dt = instant.atZone(zone)
        val today = LocalDate.now(zone)
        val day = when (dt.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> if (dt.year == today.year) dt.format(dayMonth) else dt.format(dayMonthYear)
        }
        return "$day, ${dt.format(time)}"
    }

    fun date(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.atZone(zone)?.format(dayMonthYear) ?: ""
}

/** 25.0 -> "25", 2.5 -> "2.5". */
val Double.clean: String get() = if (this % 1.0 == 0.0) toLong().toString() else toString()
