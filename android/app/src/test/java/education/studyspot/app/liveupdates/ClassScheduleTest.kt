package education.studyspot.app.liveupdates

import education.studyspot.app.model.ClassMeeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ClassScheduleTest {
    private val zone = ZoneId.of("America/New_York")

    /** Tuesday 17 November 2026, 10:30 local. */
    private val now = ZonedDateTime.of(2026, 11, 17, 10, 30, 0, 0, zone)

    @Test
    fun nextStartLaterToday() {
        val start = ClassMeeting(day = 2, start = "13:15", end = "14:00").nextStart(now)!!
        assertEquals(ZonedDateTime.of(2026, 11, 17, 13, 15, 0, 0, zone), start)
    }

    @Test
    fun nextStartEarlierTodayIsNextWeek() {
        val start = ClassMeeting(day = 2, start = "08:00").nextStart(now)!!
        assertEquals(ZonedDateTime.of(2026, 11, 24, 8, 0, 0, 0, zone), start)
    }

    @Test
    fun nextStartAtExactlyNowIsNow() {
        val start = ClassMeeting(day = 2, start = "10:30").nextStart(now)!!
        assertEquals(now, start)
    }

    @Test
    fun sundayIsDayZero() {
        val start = ClassMeeting(day = 0, start = "09:00").nextStart(now)!!
        assertEquals(DayOfWeek.SUNDAY, start.dayOfWeek)
        assertEquals(ZonedDateTime.of(2026, 11, 22, 9, 0, 0, 0, zone), start)
        val saturday = ClassMeeting(day = 6, start = "09:00").nextStart(now)!!
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
    }

    @Test
    fun invalidTimesHaveNoStart() {
        assertNull(ClassMeeting(day = 1, start = "").nextStart(now))
        assertNull(ClassMeeting(day = 1, start = "25:00").nextStart(now))
    }

    @Test
    fun upcomingIsSortedAndWithinTheWindow() {
        val math = ScheduledClass("m", "Math", schedule = listOf(ClassMeeting(2, "13:00"), ClassMeeting(4, "09:00")))
        val art = ScheduledClass("a", "Art", schedule = listOf(ClassMeeting(2, "11:00"), ClassMeeting(3, "09:00")))
        val all = ClassSchedule.upcoming(listOf(math, art), now)
        assertEquals(listOf("a", "m", "a", "m"), all.map { it.cls.id })
        assertEquals(ZonedDateTime.of(2026, 11, 17, 11, 0, 0, 0, zone), all.first().start)

        val nextDay = ClassSchedule.upcoming(listOf(math, art), now, Duration.ofDays(1))
        assertEquals(listOf("a" to 11, "m" to 13, "a" to 9), nextDay.map { it.cls.id to it.start.hour })
    }

    @Test
    fun duplicateMeetingsCollapse() {
        val c = ScheduledClass("m", "Math", schedule = listOf(ClassMeeting(2, "13:00"), ClassMeeting(2, "13:00")))
        assertEquals(1, ClassSchedule.upcoming(listOf(c), now).size)
    }

    @Test
    fun countdownStartsTenMinutesBefore() {
        val start = ZonedDateTime.of(2026, 11, 17, 0, 2, 0, 0, zone)
        // Wraps to the previous day.
        assertEquals(ZonedDateTime.of(2026, 11, 16, 23, 52, 0, 0, zone), ClassSchedule.triggerAt(start))
    }

    @Test
    fun meetingAndPushShareTheNotificationId() {
        val meeting = ClassSchedule.upcoming(listOf(ScheduledClass("c1", "Bio", schedule = listOf(ClassMeeting(2, "13:00")))), now).single()
        val fromPush = ClassCountdown(classId = "c1", className = "Bio", startsAt = meeting.start.toEpochSecond())
        val fromAlarm = ClassCountdown(classId = "c1", className = "Biology", room = "4", startsAt = meeting.startsAt)
        assertEquals(fromPush.notificationId, fromAlarm.notificationId)
        assertTrue(fromPush.notificationId != fromPush.copy(startsAt = fromPush.startsAt + 7 * 86_400).notificationId)
    }

    @Test
    fun countdownText() {
        val startsAt = now.plusMinutes(5).toEpochSecond()
        val cd = ClassCountdown(
            classId = "c", className = "Biology", subject = "Science", room = "204", startsAt = startsAt,
            dueCount = 4, dueToday = listOf(DueItem("Lab", "Biology"), DueItem("Essay", "English"), DueItem("Quiz", "Math")),
        )
        val nowMs = now.toInstant().toEpochMilli()
        assertEquals("Biology starts in 5 min", cd.title(nowMs))
        assertEquals("Biology starts in 1 min", cd.title(nowMs + 4 * 60_000 + 30_000))
        assertEquals("Biology is starting", cd.title(nowMs + 5 * 60_000))
        assertEquals("Science · Room 204", cd.detailLine)
        assertEquals("4 due today: Lab, Essay, Quiz +1 more", cd.dueLine)
        assertEquals("", cd.copy(dueCount = 0, dueToday = emptyList()).dueLine)
        assertEquals(startsAt * 1000 + 10 * 60_000, cd.endsAtMillis)
    }
}
