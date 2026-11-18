package education.studyspot.app.model

import education.studyspot.app.core.AppJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Decodes JSON shaped exactly like the server's responses (server/src/routes, schema.sql). */
class ModelDecodingTest {

    @Test
    fun decodesAuthMe() {
        // GET /api/auth/me: toPublicUser(row) + mfa_pending
        val json = """
            {
              "id": "6f1c2a51-9a7e-4c43-9b8e-0a1d2c3b4e5f",
              "username": "alex",
              "name": null,
              "full_name": "Alex Doe",
              "role": "admin",
              "disabled_reason": null,
              "is_verified": true,
              "twofa_enabled": true,
              "twofa_method": "totp",
              "twofa_methods": ["totp", "passkey"],
              "data": {
                "onboarding_completed": true,
                "leaderboard_enabled": "true",
                "timezone": "Europe/Amsterdam",
                "grading_scale": [{"letter": "A", "min": 93}, {"letter": "B", "min": "85"}],
                "last_sync": "2026-11-01T08:30:00.000Z"
              },
              "created_at": "2025-01-02T10:00:00.000Z",
              "updated_at": "2026-11-01T08:30:00.000Z",
              "has_passkey": true,
              "created_date": "2025-01-02T10:00:00.000Z",
              "updated_date": "2026-11-01T08:30:00.000Z",
              "mfa_pending": true
            }
        """.trimIndent()
        val u = AppJson.decodeFromString(User.serializer(), json)
        assertEquals("alex", u.username)
        assertEquals("Alex Doe", u.displayName)
        assertTrue(u.isAdmin)
        assertTrue(u.mfaPending)
        assertTrue(u.hasPasskey)
        assertTrue(u.onboardingCompleted)
        assertTrue(u.leaderboardEnabled)
        assertEquals(listOf(TwoFactorMethod.Totp, TwoFactorMethod.Passkey), u.enabledTwoFactorMethods)
        assertEquals(listOf(GradeEntry("A", 93.0), GradeEntry("B", 85.0)), u.gradingScale)
        assertEquals(Instant.parse("2026-11-01T08:30:00Z"), u.lastSync)
        assertEquals("Europe/Amsterdam", u.timezone)
    }

    @Test
    fun legacyTwoFactorFieldsAndDefaults() {
        val u = AppJson.decodeFromString(
            User.serializer(),
            """{"id":"1","username":"sam","role":"user","twofa_enabled":true,"twofa_methods":[],"data":{}}""",
        )
        assertEquals(listOf(TwoFactorMethod.Totp), u.enabledTwoFactorMethods)
        assertFalse(u.onboardingCompleted)
        assertEquals("sam", u.displayName)
        assertEquals(Grading.defaultScale, u.gradingScale)
    }

    @Test
    fun decodesAssignmentsWithNumericStringsAndNulls() {
        // NUMERIC columns arrive as strings; nullable text columns as null.
        val json = """
            [{
              "id": "a1", "created_by_id": "u1", "title": "Essay", "class_id": "c1", "class_name": "English",
              "due_date": "2026-11-20T23:59:00.000Z", "priority": "high", "type": "project",
              "notes": null, "description": null, "source": "google_classroom", "external_id": "123",
              "completed": false, "progress": "40",
              "subtasks": [{"text": "Outline", "done": true}, {"text": "Draft", "done": false}],
              "points": "100", "score": "88.5", "reminder_sent": false, "reminder_sent_hours": [],
              "created_at": "2026-11-01T00:00:00.000Z", "updated_at": "2026-11-02T00:00:00.000Z",
              "created_date": "2026-11-01T00:00:00.000Z", "updated_date": "2026-11-02T00:00:00.000Z"
            },
            {
              "id": "a2", "title": "Reading", "class_id": "", "due_date": null, "priority": "urgent", "type": "weird",
              "source": "manual", "completed": true, "progress": 0, "subtasks": null, "points": 0, "score": null
            }]
        """.trimIndent()
        val list = AppJson.decodeFromString(ListSerializer(Assignment.serializer()), json)
        val a = list[0]
        assertEquals(Priority.High, a.priority)
        assertEquals(AssignmentType.Project, a.type)
        assertEquals(Source.GoogleClassroom, a.source)
        assertEquals(40.0, a.progress, 0.0)
        assertEquals(100.0, a.points, 0.0)
        assertEquals(88.5, a.score!!, 0.0)
        assertEquals(2, a.subtasks.size)
        assertTrue(a.subtasks[0].done)
        assertEquals("", a.notes)
        assertTrue(a.isGraded)
        assertEquals(Instant.parse("2026-11-20T23:59:00Z"), a.dueDate)
        assertEquals("B", Grading.letter(a.score, a.points, Grading.defaultScale))
        assertEquals(89, Grading.percent(a))

        val b = list[1]
        assertNull(b.classIdOrNull)
        assertNull(b.dueDate)
        assertEquals(Priority.Medium, b.priority) // unknown -> default
        assertEquals(AssignmentType.Homework, b.type)
        assertTrue(b.subtasks.isEmpty())
        assertNull(b.score)
        assertFalse(b.isGraded)
        assertFalse(b.isOverdue)
    }

