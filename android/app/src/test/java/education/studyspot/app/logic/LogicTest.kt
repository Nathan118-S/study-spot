package education.studyspot.app.logic

import education.studyspot.app.data.DemoData
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Connections
import education.studyspot.app.model.GradeEntry
import education.studyspot.app.model.Grading
import education.studyspot.app.model.Priority
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.Source
import education.studyspot.app.model.Subtask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class StreaksTest {
    private val now: Instant = Instant.parse("2026-11-17T12:00:00Z")
    private fun day(offset: Long): Instant = now.plusSeconds(offset * 86_400)
    private fun a(id: String, due: Instant?, done: Boolean, classId: String = "c1") =
        Assignment(id = id, classId = classId, dueDate = due, completed = done)

    @Test fun emptyClassHasNoStreak() {
        assertEquals(StreakResult(0, StreakStatus.Empty, 0, 0), Streaks.compute(emptyList(), now))
    }

    @Test fun allCompletedIsActiveWithFullRun() {
        val r = Streaks.compute(listOf(a("1", day(-3), true), a("2", day(-1), true), a("3", day(2), true)), now)
        assertEquals(StreakResult(3, StreakStatus.Active, 3, 3), r)
    }

    @Test fun upcomingUnfinishedIsAtRiskAndEndsTheRun() {
        // Sorted by due date: done, done, (open, future). The run counts from the end, so it is 0.
        val r = Streaks.compute(listOf(a("1", day(-3), true), a("2", day(-1), true), a("3", day(2), false)), now)
        assertEquals(StreakResult(0, StreakStatus.AtRisk, 3, 2), r)
    }

    @Test fun runCountsCompletedItemsAfterTheLastOpenOne() {
        val r = Streaks.compute(listOf(a("1", day(1), false), a("2", day(2), true), a("3", day(3), true)), now)
        assertEquals(2, r.streak)
        assertEquals(StreakStatus.AtRisk, r.status)
    }

    @Test fun anyOverdueBreaksTheStreak() {
        val r = Streaks.compute(listOf(a("1", day(-2), false), a("2", day(1), true)), now)
        assertEquals(StreakResult(0, StreakStatus.Broken, 2, 1), r)
    }

    @Test fun undatedItemsSortFirstAndAreNeverOverdue() {
        val r = Streaks.compute(listOf(a("1", null, false), a("2", day(1), true)), now)
        assertEquals(StreakResult(1, StreakStatus.AtRisk, 2, 1), r)
    }

    @Test fun buildMarksSyncedClassesVerifiedOnlyWhenConnected() {
        val classes = listOf(
            SchoolClass("c1", source = Source.GoogleClassroom),
            SchoolClass("c2", source = Source.Blackboard),
            SchoolClass("c3", source = Source.Manual),
        )
        val list = listOf(a("1", day(-1), true, "c1"), a("2", day(-1), false, "c2"))
        val streaks = Streaks.build(classes, list, Connections(classroom = true), now)
        assertEquals(listOf(true, false, false), streaks.map { it.verified })
        assertEquals(listOf(StreakStatus.Active, StreakStatus.Broken, StreakStatus.Empty), streaks.map { it.status })
        assertEquals(StreakSummary(active = 1, atRisk = 0, broken = 1, best = 1, total = 3), Streaks.summary(streaks))
    }

    @Test fun demoStreaksMatchTheirSummary() {
        val s = Streaks.summary(DemoData.streaks)
        assertEquals(DemoData.streakSummary, s)
    }
}

class GradingTest {
    @Test fun defaultScaleLetters() {
        val scale = Grading.defaultScale
        assertEquals("A", Grading.letter(95.0, 100.0, scale))
        assertEquals("A", Grading.letter(90.0, 100.0, scale))
        assertEquals("B", Grading.letter(89.9, 100.0, scale))
        assertEquals("F", Grading.letter(10.0, 100.0, scale))
        assertNull(Grading.letter(null, 100.0, scale))
        assertNull(Grading.letter(10.0, 0.0, scale))
    }

    @Test fun customScaleIsSortedAndFallsBackToLowest() {
        val scale = listOf(GradeEntry("Pass", 50.0), GradeEntry("Distinction", 85.0), GradeEntry("Fail", 20.0))
        assertEquals("Distinction", Grading.letter(43.0, 50.0, scale))
        assertEquals("Pass", Grading.letter(30.0, 50.0, scale))
        assertEquals("Fail", Grading.letter(1.0, 50.0, scale)) // below every minimum
    }

