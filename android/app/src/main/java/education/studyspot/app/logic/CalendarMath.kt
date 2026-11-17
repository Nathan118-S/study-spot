package education.studyspot.app.logic

import education.studyspot.app.model.Assignment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Month-grid helpers for the calendar (Sunday-first weeks, like iOS CalendarScreen). */
object CalendarMath {
    /** Every day shown for [month]: whole Sunday–Saturday weeks covering the month (28–42 days). */
    fun monthGrid(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
    }

    /** Assignments with a due date, grouped by local due day and sorted by time within each day. */
    fun byDay(assignments: List<Assignment>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Assignment>> =
        assignments.filter { it.dueDate != null }
            .sortedBy { it.dueDate }
            .groupBy { it.dueDate!!.atZone(zone).toLocalDate() }

    /** [due] moved to [day], keeping its local time of day (null if there was no due date). */
    fun moveToDay(due: Instant?, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val old = due?.atZone(zone) ?: return null
        return ZonedDateTime.of(day, old.toLocalTime(), zone).toInstant()
    }
}
