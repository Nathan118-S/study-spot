package education.studyspot.app.logic

import education.studyspot.app.model.Assignment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** One Monday-to-Monday week of completion stats. */
data class WeekBucket(
    val start: LocalDate,
    val label: String,
    val completed: Int,
    val progressPoints: Double,
    val cumulative: Int,
)

data class InsightStats(val totalCompleted: Int, val thisWeek: Int, val averagePerWeek: Double, val bestWeek: Int)

/** Weekly completion trend (mirrors ios/StudySpot/Logic/Grading.swift: Insights). */
object Insights {
    const val WEEKS = 8

    /** Monday of the week containing [day]. */
    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * The last [WEEKS] weeks (oldest first). Each assignment counts in the week of
     * its last update (falling back to created, then due date), like iOS.
     */
    fun weekly(
        assignments: List<Assignment>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<WeekBucket> {
        val fmt = DateTimeFormatter.ofPattern("MMM d", locale)
        val thisWeek = weekStart(now.atZone(zone).toLocalDate())
        val starts = (WEEKS - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
        val completed = IntArray(WEEKS)
        val progress = DoubleArray(WEEKS)
        for (a in assignments) {
            val ref = a.updatedDate ?: a.createdDate ?: a.dueDate ?: continue
            val idx = starts.indexOf(weekStart(ref.atZone(zone).toLocalDate()))
            if (idx < 0) continue
            if (a.completed) completed[idx]++
            if (a.progress > 0) progress[idx] += a.progress
        }
        var running = 0
        return starts.mapIndexed { i, s ->
            running += completed[i]
            WeekBucket(s, s.format(fmt), completed[i], progress[i], running)
        }
    }

    fun stats(weekly: List<WeekBucket>): InsightStats = InsightStats(
        totalCompleted = weekly.lastOrNull()?.cumulative ?: 0,
        thisWeek = weekly.lastOrNull()?.completed ?: 0,
        averagePerWeek = if (weekly.isEmpty()) 0.0 else weekly.sumOf { it.completed }.toDouble() / weekly.size,
        bestWeek = weekly.maxOfOrNull { it.completed } ?: 0,
    )
}
