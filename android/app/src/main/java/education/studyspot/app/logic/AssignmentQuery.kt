package education.studyspot.app.logic

import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Priority
import education.studyspot.app.model.Subtask
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

// Dashboard filtering and sorting (mirrors ios/StudySpot/Views/Dashboard/DashboardView.swift).

enum class DashboardTab(val label: String) { Unfinished("Unfinished"), Completed("Completed"), Graded("Graded") }

enum class DueFilter(val label: String) {
    All("All dates"), Overdue("Overdue"), Today("Due today"), Week("This week"),
}

enum class SortOrder(val label: String) {
    Soonest("Soonest due"), Newest("Newest"), Priority("Highest priority"), ClassName("Class name"),
}

/** Everything the dashboard's "Filter & sort" sheet controls. `null` filters mean "all". */
data class AssignmentQuery(
    val classId: String? = null,
    val due: DueFilter = DueFilter.All,
    val priority: Priority? = null,
    val type: AssignmentType? = null,
    val sort: SortOrder = SortOrder.Soonest,
) {
    val filtersActive: Boolean get() = classId != null || due != DueFilter.All || priority != null || type != null
    val activeFilterCount: Int get() = listOf(classId != null, due != DueFilter.All, priority != null, type != null).count { it }

    fun clearedFilters() = copy(classId = null, due = DueFilter.All, priority = null, type = null)

    /** The assignments for [tab], filtered and sorted. The Graded tab is filtered but always sorted by grade. */
    fun apply(
        all: List<Assignment>,
        tab: DashboardTab,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Assignment> {
        var list = when (tab) {
            DashboardTab.Unfinished -> all.filter { !it.completed && !it.isGraded }
            DashboardTab.Completed -> all.filter { it.completed && !it.isGraded }
            DashboardTab.Graded -> all.filter { it.isGraded }
        }
        classId?.let { id -> list = list.filter { it.classIdOrNull == id } }
        priority?.let { p -> list = list.filter { it.priority == p } }
        type?.let { t -> list = list.filter { it.type == t } }
        val today = now.atZone(zone).toLocalDate()
        list = when (due) {
            DueFilter.All -> list
            DueFilter.Overdue -> list.filter { it.isOverdue(now) }
            DueFilter.Today -> list.filter { it.dueDate?.atZone(zone)?.toLocalDate() == today }
            DueFilter.Week -> {
                val start = Insights.weekStart(today)
                val end = start.plusWeeks(1)
                list.filter { a -> a.dueDate?.atZone(zone)?.toLocalDate()?.let { it >= start && it < end } == true }
            }
        }
        if (tab == DashboardTab.Graded) return list.sortedByDescending { (it.score ?: 0.0) / it.points }
        val soonest = compareBy<Assignment> { it.dueDate ?: Instant.MAX }
        return when (sort) {
            SortOrder.Soonest -> list.sortedWith(soonest)
            SortOrder.Newest -> list.sortedByDescending { it.createdDate ?: Instant.MIN }
            SortOrder.Priority -> list.sortedWith(compareByDescending<Assignment> { it.priority.rank }.then(soonest))
            SortOrder.ClassName -> list.sortedWith(
                compareBy<Assignment, String>(String.CASE_INSENSITIVE_ORDER) { it.className.orEmpty() }.then(soonest),
            )
        }
    }
}

/** Subtask and completion edits (mirrors AssignmentDetailView.swift's persistence helpers). */
object AssignmentEdits {
    /** Percent of [subtasks] done, rounded; 0 when there are none. */
    fun progress(subtasks: List<Subtask>): Double =
        if (subtasks.isEmpty()) 0.0 else Math.round(subtasks.count { it.done } * 100.0 / subtasks.size).toDouble()

    /**
     * The assignment after toggling completion: with subtasks, all subtasks follow
     * the new state; without, progress becomes 100 (or drops from 100 to 0 when undone).
     */
    fun toggledCompletion(a: Assignment): Assignment {
        val completed = !a.completed
        return if (a.subtasks.isNotEmpty()) {
            val list = a.subtasks.map { it.copy(done = completed) }
            a.copy(completed = completed, subtasks = list, progress = progress(list))
        } else {
            a.copy(completed = completed, progress = if (completed) 100.0 else if (a.progress >= 100) 0.0 else a.progress)
        }
    }

    fun subtasksJson(list: List<Subtask>): JsonArray = buildJsonArray {
        list.forEach { st -> add(buildJsonObject { put("text", st.text); put("done", st.done) }) }
    }

    /** "88", "88.5", "88,5" -> number; blank -> null (clears the score); anything else -> invalid. */
    sealed interface ScoreInput {
        data class Valid(val value: Double?) : ScoreInput
        data object Invalid : ScoreInput
    }

    fun parseScore(text: String): ScoreInput {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return ScoreInput.Valid(null)
        val v = t.toDoubleOrNull() ?: return ScoreInput.Invalid
        return if (v < 0 || v.isNaN() || v.isInfinite()) ScoreInput.Invalid else ScoreInput.Valid(v)
    }
}
