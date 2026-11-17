package education.studyspot.app.logic

import education.studyspot.app.model.Assignment
import education.studyspot.app.model.Connections
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.Source
import java.time.Instant

// Per-class study streaks (mirrors ios/StudySpot/Logic/Grading.swift: Streaks).

enum class StreakStatus(val label: String) {
    Active("Active"), AtRisk("At risk"), Broken("Broken"), Empty("No assignments"),
}

data class ClassStreak(
    val schoolClass: SchoolClass,
    /** True for classes synced from a connected Google Classroom / Blackboard. */
    val verified: Boolean,
    val streak: Int,
    val status: StreakStatus,
    val total: Int,
    val completed: Int,
) {
    val id: String get() = schoolClass.id
}

data class StreakSummary(
    val active: Int = 0,
    val atRisk: Int = 0,
    val broken: Int = 0,
    val best: Int = 0,
    val total: Int = 0,
)

data class StreakResult(val streak: Int, val status: StreakStatus, val total: Int, val completed: Int)

object Streaks {
    /**
     * The run of completed assignments at the end of the by-due-date list,
     * broken entirely by any overdue item. Assignments without a due date sort first.
     */
    fun compute(assignments: List<Assignment>, now: Instant = Instant.now()): StreakResult {
        val sorted = assignments.sortedBy { it.dueDate ?: Instant.MIN }
        val total = sorted.size
        val completed = sorted.count { it.completed }
        if (total == 0) return StreakResult(0, StreakStatus.Empty, 0, 0)
        val hasOverdue = sorted.any { a -> val d = a.dueDate; d != null && !a.completed && d < now }
        if (hasOverdue) return StreakResult(0, StreakStatus.Broken, total, completed)
        val run = sorted.asReversed().takeWhile { it.completed }.size
        return StreakResult(run, if (completed == total) StreakStatus.Active else StreakStatus.AtRisk, total, completed)
    }

    fun build(
        classes: List<SchoolClass>,
        assignments: List<Assignment>,
        connections: Connections,
        now: Instant = Instant.now(),
    ): List<ClassStreak> {
        val byClass = assignments.groupBy { it.classIdOrNull }
        return classes.map { c ->
            val verified = (c.source == Source.GoogleClassroom && connections.classroom) ||
                (c.source == Source.Blackboard && connections.blackboard)
            val r = compute(byClass[c.id].orEmpty(), now)
            ClassStreak(c, verified, r.streak, r.status, r.total, r.completed)
        }
    }

    fun summary(streaks: List<ClassStreak>) = StreakSummary(
        active = streaks.count { it.status == StreakStatus.Active },
        atRisk = streaks.count { it.status == StreakStatus.AtRisk },
        broken = streaks.count { it.status == StreakStatus.Broken },
        best = streaks.maxOfOrNull { it.streak } ?: 0,
        total = streaks.size,
    )
}
