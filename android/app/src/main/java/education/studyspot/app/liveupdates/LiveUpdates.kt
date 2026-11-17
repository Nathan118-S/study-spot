package education.studyspot.app.liveupdates

import android.content.Context
import android.os.Build

/**
 * Android's counterpart to the iOS Live Activities (study timer, "class starts
 * soon"): ongoing notifications, promoted to Live Updates on Android 16+
 * (Notification.ProgressStyle / requestPromotedOngoing).
 * Use push.NotificationChannels.LIVE.
 *
 * TODO(liveupdates): implement startTimer / update / end and class reminders.
 */
object LiveUpdates {
    /** Promoted ongoing notifications (Live Updates) exist from Android 16 (API 36). */
    val supportsPromoted: Boolean get() = Build.VERSION.SDK_INT >= 36

    /** Ends every ongoing Study Spot notification (signing out). */
    @Suppress("UNUSED_PARAMETER")
    fun endAll(context: Context) {
        // TODO(liveupdates): cancel the timer and class notifications.
    }
}
