package education.studyspot.app.timer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pomodoro phases (ios/StudySpot/Views/Dashboard/StudyTimerView.swift TimerPhase). */
@Serializable
enum class TimerPhase(val wire: String, val label: String, val shortLabel: String) {
    @SerialName("focus") Focus("focus", "Focus", "Focus"),
    @SerialName("short") Short("short", "Short Break", "Short"),
    @SerialName("long") Long("long", "Long Break", "Long");

    val isBreak: Boolean get() = this != Focus

    companion object {
        fun from(raw: String?): TimerPhase = entries.firstOrNull { it.wire == raw } ?: Focus
    }
}

/** A finished phase, returned by [TimerState.complete] so the caller can log it. */
data class CompletedPhase(
    val phase: TimerPhase,
    val minutes: Int,
    val startedAt: Instant,
    val completedAt: Instant,
    val assignmentId: String?,
    val assignmentTitle: String?,
    val classId: String?,
    val className: String?,
)

/**
 * The whole study timer as an immutable value: every action returns a new
 * state. Driven by the wall clock ([endsAtMillis]) so it survives the app,
 * the activity or the process going away; [TimerController] persists it.
 *
 * - idle: not running and the full phase length is left
 * - running: [endsAtMillis] is set
 * - paused: not running with part of the phase used
 */