    @Test
    fun decodesLmsSources() {
        // Assignments and classes imported from Canvas, Schoology and Brightspace calendar links.
        val json = """
            [{"id":"a1","title":"Lab 3","source":"canvas"},
             {"id":"a2","title":"Essay","source":"schoology"},
             {"id":"a3","title":"Quiz","source":"brightspace"},
             {"id":"a4","title":"Mystery","source":"moodle"}]
        """.trimIndent()
        val list = AppJson.decodeFromString(ListSerializer(Assignment.serializer()), json)
        assertEquals(listOf(Source.Canvas, Source.Schoology, Source.Brightspace, Source.Manual), list.map { it.source })
        assertEquals(listOf("Canvas", "Schoology", "Brightspace"), list.take(3).map { it.source.label })
        assertEquals(listOf("canvas", "schoology", "brightspace"), list.take(3).map { it.source.wire })
        val c = AppJson.decodeFromString(SchoolClass.serializer(), """{"id":"c1","name":"Chemistry","source":"brightspace"}""")
        assertEquals(Source.Brightspace, c.source)
        assertFalse(Source.Canvas.isGoogle)
        assertTrue(Source.GoogleClassroom.isGoogle)
    }

    @Test
    fun decodesClassWithSchedule() {
        val json = """
            {"id":"c1","name":"Biology","color":"#22c55e","teacher_name":"Ms. Lee","subject":"Science","room":"B12",
             "schedule":[{"day":1,"start":"09:00","end":"09:50"},{"day":"3","start":"13:15","end":"14:05"}],
             "source":"manual","external_id":null,"best_streak":"7"}
        """.trimIndent()
        val c = AppJson.decodeFromString(SchoolClass.serializer(), json)
        assertEquals("Biology", c.displayName)
        assertEquals(7, c.bestStreak)
        assertEquals(2, c.schedule.size)
        assertEquals(3, c.schedule[1].day)
        assertEquals(LocalTime.of(13, 15), c.schedule[1].startTime)
    }

    @Test
    fun decodesNotificationsConnectionsAndLeaderboard() {
        val n = AppJson.decodeFromString(
            AppNotification.serializer(),
            """{"id":"n1","user_id":"u1","title":"Streak!","content":null,"type":"streak","read":false,"action_label":"View","action_url":"/analytics","created_date":"2026-11-10T12:00:00Z"}""",
        )
        assertEquals("/analytics", n.actionUrl)
        assertEquals("", n.content)
        assertFalse(n.read)

        val conn = AppJson.decodeFromString(Connections.serializer(), """{"calendar":true,"classroom":false,"blackboard":false}""")
        assertTrue(conn.any)

        val lb = AppJson.decodeFromString(
            Leaderboard.serializer(),
            """{"entries":[{"userId":"u1","name":"Alex","bestStreak":12}],"currentUserId":"u1"}""",
        )
        assertEquals(12, lb.entries.single().bestStreak)
    }

    @Test
    fun nextMeetingStart() {
        // Monday 09:00 meeting; from Tuesday 2026-11-17 -> Monday 2026-11-23.
        val m = ClassMeeting(day = 1, start = "09:00", end = "09:50")
        val from = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, ZoneId.of("UTC"))
        val next = m.nextStart(from)!!
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(23, next.dayOfMonth)
    }
}
