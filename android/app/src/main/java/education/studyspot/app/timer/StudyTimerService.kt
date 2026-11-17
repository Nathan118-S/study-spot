package education.studyspot.app.timer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import education.studyspot.app.R
import education.studyspot.app.liveupdates.LiveUpdates
import education.studyspot.app.liveupdates.StudyTimerLive
import education.studyspot.app.push.NotificationChannels
import education.studyspot.app.push.NotificationIds
import education.studyspot.app.push.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the study timer alive in the background
 * (type specialUse) and shows its ongoing notification: a count-down
 * chronometer, a progress bar and Pause/Resume/Stop. It follows
 * [TimerController.state] and stops itself when the timer goes idle.
 */
class StudyTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { TimerController.get(this) }
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            controller.state.collectLatest { s ->
                if (!s.active) {
                    stop()
                    return@collectLatest
                }
                // Re-post now, then every 15 s so the progress bar moves; finish the phase on time.
                while (true) {
                    val now = System.currentTimeMillis()
                    if (s.isDue(now)) {
                        controller.checkDue()
                        return@collectLatest
                    }
                    show(s, now)
                    if (!s.running) return@collectLatest
                    delay(minOf(15_000L, s.remaining(now)).coerceAtLeast(250L))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground on every start (startForegroundService contract), even to stop.
        val s = controller.state.value
        show(s, System.currentTimeMillis())
        if (!s.active) stop()
        return START_NOT_STICKY
    }

    private fun show(s: TimerState, now: Long) {
        val n = TimerNotifications.ongoing(this, s, now)
        if (!inForeground) {
            try {
                ServiceCompat.startForeground(
                    this, NotificationIds.TIMER_ONGOING, n,
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
                )
                inForeground = true
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException: fall back to a plain notification.
                Log.w(TAG, "Couldn't start the timer in the foreground", e)
                Notifier.post(this, null, NotificationIds.TIMER_ONGOING, n)
            }
        } else {
            Notifier.post(this, null, NotificationIds.TIMER_ONGOING, n)
        }
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Notifier.cancel(this, null, NotificationIds.TIMER_ONGOING)
        inForeground = false
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StudyTimerService"

        /** Starts (or nudges) the service. Safe to call when it's already running. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, StudyTimerService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background (Android 12+); the alarm still ends the phase.
                Log.w(TAG, "Couldn't start the timer service", e)
                Notifier.post(context, null, NotificationIds.TIMER_ONGOING,
                    TimerNotifications.ongoing(context, TimerController.get(context).state.value, System.currentTimeMillis()))
            }
        }
    }
}

/** Notification actions and the phase-end alarm. */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = TimerController.get(context)
        when (intent.action) {
            ACTION_PAUSE -> c.pause()
            ACTION_RESUME, ACTION_START -> {
                TimerNotifications.cancelAlert(context)
                c.start()
            }
            ACTION_STOP -> c.stop()
            ACTION_SKIP -> c.skip()
            ACTION_DUE -> c.checkDue()
        }
    }

    companion object {
        const val ACTION_PAUSE = "education.studyspot.app.timer.PAUSE"
        const val ACTION_RESUME = "education.studyspot.app.timer.RESUME"
        const val ACTION_START = "education.studyspot.app.timer.START"
        const val ACTION_STOP = "education.studyspot.app.timer.STOP"
        const val ACTION_SKIP = "education.studyspot.app.timer.SKIP"
        const val ACTION_DUE = "education.studyspot.app.timer.DUE"
    }
}

/** The timer's notifications: the ongoing one (service) and the "phase finished" alert. */
object TimerNotifications {
    private fun action(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, TimerActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun live(s: TimerState, now: Long) = StudyTimerLive(
        phase = s.phase.wire,
        phaseLabel = s.phase.label,
        endsAtMillis = s.endsAtMillis,
        totalSeconds = s.totalMillis / 1000,
        remainingSeconds = s.remaining(now) / 1000,
        assignmentTitle = s.assignmentTitle.orEmpty(),
        className = s.className.orEmpty(),
    )

    fun ongoing(context: Context, s: TimerState, now: Long): Notification {
        val b = LiveUpdates.timerBuilder(context, NotificationChannels.TIMER, live(s, now), now)
            .setContentIntent(Notifier.openAppIntent(context, "studyspot://timer".toUri(), NotificationIds.TIMER_ONGOING))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.running) {
            b.addAction(R.drawable.ic_timer_pause, "Pause", action(context, TimerActionReceiver.ACTION_PAUSE, 1))
        } else {
            b.addAction(R.drawable.ic_timer_play, "Resume", action(context, TimerActionReceiver.ACTION_RESUME, 2))
        }
        b.addAction(R.drawable.ic_timer_skip, "Skip", action(context, TimerActionReceiver.ACTION_SKIP, 3))
        b.addAction(R.drawable.ic_timer_stop, "Stop", action(context, TimerActionReceiver.ACTION_STOP, 4))
        return b.build()
    }

    /** "Focus session complete" / "Break over", with a button to start the next phase. */
    fun showAlert(context: Context, done: CompletedPhase, next: TimerPhase) {
        val focus = done.phase == TimerPhase.Focus
        val n = NotificationCompat.Builder(context, NotificationChannels.TIMER)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(ContextCompat.getColor(context, R.color.ss_accent))
            .setContentTitle(if (focus) "Focus session complete" else "Break over")
            .setContentText(if (focus) "Nice work! Time for a ${next.label.lowercase()}." else "Ready for the next focus session?")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(Notifier.openAppIntent(context, "studyspot://timer".toUri(), NotificationIds.TIMER_ALERT))
            .addAction(R.drawable.ic_timer_play, "Start ${next.label.lowercase()}", action(context, TimerActionReceiver.ACTION_START, 5))
            .build()
        Notifier.post(context, null, NotificationIds.TIMER_ALERT, n)
    }

    fun cancelAlert(context: Context) = Notifier.cancel(context, null, NotificationIds.TIMER_ALERT)
}
