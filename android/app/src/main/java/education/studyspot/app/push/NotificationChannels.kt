package education.studyspot.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import education.studyspot.app.R

/** Notification channels (created at startup; harmless without push). */
object NotificationChannels {
    const val REMINDERS = "reminders"
    const val GENERAL = "general"
    /** Study timer / class Live Updates (see liveupdates/LiveUpdates.kt). */
    const val LIVE = "live_updates"

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(REMINDERS, context.getString(R.string.push_channel_reminders), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(GENERAL, context.getString(R.string.push_channel_general), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(LIVE, "Study timer and classes", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }
}
