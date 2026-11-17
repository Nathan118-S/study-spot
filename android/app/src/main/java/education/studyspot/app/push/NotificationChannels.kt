package education.studyspot.app.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import education.studyspot.app.MainActivity
import education.studyspot.app.R

/**
 * Notification channels (created at startup; harmless without push). The
 * server picks reminders / streaks / general for its pushes
 * (server/src/lib/notify.js ANDROID_CHANNELS).
 */
object NotificationChannels {
    const val REMINDERS = "reminders"
    const val STREAKS = "streaks"
    const val GENERAL = "general"
    /** Class countdowns and pushed study timers (see liveupdates/LiveUpdates.kt). */
    const val LIVE = "live_updates"
    /** The running study timer and its "phase finished" alert. */
    const val TIMER = "timer"

    /** Channels the server may name in a `type=notification` push. */
    val pushChannels = setOf(REMINDERS, STREAKS, GENERAL)

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(REMINDERS, context.getString(R.string.push_channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.push_channel_reminders_desc)
                },
                NotificationChannel(STREAKS, context.getString(R.string.push_channel_streaks), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.push_channel_streaks_desc)
                },
                NotificationChannel(GENERAL, context.getString(R.string.push_channel_general), NotificationManager.IMPORTANCE_DEFAULT),
                // Not MIN: promoted (Live Update) notifications need a visible channel.
                NotificationChannel(LIVE, context.getString(R.string.push_channel_live), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.push_channel_live_desc)
                },
                NotificationChannel(TIMER, context.getString(R.string.push_channel_timer), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.push_channel_timer_desc)
                },
            ),
        )
    }
}

/** Posting helpers shared by push, live updates and the timer. */
object Notifier {
    /** Whether the app may post notifications (POST_NOTIFICATIONS on 13+, and not blocked in settings). */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts [notification] if allowed; silently skips otherwise. */
    fun post(context: Context, tag: String?, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(tag, id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel(context: Context, tag: String?, id: Int) {
        NotificationManagerCompat.from(context).cancel(tag, id)
    }

    /** Cancels every posted notification with [tag]. */
    fun cancelTag(context: Context, tag: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.activeNotifications }.getOrNull()?.forEach { sbn ->
            if (sbn.tag == tag) nm.cancel(sbn.tag, sbn.id)
        }
    }

    /**
     * Opens the app at a studyspot:// link (or just the app when [uri] is
     * null). MainActivity is singleTask, so the link reaches onNewIntent.
     */
    fun openAppIntent(context: Context, uri: Uri?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (uri != null) {
                action = Intent.ACTION_VIEW
                data = uri
            } else {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Notification ids (one place so features don't collide). */
object NotificationIds {
    const val TIMER_ONGOING = 1001
    const val TIMER_ALERT = 1002
    const val REMOTE_TIMER = 1003

    const val TAG_CLASS = "class_countdown"
    const val TAG_PUSH = "push"

    /** One id per class meeting, so a push and the local alarm replace each other. */
    fun classMeeting(classId: String, startsAtEpochSecond: Long): Int =
        "class:$classId:$startsAtEpochSecond".hashCode()
}
