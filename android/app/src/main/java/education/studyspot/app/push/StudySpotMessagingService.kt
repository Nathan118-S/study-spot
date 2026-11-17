package education.studyspot.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives FCM messages (only active when [PushManager.isConfigured]). */
class StudySpotMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushManager.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Notification payloads are shown by the system while in the background.
        // TODO(push): show data messages / foreground notifications via NotificationChannels,
        // with a studyspot:// deep link as the content intent.
    }
}
