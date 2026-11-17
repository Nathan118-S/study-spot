package education.studyspot.app.widget

import android.content.Context
import education.studyspot.app.core.AppJson
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.Connections
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.Source
import education.studyspot.app.model.User
import kotlinx.serialization.Serializable
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * What the widgets show: a small, self-contained copy of the user's data,
 * written by the app whenever assignments or classes change
 * (ios/Shared/SharedModels.swift WidgetSnapshot). Also read by the class
 * countdown alarms for the "due today" list.
 */
@Serializable
data class WidgetSnapshot(
    val signedIn: Boolean = false,
    val userName: String = "",
    /** Unfinished assignments, soonest first (undated last); at most 20. */
    val upcoming: List<WidgetAssignment> = emptyList(),
    val completedThisWeek: Int = 0,
    val bestStreak: Int = 0,
    val bestStreakClass: String? = null,
    val bestStreakColor: String? = null,
    val activeStreaks: Int = 0,
    val atRiskStreaks: Int = 0,
) {
    /** Still due later today (anything already past due counts as overdue). */
    fun dueToday(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<WidgetAssignment> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return upcoming.filter { a ->
            !a.isOverdue(nowMillis) && a.dueMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
        }
    }

    /** Everything due today, including what already passed (the class countdown's list). */
    fun allDueToday(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<WidgetAssignment> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return upcoming.filter { a -> a.dueMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true }
    }

    fun overdue(nowMillis: Long): List<WidgetAssignment> = upcoming.filter { it.isOverdue(nowMillis) }

    fun dueThisWeek(nowMillis: Long): List<WidgetAssignment> {
        val end = nowMillis + 7 * 86_400_000L
        return upcoming.filter { a -> a.dueMillis?.let { it in nowMillis until end } == true }
    }

    companion object {
        val signedOut = WidgetSnapshot()

        /** Sample data for widget previews (matches the iOS preview). */
        fun preview(nowMillis: Long = System.currentTimeMillis()): WidgetSnapshot {
            fun at(h: Double) = nowMillis + (h * 3_600_000).toLong()
            return WidgetSnapshot(
                signedIn = true,
                userName = "Alex",
                upcoming = listOf(
                    WidgetAssignment("p1", "Problem Set 4", "AP Calculus", "#6366f1", at(-20.0), "high", 40.0),
                    WidgetAssignment("p2", "Essay Draft", "English Literature", "#ec4899", at(3.0), "high", 60.0),
                    WidgetAssignment("p3", "Vectors Worksheet", "Physics", "#10b981", at(6.0), "high", 0.0),
                    WidgetAssignment("p4", "Quiz 3: Derivatives", "AP Calculus", "#6366f1", at(26.0), "medium", 0.0),
                    WidgetAssignment("p5", "Lab Report: Pendulums", "Physics", "#10b981", at(50.0), "medium", 20.0),
                    WidgetAssignment("p6", "Socratic Seminar Prep", "English Literature", "#ec4899", at(52.0), "medium", 25.0),
                    WidgetAssignment("p7", "Chapter 5 Reading", "US History", "#f59e0b", at(98.0), "low", 0.0),
                ),
                completedThisWeek = 4,
                bestStreak = 14,
                bestStreakClass = "AP Calculus",
                bestStreakColor = "#6366f1",
                activeStreaks = 2,
                atRiskStreaks = 1,
            )
        }

        /** Builds the snapshot from the app's data (ios/StudySpot/Services/WidgetBridge.swift). */
        fun build(
            user: User?,
            assignments: List<Assignment>,
            classes: List<SchoolClass>,
            connections: Connections,
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): WidgetSnapshot {
            if (user == null) return signedOut
            val colors = classes.associate { it.id to it.color }
            val upcoming = assignments
                .filter { !it.completed }
                .sortedBy { it.dueDate ?: Instant.MAX }
                .take(20)
                .map { a ->
                    WidgetAssignment(
                        id = a.id,
                        title = a.displayTitle,
                        className = a.className?.takeIf { it.isNotBlank() } ?: "Uncategorized",
                        classColor = a.classIdOrNull?.let { colors[it] } ?: SchoolClass.UNCATEGORIZED_COLOR,
                        dueMillis = a.dueDate?.toEpochMilli(),
                        priority = a.priority.wire,
                        progress = a.progress,
                    )
                }
            val streaks = Streaks.build(classes, assignments, connections, now)
            val best = streaks.maxByOrNull { it.streak }
            val weekStart = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()
            val doneThisWeek = assignments.count { a -> a.completed && (a.updatedDate ?: a.createdDate ?: Instant.MIN) >= weekStart }
            val hasBest = (best?.streak ?: 0) > 0
            return WidgetSnapshot(
                signedIn = true,
                userName = user.displayName,
                upcoming = upcoming,
                completedThisWeek = doneThisWeek,
                bestStreak = best?.streak ?: 0,
                bestStreakClass = if (hasBest) best?.schoolClass?.displayName else null,
                bestStreakColor = if (hasBest) best?.schoolClass?.color else null,
                activeStreaks = streaks.count { it.status == StreakStatus.Active },
                atRiskStreaks = streaks.count { it.status == StreakStatus.AtRisk },
            )
        }
    }
}

