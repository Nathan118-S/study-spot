package education.studyspot.app.liveupdates

import android.app.Notification
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import education.studyspot.app.R
import education.studyspot.app.push.NotificationChannels
import education.studyspot.app.push.NotificationIds
import education.studyspot.app.push.Notifier
import education.studyspot.app.timer.TimerController
import java.text.DateFormat
import java.util.Date

/**
 * Android's counterpart to the iOS Live Activities (study timer, "class
 * starts soon"): ongoing notifications with a countdown chronometer.
 *
 * - Android 16+ (API 36): promoted ongoing notifications ("Live Updates"):
 *   [NotificationCompat.ProgressStyle] + requestPromotedOngoing, shown as a
 *   status-bar chip and at the top of the shade / lock screen.
 * - Older versions: a regular ongoing notification with a count-down
 *   chronometer (and a progress bar for the timer).
 */
object LiveUpdates {
    /** Promoted ongoing notifications (Live Updates) exist from Android 16 (API 36). */
    val supportsPromoted: Boolean get() = Build.VERSION.SDK_INT >= 36

    /** Whether the system will actually promote our notifications (the user can turn it off per app). */
    fun canPromote(context: Context): Boolean =
        supportsPromoted && runCatching { NotificationManagerCompat.from(context).canPostPromotedNotifications() }.getOrDefault(false)

    // region Class countdown

    /** Posts (or replaces) the countdown for one class meeting. No-op once it should have ended. */
    fun showClassCountdown(context: Context, cd: ClassCountdown, nowMillis: Long = System.currentTimeMillis()) {
        if (nowMillis >= cd.endsAtMillis) return
        Notifier.post(context, NotificationIds.TAG_CLASS, cd.notificationId, classNotification(context, cd, nowMillis))
    }

    fun classNotification(context: Context, cd: ClassCountdown, nowMillis: Long): Notification {
        val color = parseColor(cd.colorHex, ContextCompat.getColor(context, R.color.ss_accent))
        val detail = cd.detailLine.ifEmpty { "Get ready for class" }
        val text = listOf(detail, cd.dueLine).filter { it.isNotEmpty() }.joinToString(" · ")
        val b = NotificationCompat.Builder(context, NotificationChannels.LIVE)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(cd.title(nowMillis))
            .setContentText(text)
            .setColor(color)
            .setWhen(cd.startsAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(Notifier.openAppIntent(context, "studyspot://tab/dashboard".toUri(), cd.notificationId))
            .setTimeoutAfter((cd.endsAtMillis - nowMillis).coerceAtLeast(1_000))
            .setRequestPromotedOngoing(true)
        if (supportsPromoted) {
            // The bar covers the 10 minutes before class, like the iOS countdown ring.
            val lead = ClassCountdown.LEAD_MINUTES * 60
            val elapsed = (lead - (cd.startsAtMillis - nowMillis) / 1000).toInt().coerceIn(0, lead)
            b.setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(lead).setColor(color)))
                    .setProgress(elapsed)
                    .setStyledByProgress(true),
            )
            b.setShortCriticalText(cd.className.take(12))
        } else {
            val lines = buildList {
                add(detail)
                if (cd.dueCount > 0) {
                    add("${cd.dueCount} due today")
                    cd.dueToday.take(3).forEach { add("• ${it.title}" + if (it.className.isNotBlank()) " (${it.className})" else "") }
                }
            }
            b.setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
        }
        return b.build()
    }

    /** Ends every class countdown (FCM `type=class_end`, sign-out, setting turned off). */
    fun endClassCountdowns(context: Context) = Notifier.cancelTag(context, NotificationIds.TAG_CLASS)

    // endregion

    // region Study timer

    /** A server-pushed study timer (display only; it disappears at its end). */
    fun showRemoteTimer(context: Context, t: StudyTimerLive, nowMillis: Long = System.currentTimeMillis()) {
        val end = t.endsAtMillis ?: return
        if (end <= nowMillis) return
        val n = timerBuilder(context, NotificationChannels.LIVE, t, nowMillis)
            .setContentIntent(Notifier.openAppIntent(context, "studyspot://timer".toUri(), NotificationIds.REMOTE_TIMER))
            .setTimeoutAfter(end - nowMillis)
            .setOnlyAlertOnce(true)
            .build()
        Notifier.post(context, null, NotificationIds.REMOTE_TIMER, n)
    }

    /**
     * The shared look of a timer notification: "Focus · Essay", the class or
     * "Paused", a count-down chronometer to the phase end and a progress bar.
     * The caller adds the tap target, actions and timeout.
     */
    fun timerBuilder(context: Context, channel: String, t: StudyTimerLive, nowMillis: Long): NotificationCompat.Builder {
        val accent = ContextCompat.getColor(context, R.color.ss_accent)
        val title = if (t.assignmentTitle.isNotBlank()) "${t.phaseLabel} · ${t.assignmentTitle}" else t.phaseLabel
        val text = when {
            t.endsAtMillis == null -> "Paused · ${formatMinutesSeconds(t.remainingSeconds)} left"
            else -> {
                val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t.endsAtMillis))
                listOf(t.className.takeIf { it.isNotBlank() }, "Ends at $at").filterNotNull().joinToString(" · ")
            }
        }
        val progress = t.progress(nowMillis)
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(accent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (t.endsAtMillis != null) {
            b.setWhen(t.endsAtMillis).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            b.setRequestPromotedOngoing(true)
        } else {
            b.setShowWhen(false).setUsesChronometer(false)
        }
        if (supportsPromoted) {
            b.setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100).setColor(accent)))
                    .setProgress(progress),
            )
            if (t.endsAtMillis == null) b.setShortCriticalText("Paused")
        } else {
            b.setProgress(100, progress, false)
        }
        return b
    }

    // endregion

    /** Ends every ongoing Study Spot notification and the local timer (signing out). */
    fun endAll(context: Context) {
        endClassCountdowns(context)
        Notifier.cancel(context, null, NotificationIds.REMOTE_TIMER)
        Notifier.cancel(context, null, NotificationIds.TIMER_ALERT)
        ClassAlarmScheduler.disable(context)
        TimerController.get(context).stop()
    }

    fun formatMinutesSeconds(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    fun parseColor(hex: String?, fallback: Int): Int {
        var s = hex?.trim()?.removePrefix("#") ?: return fallback
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        val v = s.toLongOrNull(16) ?: return fallback
        return when (s.length) {
            6 -> (0xFF000000 or v).toInt()
            8 -> v.toInt()
            else -> fallback
        }
    }
}
