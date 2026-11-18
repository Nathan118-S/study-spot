package education.studyspot.app.liveupdates

import androidx.core.content.edit
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import education.studyspot.app.core.AppJson
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.get
import education.studyspot.app.model.ClassMeeting
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.User
import education.studyspot.app.push.NotificationIds
import education.studyspot.app.widget.WidgetSnapshotStore
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** The bits of a class the countdown alarms need, saved so they work after a reboot without the network. */
@Serializable
data class ScheduledClass(
    val id: String,
    val name: String,
    val subject: String = "",
    val room: String = "",
    val color: String = SchoolClass.DEFAULT_COLOR,
    val schedule: List<ClassMeeting> = emptyList(),
) {
    companion object {
        fun from(c: SchoolClass) = ScheduledClass(c.id, c.displayName, c.subject.orEmpty(), c.room.orEmpty(), c.color, c.schedule)
    }
}

/** Pure scheduling math (unit tested). */
object ClassSchedule {
    data class Meeting(val cls: ScheduledClass, val start: ZonedDateTime) {
        val startsAt: Long get() = start.toEpochSecond()
    }

    /** Whether the user wants class countdowns (iOS ClassActivityManager.isEnabled). */
    fun isEnabled(user: User?): Boolean =
        user != null && !user.isDemo && user.data["class_live_activities"].boolValue != false

    /**
     * Each meeting's next start within [within] of [now], soonest first. A
     * class that starts within the lead time still counts (it gets its
     * countdown at once).
     */
    fun upcoming(classes: List<ScheduledClass>, now: ZonedDateTime, within: Duration = Duration.ofDays(7)): List<Meeting> =
        classes.flatMap { c ->
            c.schedule.mapNotNull { m ->
                val start = m.nextStart(now) ?: return@mapNotNull null
                if (Duration.between(now, start) >= within) null else Meeting(c, start)
            }
        }
            .distinctBy { it.cls.id to it.startsAt }
            .sortedBy { it.start }

    /** When the countdown for a class starting at [start] should appear. */
    fun triggerAt(start: ZonedDateTime): ZonedDateTime = start.minusMinutes(ClassCountdown.LEAD_MINUTES.toLong())
}

/**
 * Posts the class countdown 10 minutes before each class without push (the
 * iOS app's fallback reminders). Alarms are set for the next start of every
 * weekly meeting and re-set after each one fires, on app start, when classes
 * change, after a reboot and by a twice-daily worker.
 *
 * Uses exact alarms when the user allowed them (SCHEDULE_EXACT_ALARM, Alarms &
 * reminders), otherwise inexact while-idle alarms, which may arrive a few
 * minutes late. A push for the same meeting uses the same notification id,
 * so the two never double up.
 */
object ClassAlarmScheduler {
    private const val PREFS = "class_alarms"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CLASSES = "classes"
    private const val KEY_CODES = "codes"
    private const val WORK = "class-countdown-refresh"
    private const val MAX_ALARMS = 64

    const val ACTION_FIRE = "education.studyspot.app.CLASS_COUNTDOWN"
    const val EXTRA_CLASS_ID = "classId"
    const val EXTRA_STARTS_AT = "startsAt"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    private fun loadClasses(context: Context): List<ScheduledClass> {
        val raw = prefs(context).getString(KEY_CLASSES, null) ?: return emptyList()
        return runCatching { AppJson.decodeFromString<List<ScheduledClass>>(raw) }.getOrDefault(emptyList())
    }