@Serializable
data class TimerState(
    val phase: TimerPhase = TimerPhase.Focus,
    val focusMinutes: Int = 25,
    val shortMinutes: Int = 5,
    val longMinutes: Int = 15,
    /** Wall-clock end of the running phase; null while paused or idle. */
    val endsAtMillis: kotlin.Long? = null,
    /** Time left while paused / idle. */
    val remainingMillis: kotlin.Long = 25 * 60_000L,
    /** Focus sessions finished on [cyclesDay]; every 4th is followed by a long break. */
    val cycles: Int = 0,
    val cyclesDay: String? = null,
    /** When the current phase was first started (for the logged session's started_at). */
    val phaseStartedAtMillis: kotlin.Long? = null,
    val assignmentId: String? = null,
    val assignmentTitle: String? = null,
    val classId: String? = null,
    val className: String? = null,
    /** Demo accounts never write to the server. */
    val demo: Boolean = false,
) {
    val running: Boolean get() = endsAtMillis != null

    fun minutesFor(p: TimerPhase): Int = when (p) {
        TimerPhase.Focus -> focusMinutes
        TimerPhase.Short -> shortMinutes
        TimerPhase.Long -> longMinutes
    }

    val totalMillis: kotlin.Long get() = minutesFor(phase) * 60_000L

    val paused: Boolean get() = !running && remainingMillis < totalMillis
    val idle: Boolean get() = !running && !paused

    /** True while the foreground service should be up (running or paused). */
    val active: Boolean get() = running || paused

    fun remaining(nowMillis: kotlin.Long): kotlin.Long =
        endsAtMillis?.let { (it - nowMillis).coerceAtLeast(0) } ?: remainingMillis

    fun progress(nowMillis: kotlin.Long): Float =
        if (totalMillis <= 0) 0f else (1f - remaining(nowMillis).toFloat() / totalMillis).coerceIn(0f, 1f)

    /** "mm:ss" (or "h:mm:ss") rounded up, like iOS. */
    fun display(nowMillis: kotlin.Long): String = formatClock(remaining(nowMillis))

    /** Focus sessions finished today. */
    fun cyclesToday(today: LocalDate): Int = if (cyclesDay == today.toString()) cycles else 0

    // region Actions

    fun start(nowMillis: kotlin.Long): TimerState {
        if (running) return this
        val left = remainingMillis.takeIf { it > 0 } ?: totalMillis
        return copy(
            endsAtMillis = nowMillis + left,
            remainingMillis = left,
            phaseStartedAtMillis = phaseStartedAtMillis ?: nowMillis,
        )
    }

    fun pause(nowMillis: kotlin.Long): TimerState {
        val end = endsAtMillis ?: return this
        return copy(endsAtMillis = null, remainingMillis = (end - nowMillis).coerceAtLeast(0))
    }

    fun toggle(nowMillis: kotlin.Long): TimerState = if (running) pause(nowMillis) else start(nowMillis)

    /** Back to the full length of the current phase. */
    fun reset(): TimerState = copy(endsAtMillis = null, remainingMillis = totalMillis, phaseStartedAtMillis = null)

    fun switchPhase(p: TimerPhase): TimerState =
        copy(phase = p, endsAtMillis = null, remainingMillis = minutesFor(p) * 60_000L, phaseStartedAtMillis = null)

    /** Ends the current phase early without logging it and moves to the next one. */
    fun skip(): TimerState = switchPhase(nextPhase(countThisFocus = false))

    /** Sets a phase length (1–180 minutes). The idle phase picks it up at once. */
    fun withMinutes(p: TimerPhase, minutes: Int): TimerState {
        val m = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val next = when (p) {
            TimerPhase.Focus -> copy(focusMinutes = m)
            TimerPhase.Short -> copy(shortMinutes = m)
            TimerPhase.Long -> copy(longMinutes = m)
        }
        return if (p == phase && idle) next.copy(remainingMillis = m * 60_000L) else next
    }

    fun withAssignment(id: String?, title: String?, classId: String?, className: String?): TimerState =
        copy(assignmentId = id, assignmentTitle = title, classId = classId, className = className)

    /** True when a running phase has reached its end. */
    fun isDue(nowMillis: kotlin.Long): Boolean = endsAtMillis?.let { nowMillis >= it } == true

    /**
     * Finishes the running phase (call once [isDue]). Returns the next state,
     * idle on the following phase, and what finished.
     */
    fun complete(nowMillis: kotlin.Long, zone: ZoneId = ZoneId.systemDefault()): Pair<TimerState, CompletedPhase> {
        val end = endsAtMillis ?: nowMillis
        val finishedAt = minOf(end, nowMillis)
        val mins = minutesFor(phase)
        val finished = CompletedPhase(
            phase = phase,
            minutes = mins,
            startedAt = Instant.ofEpochMilli(finishedAt - mins * 60_000L),
            completedAt = Instant.ofEpochMilli(finishedAt),
            assignmentId = assignmentId,
            assignmentTitle = assignmentTitle,
            classId = classId,
            className = className,
        )
        val today = Instant.ofEpochMilli(finishedAt).atZone(zone).toLocalDate()
        val base = if (phase == TimerPhase.Focus) {
            val done = cyclesToday(today)
            copy(cycles = done + 1, cyclesDay = today.toString())
        } else {
            this
        }
        val next = base.nextPhase(countThisFocus = false)
        return base.switchPhase(next) to finished
    }

    /**
     * The phase after this one: a break after focus (long after every
     * [LONG_BREAK_EVERY]th), focus after a break. With [countThisFocus] the
     * current focus phase counts towards the long break.
     */
    fun nextPhase(countThisFocus: Boolean = true): TimerPhase {
        if (phase != TimerPhase.Focus) return TimerPhase.Focus
        val done = cycles + if (countThisFocus) 1 else 0
        return if (done > 0 && done % LONG_BREAK_EVERY == 0) TimerPhase.Long else TimerPhase.Short
    }

    // endregion

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
        const val LONG_BREAK_EVERY = 4
        val focusPresets = listOf(15, 25, 45, 50)
        val shortPresets = listOf(5, 10)
        val longPresets = listOf(15, 20, 30)

        fun presets(p: TimerPhase) = when (p) {
            TimerPhase.Focus -> focusPresets
            TimerPhase.Short -> shortPresets
            TimerPhase.Long -> longPresets
        }

        fun formatClock(millis: kotlin.Long): String {
            val s = ((millis + 999) / 1000).coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
        }
    }
}
