package education.studyspot.app.timer

import education.studyspot.app.core.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TimerStateTest {
    private val min = 60_000L
    private val t0 = Instant.parse("2026-11-17T10:00:00Z").toEpochMilli()
    private val utc = ZoneOffset.UTC

    @Test
    fun startsIdleOnA25MinuteFocus() {
        val s = TimerState()
        assertEquals(TimerPhase.Focus, s.phase)
        assertTrue(s.idle)
        assertFalse(s.running)
        assertEquals("25:00", s.display(t0))
    }

    @Test
    fun startPauseResumeKeepsTheRemainingTime() {
        var s = TimerState().start(t0)
        assertTrue(s.running)
        assertEquals(t0 + 25 * min, s.endsAtMillis)
        assertEquals(t0, s.phaseStartedAtMillis)

        s = s.pause(t0 + 10 * min)
        assertTrue(s.paused)
        assertNull(s.endsAtMillis)
        assertEquals(15 * min, s.remainingMillis)
        assertEquals("15:00", s.display(t0 + 99 * min)) // paused: the clock doesn't move

        s = s.start(t0 + 20 * min)
        assertEquals(t0 + 35 * min, s.endsAtMillis)
        assertEquals(t0, s.phaseStartedAtMillis) // first start is kept
        assertFalse(s.isDue(t0 + 34 * min))
        assertTrue(s.isDue(t0 + 35 * min))
    }

    @Test
    fun displayRoundsUp() {
        val s = TimerState().start(t0)
        assertEquals("24:59", s.display(t0 + 1_001))
        assertEquals("00:01", s.display(t0 + 25 * min - 1))
        assertEquals("00:00", s.display(t0 + 26 * min))
        assertEquals("1:30:00", TimerState.formatClock(90 * min))
    }

    @Test
    fun completingFocusLogsItAndMovesToAShortBreak() {
        val s = TimerState().withAssignment("a1", "Essay", "c1", "English").start(t0)
        val (next, done) = s.complete(t0 + 25 * min, utc)
        assertEquals(TimerPhase.Focus, done.phase)
        assertEquals(25, done.minutes)
        assertEquals(Instant.ofEpochMilli(t0 + 25 * min), done.completedAt)
        assertEquals(Instant.ofEpochMilli(t0), done.startedAt)
        assertEquals("a1", done.assignmentId)
        assertEquals("English", done.className)

        assertEquals(TimerPhase.Short, next.phase)
        assertTrue(next.idle)
        assertEquals(5 * min, next.remainingMillis)
        assertEquals(1, next.cyclesToday(LocalDate.of(2026, 11, 17)))
        assertEquals("a1", next.assignmentId) // the assignment stays selected
    }

    @Test
    fun completionUsesThePhaseEndWhenFinishedLate() {
        // App reopened an hour after the phase ended: logged at the real end time.
        val (_, done) = TimerState().start(t0).complete(t0 + 85 * min, utc)
        assertEquals(Instant.ofEpochMilli(t0 + 25 * min), done.completedAt)
    }

    @Test
    fun everyFourthFocusIsFollowedByALongBreak() {
        var s = TimerState()
        val phases = mutableListOf<TimerPhase>()
        var now = t0
        repeat(8) {
            s = s.start(now)
            now = s.endsAtMillis!!
            s = s.complete(now, utc).first
            phases += s.phase
        }
        assertEquals(
            listOf(
                TimerPhase.Short, TimerPhase.Focus, TimerPhase.Short, TimerPhase.Focus,
                TimerPhase.Short, TimerPhase.Focus, TimerPhase.Long, TimerPhase.Focus,
            ),
            phases,
        )
        assertEquals(4, s.cycles)
    }

    @Test
    fun cyclesResetOnANewDay() {
        val s = TimerState(cycles = 3, cyclesDay = "2026-11-16").start(t0)
        val (next, _) = s.complete(t0 + 25 * min, utc)
        assertEquals(1, next.cycles)
        assertEquals(TimerPhase.Short, next.phase)
        assertEquals(0, next.cyclesToday(LocalDate.of(2026, 11, 18)))
    }

    @Test
    fun breaksReturnToFocusWithoutCounting() {
        val s = TimerState().switchPhase(TimerPhase.Long).start(t0)
        val (next, done) = s.complete(t0 + 15 * min, utc)
        assertEquals(TimerPhase.Long, done.phase)
        assertEquals(TimerPhase.Focus, next.phase)
        assertEquals(0, next.cycles)
    }

    @Test
    fun resetAndSkip() {
        val running = TimerState().start(t0)
        val reset = running.pause(t0 + 5 * min).reset()
        assertTrue(reset.idle)
        assertEquals(25 * min, reset.remainingMillis)
        assertNull(reset.phaseStartedAtMillis)

        val skipped = running.skip()
        assertEquals(TimerPhase.Short, skipped.phase)
        assertTrue(skipped.idle)
        assertEquals(0, skipped.cycles) // skipping isn't a finished session
        assertEquals(TimerPhase.Focus, skipped.skip().phase)
    }

    @Test
    fun customLengthsAreClampedAndApplyWhenIdle() {
        var s = TimerState().withMinutes(TimerPhase.Focus, 50)
        assertEquals(50 * min, s.remainingMillis)
        s = s.withMinutes(TimerPhase.Focus, 0)
        assertEquals(1, s.focusMinutes)
        s = s.withMinutes(TimerPhase.Focus, 999)
        assertEquals(180, s.focusMinutes)
        s = s.withMinutes(TimerPhase.Short, 7).withMinutes(TimerPhase.Long, 25)
        assertEquals(180 * min, s.remainingMillis) // other phases don't touch the current one
        assertEquals(7 * min, s.switchPhase(TimerPhase.Short).remainingMillis)
        assertEquals(25 * min, s.switchPhase(TimerPhase.Long).totalMillis)

        // While paused the remaining time is kept.
        val paused = TimerState().start(t0).pause(t0 + min)
        assertEquals(24 * min, paused.withMinutes(TimerPhase.Focus, 45).remainingMillis)
    }

    @Test
    fun progress() {
        val s = TimerState().start(t0)
        assertEquals(0f, s.progress(t0), 0.001f)
        assertEquals(0.5f, s.progress(t0 + 12 * min + 30_000), 0.001f)
        assertEquals(1f, s.progress(t0 + 30 * min), 0.001f)
    }

    @Test
    fun survivesASaveAndRestore() {
        val s = TimerState(cycles = 2, cyclesDay = "2026-11-17").withAssignment("a1", "Essay", null, null).start(t0)
        val json = AppJson.encodeToString(TimerState.serializer(), s)
        assertEquals(s, AppJson.decodeFromString(TimerState.serializer(), json))
        assertTrue(json.contains("\"phase\":\"focus\""))
    }
}
