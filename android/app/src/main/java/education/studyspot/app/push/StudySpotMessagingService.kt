package education.studyspot.app.push

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import education.studyspot.app.R
import education.studyspot.app.liveupdates.ClassAlarmScheduler
import education.studyspot.app.liveupdates.LiveUpdates

/**
 * Receives FCM messages (only active when [PushManager.isConfigured]). The
 * server sends data-only messages, so the app builds every notification
 * itself, on its own channels, with a studyspot:// tap target.
 */
class StudySpotMessagingService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION") // still the token-refresh callback in firebase-messaging
    override fun onNewToken(token: String) {
        PushManager.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        handle(applicationContext, PushPayload.parse(message.data))
    }

    companion object {
        fun handle(context: Context, payload: PushPayload) {
            when (payload) {
                is PushPayload.Notification -> showNotification(context, payload)
                is PushPayload.ClassStart -> {
                    // Respect "Class countdowns" (class_live_activities) unless an admin is simulating.
                    if (payload.countdown.simulated || ClassAlarmScheduler.isEnabled(context)) {
                        LiveUpdates.showClassCountdown(context, payload.countdown)
                    }
                }
                PushPayload.ClassEnd -> LiveUpdates.endClassCountdowns(context)
                is PushPayload.StudyTimer -> LiveUpdates.showRemoteTimer(context, payload.timer)
                is PushPayload.Unknown -> Unit
            }
        }

        private fun showNotification(context: Context, p: PushPayload.Notification) {
            val id = p.assignmentId?.let { "assignment:$it".hashCode() } ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            val b = NotificationCompat.Builder(context, p.channel)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(ContextCompat.getColor(context, R.color.ss_accent))
                .setContentTitle(p.title.ifEmpty { context.getString(R.string.app_name) })
                .setContentText(p.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(p.body))
                .setAutoCancel(true)
                .setCategory(if (p.channel == NotificationChannels.REMINDERS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(Notifier.openAppIntent(context, p.link?.toUri(), id))
            p.subtitle?.let { b.setSubText(it) }
            Notifier.post(context, NotificationIds.TAG_PUSH, id, b.build())
        }
    }
}
