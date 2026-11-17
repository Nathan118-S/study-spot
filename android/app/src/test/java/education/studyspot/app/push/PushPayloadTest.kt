package education.studyspot.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FCM data messages as the server sends them (every value a string; see server/src/lib/fcm.js stringify). */
class PushPayloadTest {
    @Test
    fun notificationWithAssignment() {
        val p = PushPayload.parse(
            mapOf(
                "type" to "notification", "title" to "Essay due soon", "subtitle" to "English",
                "body" to "Due in 2 hours", "channel" to "reminders", "assignmentId" to "abc-123",
            ),
        ) as PushPayload.Notification
        assertEquals("Essay due soon", p.title)
        assertEquals("English", p.subtitle)
        assertEquals("Due in 2 hours", p.body)
        assertEquals(NotificationChannels.REMINDERS, p.channel)
        assertEquals("studyspot://assignment/abc-123", p.link)
        assertFalse(p.simulated)
    }

    @Test
    fun notificationUrlWinsAndUnknownChannelsFallBack() {
        val p = PushPayload.parse(
            mapOf(
                "type" to "notification", "title" to "Streak", "body" to "Keep it up", "channel" to "bogus",
                "url" to "studyspot://tab/streaks", "assignmentId" to "x", "simulated" to "true",
            ),
        ) as PushPayload.Notification
        assertEquals(NotificationChannels.GENERAL, p.channel)
        assertEquals("studyspot://tab/streaks", p.link)
        assertTrue(p.simulated)
        assertNull(p.subtitle)

        val web = PushPayload.parse(mapOf("type" to "notification", "title" to "t", "body" to "b", "url" to "https://evil.example"))
            as PushPayload.Notification
        assertNull(web.link) // only app links are opened
    }

    @Test
    fun classStart() {
        val p = PushPayload.parse(
            mapOf(
                "type" to "class_start", "classId" to "c1", "className" to "Biology", "subject" to "Science",
                "room" to "204", "color" to "#10b981", "startsAt" to "1795435200", "endsAfterMinutes" to "10",
                "dueCount" to "4",
                "dueToday" to """[{"title":"Lab","className":"Biology"},{"title":"Essay","className":"English"},{"title":"Quiz","className":"Math"}]""",
                "simulated" to "true",
            ),
        ) as PushPayload.ClassStart
        val cd = p.countdown
        assertEquals("c1", cd.classId)
        assertEquals("Biology", cd.className)
        assertEquals("204", cd.room)
        assertEquals("#10b981", cd.colorHex)
        assertEquals(1795435200L, cd.startsAt)
        assertEquals(10, cd.endsAfterMinutes)
        assertEquals(4, cd.dueCount)
        assertEquals(listOf("Lab", "Essay", "Quiz"), cd.dueToday.map { it.title })
        assertTrue(cd.simulated)
    }

    @Test
    fun classStartToleratesMissingOptionalFields() {
        val cd = (PushPayload.parse(mapOf("type" to "class_start", "classId" to "c1", "startsAt" to "100", "dueToday" to "not json"))
            as PushPayload.ClassStart).countdown
        assertEquals("Class", cd.className)
        assertEquals(0, cd.dueCount)
        assertTrue(cd.dueToday.isEmpty())
        assertEquals(10, cd.endsAfterMinutes)

        assertTrue(PushPayload.parse(mapOf("type" to "class_start", "classId" to "c1")) is PushPayload.Unknown)
    }

    @Test
    fun classEnd() {
        assertEquals(PushPayload.ClassEnd, PushPayload.parse(mapOf("type" to "class_end")))
    }

    @Test
    fun studyTimer() {
        val now = 1_795_435_200_000L
        val p = PushPayload.parse(
            mapOf(
                "type" to "study_timer", "phase" to "short", "phaseLabel" to "Short Break",
                "endsAt" to "1795435500", "total" to "300", "assignmentTitle" to "", "className" to "", "simulated" to "true",
            ),
            nowMillis = now,
        ) as PushPayload.StudyTimer
        val t = p.timer
        assertEquals("short", t.phase)
        assertEquals("Short Break", t.phaseLabel)
        assertEquals(1_795_435_500_000L, t.endsAtMillis)
        assertEquals(300L, t.totalSeconds)
        assertEquals(300L, t.remainingSeconds)
        assertEquals(0, t.progress(now))
        assertEquals(50, t.progress(now + 150_000))
        assertTrue(t.simulated)
    }

    @Test
    fun unknownTypes() {
        assertEquals(PushPayload.Unknown("something_new"), PushPayload.parse(mapOf("type" to "something_new")))
        assertEquals(PushPayload.Unknown(null), PushPayload.parse(emptyMap()))
        assertTrue(PushPayload.parse(mapOf("type" to "study_timer", "endsAt" to "x")) is PushPayload.Unknown)
    }
}
