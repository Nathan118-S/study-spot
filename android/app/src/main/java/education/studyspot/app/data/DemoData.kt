package education.studyspot.app.data

import education.studyspot.app.logic.ClassStreak
import education.studyspot.app.logic.StreakStatus
import education.studyspot.app.logic.StreakSummary
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.ClassMeeting
import education.studyspot.app.model.Connections
import education.studyspot.app.model.Leaderboard
import education.studyspot.app.model.LeaderboardEntry
import education.studyspot.app.model.Priority
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.Source
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local sample data for "demo" accounts, which never write to the server
 * (see DataRepository.isDemo). Port of ios/StudySpot/Logic/DemoData.swift:
 * DataRepository.load uses [classes], [assignments] and [connections];
 * the Streaks screen shows [streaks], [streakSummary] and [leaderboard]
 * instead of computing them.
 */
object DemoData {
    val connections = Connections(calendar = true, classroom = true, blackboard = true)

    val classes: List<SchoolClass>
        get() = listOf(
            SchoolClass(
                id = "demo-c1", name = "AP Calculus", color = "#6366f1", teacherName = "Mrs. Chen",
                subject = "Math", room = "204", source = Source.GoogleClassroom, externalId = "d1",
                schedule = listOf(ClassMeeting(1, "09:00", "09:50"), ClassMeeting(3, "09:00", "09:50"), ClassMeeting(5, "09:00", "09:50")),
            ),
            SchoolClass(
                id = "demo-c2", name = "English Literature", color = "#ec4899", teacherName = "Mr. Patel",
                subject = "English", source = Source.GoogleClassroom, externalId = "d2",
                schedule = listOf(ClassMeeting(2, "10:00", "10:50"), ClassMeeting(4, "10:00", "10:50")),
            ),
            SchoolClass(
                id = "demo-c3", name = "Physics", color = "#10b981", teacherName = "Dr. Lee",
                subject = "Science", room = "Lab 2", source = Source.Blackboard, externalId = "d3",
            ),
            SchoolClass(id = "demo-c4", name = "US History", color = "#f59e0b", teacherName = "Ms. Garcia", source = Source.Manual),
        )

    private fun due(offsetDays: Long, hour: Int = 9): Instant {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).plusDays(offsetDays).atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant()
    }

    @Suppress("LongParameterList")
    private fun a(
        id: String, title: String, cls: String, clsName: String, off: Long, pr: Priority, type: AssignmentType,
        src: Source, done: Boolean = false, progress: Double, points: Double, score: Double? = null,
    ) = Assignment(
        id = id, title = title, classId = cls, className = clsName, dueDate = due(off), priority = pr, type = type,
        source = src, externalId = if (src == Source.Manual) null else id, completed = done, progress = progress,
        points = points, score = score, createdDate = due(off - 7), updatedDate = if (done) due(off) else due(-1),
    )

    val assignments: List<Assignment>
        get() = listOf(
            a("demo-a1", "Problem Set 4", "demo-c1", "AP Calculus", -2, Priority.High, AssignmentType.Homework, Source.GoogleClassroom, progress = 40.0, points = 100.0),
            a("demo-a2", "Essay Draft", "demo-c2", "English Literature", 0, Priority.High, AssignmentType.Project, Source.GoogleClassroom, progress = 60.0, points = 50.0),
            a("demo-a3", "Lab Report: Pendulums", "demo-c3", "Physics", 2, Priority.Medium, AssignmentType.Project, Source.Blackboard, progress = 20.0, points = 80.0),
            a("demo-a4", "Chapter 5 Reading", "demo-c4", "US History", 4, Priority.Low, AssignmentType.Reading, Source.Manual, progress = 0.0, points = 20.0),
            a("demo-a5", "Quiz 3: Derivatives", "demo-c1", "AP Calculus", 1, Priority.Medium, AssignmentType.Quiz, Source.GoogleClassroom, progress = 0.0, points = 40.0),
            a("demo-a6", "Vocab Worksheet", "demo-c2", "English Literature", -5, Priority.Low, AssignmentType.Homework, Source.GoogleClassroom, done = true, progress = 100.0, points = 30.0, score = 28.0),
            a("demo-a7", "Kinematics Problem Set", "demo-c3", "Physics", -1, Priority.High, AssignmentType.Homework, Source.Blackboard, done = true, progress = 100.0, points = 50.0, score = 47.0),
            a("demo-a8", "Unit 2 Test", "demo-c1", "AP Calculus", 7, Priority.High, AssignmentType.Test, Source.GoogleClassroom, progress = 0.0, points = 100.0),
            a("demo-a9", "Poetry Analysis", "demo-c2", "English Literature", 3, Priority.Medium, AssignmentType.Homework, Source.GoogleClassroom, progress = 15.0, points = 40.0),
            a("demo-a10", "Vectors Worksheet", "demo-c3", "Physics", 0, Priority.High, AssignmentType.Homework, Source.Blackboard, progress = 0.0, points = 30.0),
            a("demo-a11", "Cold War Timeline", "demo-c4", "US History", 5, Priority.Medium, AssignmentType.Project, Source.Manual, progress = 35.0, points = 60.0),
            a("demo-a12", "Integration Practice", "demo-c1", "AP Calculus", -1, Priority.High, AssignmentType.Homework, Source.GoogleClassroom, progress = 50.0, points = 50.0),
            a("demo-a13", "Newton’s Laws Lab", "demo-c3", "Physics", 6, Priority.Low, AssignmentType.Project, Source.Blackboard, progress = 10.0, points = 70.0),
            a("demo-a14", "Socratic Seminar Prep", "demo-c2", "English Literature", 2, Priority.Medium, AssignmentType.Reading, Source.GoogleClassroom, progress = 25.0, points = 35.0),
        )

    /** Precomputed streaks with fake (inflated) numbers. */
    val streaks: List<ClassStreak>
        get() {
            data class D(val streak: Int, val status: StreakStatus, val total: Int, val completed: Int, val verified: Boolean)
            val data = listOf(
                D(14, StreakStatus.Active, 8, 8, true),
                D(9, StreakStatus.Active, 6, 6, true),
                D(5, StreakStatus.AtRisk, 7, 6, true),
                D(0, StreakStatus.Broken, 5, 3, false),
            )
            return classes.zip(data) { c, d -> ClassStreak(c, d.verified, d.streak, d.status, d.total, d.completed) }
        }

    val streakSummary = StreakSummary(active = 2, atRisk = 1, broken = 1, best = 14, total = 4)

    val leaderboard = Leaderboard(
        entries = listOf(
            LeaderboardEntry("demo-u1", "Aisha K.", 28),
            LeaderboardEntry("demo-u2", "Marcus T.", 22),
            LeaderboardEntry("demo-self", "You", 14),
            LeaderboardEntry("demo-u3", "Sofia R.", 11),
            LeaderboardEntry("demo-u4", "Liam P.", 8),
        ),
        currentUserId = "demo-self",
    )
}