    @Test fun emptyScaleUsesDefault() {
        assertEquals("C", Grading.letter(7.0, 10.0, emptyList()))
    }

    @Test fun percentRounds() {
        assertEquals(93, Grading.percent(Assignment("x", points = 30.0, score = 28.0)))
        assertNull(Grading.percent(Assignment("x", points = 30.0)))
    }
}

class CalendarMathTest {
    @Test fun gridCoversWholeSundayFirstWeeks() {
        val grid = CalendarMath.monthGrid(YearMonth.of(2026, 11)) // Nov 1, 2026 is a Sunday
        assertEquals(LocalDate.of(2026, 11, 1), grid.first())
        assertEquals(LocalDate.of(2026, 12, 5), grid.last())
        assertEquals(35, grid.size)
        assertTrue(grid.size % 7 == 0)
        assertEquals(DayOfWeek.SUNDAY, grid.first().dayOfWeek)
    }

    @Test fun gridStartsInPreviousMonth() {
        val grid = CalendarMath.monthGrid(YearMonth.of(2026, 12)) // Dec 1, 2026 is a Tuesday
        assertEquals(LocalDate.of(2026, 11, 29), grid.first())
        assertEquals(LocalDate.of(2027, 1, 2), grid.last())
    }

    @Test fun moveKeepsLocalTimeOfDay() {
        val zone = ZoneId.of("America/New_York")
        val due = LocalDateTime.of(2026, 11, 17, 15, 30).atZone(zone).toInstant()
        val moved = CalendarMath.moveToDay(due, LocalDate.of(2026, 11, 20), zone)!!
        assertEquals(LocalDateTime.of(2026, 11, 20, 15, 30), moved.atZone(zone).toLocalDateTime())
        assertNull(CalendarMath.moveToDay(null, LocalDate.of(2026, 11, 20), zone))
    }

    @Test fun byDayGroupsInLocalZoneAndSortsByTime() {
        val zone = ZoneId.of("America/Los_Angeles")
        val late = Assignment("late", dueDate = Instant.parse("2026-11-18T06:00:00Z")) // 10 PM Nov 17 local
        val early = Assignment("early", dueDate = Instant.parse("2026-11-17T17:00:00Z")) // 9 AM Nov 17 local
        val none = Assignment("none")
        val map = CalendarMath.byDay(listOf(late, none, early), zone)
        assertEquals(listOf("early", "late"), map[LocalDate.of(2026, 11, 17)]?.map { it.id })
        assertEquals(1, map.size)
    }
}

class InsightsTest {
    private val zone = ZoneOffset.UTC

    @Test fun eightMondayWeeksWithCumulativeTotals() {
        val now = Instant.parse("2026-11-18T10:00:00Z") // Wednesday
        val list = listOf(
            Assignment("1", completed = true, updatedDate = Instant.parse("2026-11-16T09:00:00Z")), // this week
            Assignment("2", completed = true, updatedDate = Instant.parse("2026-11-15T09:00:00Z")), // last week (Sunday)
            Assignment("3", completed = false, progress = 50.0, updatedDate = Instant.parse("2026-11-17T09:00:00Z")),
            Assignment("4", completed = true, updatedDate = Instant.parse("2025-01-01T00:00:00Z")), // too old
        )
        val weeks = Insights.weekly(list, now, zone, Locale.US)
        assertEquals(8, weeks.size)
        assertEquals(LocalDate.of(2026, 11, 16), weeks.last().start)
        assertEquals("Nov 16", weeks.last().label)
        assertEquals(1, weeks.last().completed)
        assertEquals(50.0, weeks.last().progressPoints, 0.0)
        assertEquals(1, weeks[6].completed)
        assertEquals(2, weeks.last().cumulative)
        val stats = Insights.stats(weeks)
        assertEquals(InsightStats(2, 1, 0.25, 1), stats)
    }
}

class AssignmentQueryTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-18T12:00:00Z") // Wednesday

    private val items = listOf(
        Assignment("over", title = "b", classId = "c1", className = "Math", dueDate = Instant.parse("2026-11-17T09:00:00Z"),
            priority = Priority.Low, createdDate = Instant.parse("2026-11-01T00:00:00Z")),
        Assignment("today", title = "a", classId = "c2", className = "art", dueDate = Instant.parse("2026-11-18T20:00:00Z"),
            priority = Priority.High, type = AssignmentType.Quiz, createdDate = Instant.parse("2026-11-10T00:00:00Z")),
        Assignment("next", title = "c", classId = "c1", className = "Math", dueDate = Instant.parse("2026-11-24T09:00:00Z"),
            priority = Priority.High, createdDate = Instant.parse("2026-11-05T00:00:00Z")),
        Assignment("none", title = "d", createdDate = Instant.parse("2026-11-12T00:00:00Z")),
        Assignment("done", completed = true, dueDate = Instant.parse("2026-11-16T09:00:00Z")),
        Assignment("graded", completed = true, points = 10.0, score = 9.0),
    )

    private fun ids(q: AssignmentQuery, tab: DashboardTab = DashboardTab.Unfinished) = q.apply(items, tab, now, zone).map { it.id }

    @Test fun tabsSplitByCompletionAndGrade() {
        assertEquals(listOf("over", "today", "next", "none"), ids(AssignmentQuery()))
        assertEquals(listOf("done"), ids(AssignmentQuery(), DashboardTab.Completed))
        assertEquals(listOf("graded"), ids(AssignmentQuery(), DashboardTab.Graded))
    }

    @Test fun dueFilters() {
        assertEquals(listOf("over"), ids(AssignmentQuery(due = DueFilter.Overdue)))
        assertEquals(listOf("today"), ids(AssignmentQuery(due = DueFilter.Today)))
        assertEquals(listOf("over", "today"), ids(AssignmentQuery(due = DueFilter.Week))) // Mon 16 – Sun 22
    }

    @Test fun classPriorityAndTypeFilters() {
        assertEquals(listOf("over", "next"), ids(AssignmentQuery(classId = "c1")))
        assertEquals(listOf("today", "next"), ids(AssignmentQuery(priority = Priority.High)))
        assertEquals(listOf("today"), ids(AssignmentQuery(type = AssignmentType.Quiz)))
        val q = AssignmentQuery(classId = "c1", priority = Priority.High)
        assertTrue(q.filtersActive)
        assertEquals(2, q.activeFilterCount)
        assertFalse(q.clearedFilters().filtersActive)
    }

    @Test fun sortOrders() {
        assertEquals(listOf("none", "today", "next", "over"), ids(AssignmentQuery(sort = SortOrder.Newest)))
        assertEquals(listOf("today", "next", "none", "over"), ids(AssignmentQuery(sort = SortOrder.Priority))) // "none" is Medium
        assertEquals(listOf("none", "today", "over", "next"), ids(AssignmentQuery(sort = SortOrder.ClassName)))
    }
}

class AssignmentEditsTest {
    @Test fun progressFromSubtasks() {
        assertEquals(0.0, AssignmentEdits.progress(emptyList()), 0.0)
        assertEquals(33.0, AssignmentEdits.progress(listOf(Subtask("a", true), Subtask("b"), Subtask("c"))), 0.0)
        assertEquals(67.0, AssignmentEdits.progress(listOf(Subtask("a", true), Subtask("b", true), Subtask("c"))), 0.0)
    }

    @Test fun completingChecksAllSubtasks() {
        val a = Assignment("x", subtasks = listOf(Subtask("a", true), Subtask("b")), progress = 50.0)
        val done = AssignmentEdits.toggledCompletion(a)
        assertTrue(done.completed)
        assertTrue(done.subtasks.all { it.done })
        assertEquals(100.0, done.progress, 0.0)
        val undone = AssignmentEdits.toggledCompletion(done)
        assertTrue(undone.subtasks.none { it.done })
        assertEquals(0.0, undone.progress, 0.0)
    }

    @Test fun completingWithoutSubtasksSetsProgress() {
        val a = Assignment("x", progress = 40.0)
        assertEquals(100.0, AssignmentEdits.toggledCompletion(a).progress, 0.0)
        assertEquals(0.0, AssignmentEdits.toggledCompletion(a.copy(completed = true, progress = 100.0)).progress, 0.0)
        assertEquals(40.0, AssignmentEdits.toggledCompletion(a.copy(completed = true)).progress, 0.0)
    }

    @Test fun scoreParsing() {
        assertEquals(AssignmentEdits.ScoreInput.Valid(88.5), AssignmentEdits.parseScore(" 88,5 "))
        assertEquals(AssignmentEdits.ScoreInput.Valid(null), AssignmentEdits.parseScore(""))
        assertEquals(AssignmentEdits.ScoreInput.Invalid, AssignmentEdits.parseScore("abc"))
        assertEquals(AssignmentEdits.ScoreInput.Invalid, AssignmentEdits.parseScore("-1"))
    }
}
