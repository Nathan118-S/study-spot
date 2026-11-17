package education.studyspot.app.timer

import androidx.core.content.edit
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import education.studyspot.app.appContainer
import education.studyspot.app.core.AppJson
import education.studyspot.app.core.Dates
import education.studyspot.app.model.Assignment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Owns the study timer for the whole app (the iOS StudyTimerModel), so it
 * keeps going when the timer screen or the activity is gone:
 *
 * - The state is a [TimerState] persisted to SharedPreferences on every
 *   change and restored on launch (a phase that ended meanwhile is finished
 *   and logged then).
 * - While running or paused, [StudyTimerService] (a foreground service) shows
 *   the ongoing notification with a countdown and Pause/Resume/Stop.
 * - An alarm at the phase end finishes the phase even if the process was
 *   killed; finished focus phases are logged as StudySession records.
 *
 * Use from the main thread.
 */
class TimerController private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _logging = MutableStateFlow(false)
    /** True while a finished focus session is being saved. */
    val logging: StateFlow<Boolean> = _logging.asStateFlow()

    private fun now() = System.currentTimeMillis()

    // region Actions

    /** Start or resume. Call from a user action (starts a foreground service). */
    fun start() {
        val user = app.appContainer.repository.user.value
        set(_state.value.copy(demo = user?.isDemo ?: _state.value.demo).start(now()))
        StudyTimerService.start(app)
    }

    fun pause() = set(_state.value.pause(now()))

    fun toggle() = if (_state.value.running) pause() else start()

    /** Back to the full length of the current phase (stops the service). */
    fun reset() = set(_state.value.reset())

    /** Signing out / Stop: reset and clear the alert. */
    fun stop() {
        set(_state.value.reset())
        TimerNotifications.cancelAlert(app)
    }

    /** Skip to the next phase without logging this one. Keeps running if it was. */
    fun skip() {
        val wasRunning = _state.value.running
        set(_state.value.skip())
        if (wasRunning) start()
    }

    fun switchPhase(p: TimerPhase) {
        if (p == _state.value.phase && !_state.value.active) return
        set(_state.value.switchPhase(p))
    }

    fun setMinutes(p: TimerPhase, minutes: Int) = set(_state.value.withMinutes(p, minutes))

    fun selectAssignment(a: Assignment?) =
        set(_state.value.withAssignment(a?.id, a?.displayTitle, a?.classIdOrNull, a?.className))

    /** Finishes the phase if its time is up (alarm, service tick, app start). */
    fun checkDue() {
        if (_state.value.isDue(now())) completeNow()
    }

    /** App start: finish an overdue phase, re-arm the alarm and the notification. */
    fun restore() {
        checkDue()
        val s = _state.value
        if (s.running) scheduleAlarm(s.endsAtMillis!!)
        if (s.active) StudyTimerService.start(app)
    }

    // endregion

    private fun set(next: TimerState) {
        if (next == _state.value) return
        _state.value = next
        save(next)
        val end = next.endsAtMillis
        if (end != null) scheduleAlarm(end) else cancelAlarm()
        // The service follows [state]; it stops itself once the timer is idle.
    }

    private fun completeNow() {
        val (next, done) = _state.value.complete(now())
        _state.value = next
        save(next)
        cancelAlarm()
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val messages = app.appContainer.messages
        if (!foreground) TimerNotifications.showAlert(app, done, next.phase)
        if (done.phase == TimerPhase.Focus) {
            val detail = "${done.minutes} min" + (done.assignmentTitle?.let { " · $it" } ?: "")
            if (next.demo || app.appContainer.api.token == null) {
                if (foreground) messages.show("Focus session complete", detail)
            } else {
                scope.launch {
                    _logging.value = true
                    try {
                        val saved = app.appContainer.repository.createStudySession(buildJsonObject {
                            put("assignment_id", done.assignmentId)
                            put("assignment_title", done.assignmentTitle)
                            put("class_id", done.classId)
                            put("class_name", done.className)
                            put("duration_minutes", done.minutes)
                            put("session_type", "focus")
                            put("started_at", Dates.format(done.startedAt))
                            put("completed_at", Dates.format(done.completedAt))
                        })
                        if (saved != null && foreground) messages.show("Focus session logged", detail)
                    } finally {
                        _logging.value = false
                    }
                }
            }
        } else if (foreground) {
            messages.show("Break over", "Ready for the next focus session?")
        }
    }

    // region Alarm

    private fun alarmIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        app, ALARM_REQUEST,
        Intent(app, TimerActionReceiver::class.java).setAction(TimerActionReceiver.ACTION_DUE),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("ScheduleExactAlarm") // checked with canScheduleExactAlarms()
    private fun scheduleAlarm(atMillis: Long) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun cancelAlarm() {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        alarmIntent(PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    // endregion

    // region Persistence

    private fun load(): TimerState {
        val raw = prefs.getString(KEY_STATE, null) ?: return TimerState()
        return runCatching { AppJson.decodeFromString(TimerState.serializer(), raw) }.getOrDefault(TimerState())
    }

    private fun save(s: TimerState) {
        prefs.edit { putString(KEY_STATE, AppJson.encodeToString(TimerState.serializer(), s)) }
    }

    // endregion

    companion object {
        private const val TAG = "TimerController"
        private const val PREFS = "study_timer"
        private const val KEY_STATE = "state"
        private const val ALARM_REQUEST = 7301

        // Holds only the application context.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: TimerController? = null

        fun get(context: Context): TimerController =
            instance ?: synchronized(this) { instance ?: TimerController(context).also { instance = it } }
    }
}