    /** Call when the user, their settings or their classes change. */
    fun update(context: Context, user: User?, classes: List<SchoolClass>) {
        val enabled = ClassSchedule.isEnabled(user)
        val scheduled = classes.filter { it.schedule.isNotEmpty() }.map(ScheduledClass::from)
        prefs(context).edit {
            putBoolean(KEY_ENABLED, enabled)
            putString(KEY_CLASSES, AppJson.encodeToString(scheduled))
        }
        if (enabled) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ClassCountdownWorker>(12, TimeUnit.HOURS).build(),
            )
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
        reschedule(context)
    }

    /** Signing out / the setting turned off: no more alarms or countdowns. */
    fun disable(context: Context) {
        prefs(context).edit { putBoolean(KEY_ENABLED, false).remove(KEY_CLASSES) }
        cancelAlarms(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        LiveUpdates.endClassCountdowns(context)
    }

    /** Re-sets every alarm from the saved classes. */
    fun reschedule(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        cancelAlarms(context)
        if (!isEnabled(context)) {
            LiveUpdates.endClassCountdowns(context)
            return
        }
        val meetings = ClassSchedule.upcoming(loadClasses(context), now).take(MAX_ALARMS)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val codes = mutableSetOf<String>()
        for (m in meetings) {
            val trigger = ClassSchedule.triggerAt(m.start)
            if (!trigger.isAfter(now)) {
                // Starts within the lead time: show it now.
                LiveUpdates.showClassCountdown(context, countdown(context, m.cls, m.startsAt), now.toInstant().toEpochMilli())
                continue
            }
            // Request code = the meeting's notification id.
            val code = NotificationIds.classMeeting(m.cls.id, m.startsAt)
            val pi = alarmIntent(context, m.cls.id, m.startsAt, code, PendingIntent.FLAG_UPDATE_CURRENT) ?: continue
            setAlarm(am, trigger.toInstant().toEpochMilli(), pi)
            codes += code.toString()
        }
        prefs(context).edit { putStringSet(KEY_CODES, codes) }
    }

    /** Whether alarms will be on time (Alarms & reminders allowed). */
    fun canUseExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun setAlarm(am: AlarmManager, atMillis: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun alarmIntent(context: Context, classId: String, startsAt: Long, code: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, ClassAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_CLASS_ID, classId)
            .putExtra(EXTRA_STARTS_AT, startsAt)
        return PendingIntent.getBroadcast(context, code, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancelAlarms(context: Context) {
        val p = prefs(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        p.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.forEach { code ->
            val intent = Intent(context, ClassAlarmReceiver::class.java).setAction(ACTION_FIRE)
            PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                am.cancel(it)
                it.cancel()
            }
        }
        p.edit { remove(KEY_CODES) }
    }

    /** Builds the countdown for a saved class, with what's due today from the widget snapshot. */
    fun countdown(context: Context, cls: ScheduledClass, startsAt: Long, nowMillis: Long = System.currentTimeMillis()): ClassCountdown {
        val due = WidgetSnapshotStore.load(context).allDueToday(nowMillis, ZoneId.systemDefault())
        return ClassCountdown(
            classId = cls.id,
            className = cls.name,
            subject = cls.subject,
            room = cls.room,
            colorHex = cls.color,
            startsAt = startsAt,
            dueCount = due.size,
            dueToday = due.take(3).map { DueItem(it.title, it.className) },
        )
    }

    /** An alarm went off: post its countdown and set the next ones. */
    internal fun fire(context: Context, classId: String, startsAt: Long) {
        if (isEnabled(context)) {
            val cls = loadClasses(context).firstOrNull { it.id == classId }
            val now = System.currentTimeMillis()
            // Skip alarms delivered long after the class started (device was off).
            if (cls != null && now < startsAt * 1000 + ClassCountdown.DEFAULT_END_AFTER_MINUTES * 60_000L) {
                LiveUpdates.showClassCountdown(context, countdown(context, cls, startsAt, now), now)
            }
        }
        reschedule(context, Instant.now().plusSeconds(1).atZone(ZoneId.systemDefault()))
    }
}

class ClassAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ClassAlarmScheduler.ACTION_FIRE) return
        val classId = intent.getStringExtra(ClassAlarmScheduler.EXTRA_CLASS_ID) ?: return
        val startsAt = intent.getLongExtra(ClassAlarmScheduler.EXTRA_STARTS_AT, 0L)
        if (startsAt <= 0) return
        ClassAlarmScheduler.fire(context, classId, startsAt)
    }
}

/** Twice a day, re-sets the alarms (covers days the app isn't opened). */
class ClassCountdownWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ClassAlarmScheduler.reschedule(applicationContext)
        return Result.success()
    }
}
