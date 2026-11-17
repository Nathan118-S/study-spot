package education.studyspot.app.screenshots

import education.studyspot.app.AppContainer
import education.studyspot.app.data.DemoData
import education.studyspot.app.core.AppJson
import education.studyspot.app.model.AppNotification
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.SchoolClass
import kotlinx.serialization.builtins.ListSerializer
import education.studyspot.app.model.AssignmentTemplate
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Priority
import education.studyspot.app.model.Subtask
import education.studyspot.app.model.User
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit

/** A signed-in student with the demo classes and assignments, plus notifications. */
object ScreenshotFixtures {
    fun user(admin: Boolean = true) = User(
        id = "u1",
        username = "alex.rivera",
        fullName = "Alex Rivera",
        role = if (admin) "admin" else "user",
        isVerified = true,
        twofaEnabled = true,
        twofaMethods = listOf("totp", "passkey"),
        hasPasskey = true,
        data = buildJsonObject {
            put("onboarding_completed", true)
            put("welcome_style", "off")
            put("reminders_enabled", true)
            put("reminder_lead_hours", JsonArray(listOf(JsonPrimitive(2), JsonPrimitive(24))))
            put("dnd_enabled", true)
            put("dnd_start", "22:00")
            put("dnd_end", "07:00")
            put("leaderboard_enabled", true)
            put("last_sync", Instant.now().minus(2, ChronoUnit.HOURS).toString())
        },
        createdDate = Instant.parse("2025-08-21T15:04:00Z"),
    )

    val assignments
        get() = DemoData.assignments.map { a ->
            when (a.id) {
                "demo-a2" -> a.copy(
                    notes = "Focus on the symbolism of the green light. Bring two printed copies for peer review.",
                    description = "Write a 1,200–1,500 word draft arguing how Fitzgerald uses setting to critique the American Dream.",
                    subtasks = listOf(
                        Subtask("Outline the argument", done = true),
                        Subtask("Find three quotes", done = true),
                        Subtask("Write the body paragraphs"),
                        Subtask("Proofread"),
                    ),
                )
                else -> a
            }
        }

    private fun ago(minutes: Long) = Instant.now().minus(minutes, ChronoUnit.MINUTES)

    val notifications = listOf(
        AppNotification("n1", "Essay Draft is due today", "English Literature · due at 9:00 AM. You're 60% done.", "reminder", read = false, actionLabel = "Open dashboard", actionUrl = "/dashboard", createdDate = ago(25)),
        AppNotification("n2", "Unit 2 Test next week", "AP Calculus · Unit 2 Test is in 7 days. Start reviewing derivatives.", "test", read = false, createdDate = ago(180)),
        AppNotification("n3", "14-day streak in AP Calculus!", "Keep it going — finish Quiz 3 tomorrow to extend it.", "streak", read = true, actionLabel = "View streaks", actionUrl = "/streaks", createdDate = ago(60 * 26)),
        AppNotification("n4", "Google Classroom synced", "3 new assignments imported from Google Classroom.", "info", read = true, createdDate = ago(60 * 50)),
    )

    val templates = listOf(
        AssignmentTemplate("t1", name = "Weekly reading", title = "Chapter reading", type = AssignmentType.Reading, priority = Priority.Low, points = 10.0),
        AssignmentTemplate("t2", name = "Lab report", title = "Lab Report", type = AssignmentType.Project, priority = Priority.Medium, points = 80.0),
    )

    /** Signs in [user] and fills the repository as if the first load had finished. */
    fun signIn(c: AppContainer, user: User = user()) {
        FakeBackend.user = user
        FakeBackend.entities = mapOf(
            "Assignment" to AppJson.encodeToString(ListSerializer(Assignment.serializer()), assignments),
            "Class" to AppJson.encodeToString(ListSerializer(SchoolClass.serializer()), DemoData.classes),
            "Notification" to AppJson.encodeToString(ListSerializer(AppNotification.serializer()), notifications),
            "AssignmentTemplate" to AppJson.encodeToString(ListSerializer(AssignmentTemplate.serializer()), templates),
        )
        c.auth.hasGreetedThisLaunch = true
        c.repository.setUser(user)
        c.repository.seed(
            assignments = assignments,
            classes = DemoData.classes,
            connections = DemoData.connections,
            notifications = notifications,
            templates = templates,
        )
    }
}
