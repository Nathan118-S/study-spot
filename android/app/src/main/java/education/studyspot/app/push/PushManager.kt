package education.studyspot.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import education.studyspot.app.BuildConfig
import education.studyspot.app.core.ApiClient
import education.studyspot.app.model.User

/**
 * Optional push notifications through Firebase Cloud Messaging.
 *
 * There is no google-services.json / google-services plugin: Firebase is
 * initialised by hand from BuildConfig.FCM_* (set `studyspot.fcm.*` in
 * local.properties). With those empty, [isConfigured] is false and everything
 * here is a no-op, so the app builds and runs without Firebase.
 *
 * TODO(push): the server has no Android device-token endpoint yet (iOS uses
 * /api/devices/alerts with APNs). Add one, then send the token in [register].
 */
object PushManager {
    private const val TAG = "PushManager"

    val isConfigured: Boolean
        get() = BuildConfig.FCM_PROJECT_ID.isNotBlank() && BuildConfig.FCM_APP_ID.isNotBlank() &&
            BuildConfig.FCM_API_KEY.isNotBlank() && BuildConfig.FCM_SENDER_ID.isNotBlank()

    /** Initialises Firebase if configured. Safe to call more than once. Called from StudySpotApplication. */
    fun initialize(context: Context): Boolean {
        if (!isConfigured) return false
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FCM_PROJECT_ID)
                    .setApplicationId(BuildConfig.FCM_APP_ID)
                    .setApiKey(BuildConfig.FCM_API_KEY)
                    .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Firebase init failed; push disabled", e)
            false
        }
    }

    /** Called after sign-in and when reminder settings change. */
    suspend fun configure(context: Context, api: ApiClient, user: User?) {
        if (!isConfigured || user == null || user.isDemo) return
        if (!initialize(context)) return
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        // TODO(push): fetch FirebaseMessaging.getInstance().token and register(api, token).
    }

    /** Called by [StudySpotMessagingService.onNewToken]. */
    fun onNewToken(context: Context, token: String) {
        Log.d(TAG, "New FCM token (${token.length} chars)")
        // TODO(push): register with the server for the signed-in user.
    }

    /** Remove this device's token from the server (signing out). [bearer] is the outgoing session. */
    suspend fun unregister(context: Context, api: ApiClient, bearer: String?) {
        if (!isConfigured || bearer == null) return
        // TODO(push): DELETE the device token on the server using `bearer`.
    }
}