@Serializable
data class WidgetAssignment(
    val id: String,
    val title: String,
    val className: String,
    val classColor: String,
    val dueMillis: Long? = null,
    val priority: String = "medium",
    val progress: Double = 0.0,
) {
    fun isOverdue(nowMillis: Long): Boolean = dueMillis?.let { it < nowMillis } == true
}

/** Saves / loads the snapshot as a small JSON file in the app's private storage. */
object WidgetSnapshotStore {
    private const val FILE = "widget_snapshot_v1.json"
    private val lock = Any()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    /** Saves the snapshot; returns false if it was unchanged. */
    fun save(context: Context, snapshot: WidgetSnapshot): Boolean = synchronized(lock) {
        val json = AppJson.encodeToString(WidgetSnapshot.serializer(), snapshot)
        val f = file(context)
        if (f.exists() && runCatching { f.readText() }.getOrNull() == json) return false
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(f)) {
            f.writeText(json)
            tmp.delete()
        }
        true
    }

    fun load(context: Context): WidgetSnapshot = synchronized(lock) {
        val f = file(context)
        if (!f.exists()) return WidgetSnapshot.signedOut
        runCatching { AppJson.decodeFromString(WidgetSnapshot.serializer(), f.readText()) }.getOrDefault(WidgetSnapshot.signedOut)
    }
}

enum class StreakStatus { Empty, Active, AtRisk, Broken }

data class ClassStreak(val schoolClass: SchoolClass, val verified: Boolean, val streak: Int, val status: StreakStatus)

/** Per-class streaks (ios/StudySpot/Logic/Grading.swift Streaks), for the Streak widget. */
object Streaks {
    /** The run of completed assignments at the end of the by-due-date list, broken entirely by any overdue item. */
    fun compute(assignments: List<Assignment>, now: Instant = Instant.now()): Pair<Int, StreakStatus> {
        if (assignments.isEmpty()) return 0 to StreakStatus.Empty
        val sorted = assignments.sortedBy { it.dueDate ?: Instant.MIN }
        if (sorted.any { a -> !a.completed && a.dueDate?.let { it < now } == true }) return 0 to StreakStatus.Broken
        val run = sorted.asReversed().takeWhile { it.completed }.size
        return run to if (sorted.all { it.completed }) StreakStatus.Active else StreakStatus.AtRisk
    }

    fun build(classes: List<SchoolClass>, assignments: List<Assignment>, connections: Connections, now: Instant = Instant.now()): List<ClassStreak> =
        classes.map { c ->
            val (streak, status) = compute(assignments.filter { it.classId == c.id }, now)
            val verified = (c.source == Source.GoogleClassroom && connections.classroom) || (c.source == Source.Blackboard && connections.blackboard)
            ClassStreak(c, verified, streak, status)
        }
}
