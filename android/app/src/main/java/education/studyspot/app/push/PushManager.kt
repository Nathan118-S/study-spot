package education.studyspot.app.push

import androidx.core.content.edit
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import education.studyspot.app.BuildConfig
import education.studyspot.app.appContainer
import education.studyspot.app.core.ApiClient
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.get
import education.studyspot.app.model.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optional push notifications through Firebase Cloud Messaging
 * (ios/StudySpot/Services/PushNotifications.swift).
 *
 * There is no google-services.json / google-services plugin: Firebase is
 * initialised by hand from BuildConfig.FCM_* (set `studyspot.fcm.*` in
 * local.properties). With those empty, [isConfigured] is false and everything
 * here is a no-op, so the app builds and runs without Firebase; the timer and
 * the locally scheduled class countdowns still work.
 *
 * The device registers its FCM token with `POST /api/devices/android` while
 * signed in to a non-demo account with reminders and push reminders on, and
 * removes it with `DELETE /api/devices/android` when signing out or turning
 * them off.
 */
object PushManager {
    private const val TAG = "PushManager"
    private const val PREFS = "push"
    private const val KEY_TOKEN = "fcm_token"
    /** "<userId>|<token>" last registered with the server. */
    private const val KEY_REGISTERED = "registered"

    private val mutex = Mutex()

    /** Whether the current user wants pushes; a token that arrives while false isn't sent. */
    @Volatile private var wantsRegistration = false

    val isConfigured: Boolean
        get() = BuildConfig.FCM_PROJECT_ID.isNotBlank() && BuildConfig.FCM_APP_ID.isNotBlank() &&
            BuildConfig.FCM_API_KEY.isNotBlank() && BuildConfig.FCM_SENDER_ID.isNotBlank()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Push reminders on for [user] (iOS PushNotifications.isEnabled). */
    fun isEnabled(user: User?): Boolean {
        if (user == null || user.isDemo || user.mfaPending) return false
        return user.data["reminders_enabled"].boolValue != false && user.data["push_reminders"].boolValue != false
    }

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

    /** Called after sign-in and whenever the user's reminder settings change. */
    suspend fun configure(context: Context, api: ApiClient, user: User?) {
        if (!isEnabled(user)) {
            unregister(context, api, bearer = api.token)
            return
        }
        wantsRegistration = true
        if (!isConfigured || !initialize(context)) return
        mutex.withLock {
            try {
                // Skip registering if the server can't send to Android yet.
                val cfg = runCatching { api.send("/devices/config", method = "GET") }.getOrNull()
                if (cfg != null && cfg["androidPush"].boolValue == false) return
                val messaging = FirebaseMessaging.getInstance()
                messaging.isAutoInitEnabled = true
                // getToken() is marked deprecated in this firebase-messaging version, but it still
                // returns the current token directly, which is what the server registration needs.
                @Suppress("DEPRECATION")
                val token = messaging.token.await()
                prefs(context).edit { putString(KEY_TOKEN, token) }
                register(context, api, user!!.id, token)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Push registration failed", e)
            }
        }
    }

    private suspend fun register(context: Context, api: ApiClient, userId: String, token: String) {
        val key = "$userId|$token"
        if (prefs(context).getString(KEY_REGISTERED, null) == key) return
        api.send("/devices/android", body = buildJsonObject {
            put("token", token)
            put("packageName", context.packageName)
        })
        prefs(context).edit { putString(KEY_REGISTERED, key) }
    }

    /** Called by [StudySpotMessagingService.onNewToken]. */
    fun onNewToken(context: Context, token: String) {
        prefs(context).edit { putString(KEY_TOKEN, token).remove(KEY_REGISTERED) }
        if (!wantsRegistration) return
        val c = context.appContainer
        val user = c.repository.user.value ?: return
        if (!isEnabled(user) || c.api.token == null) return
        c.scope.launch {
            mutex.withLock { runCatching { register(context, c.api, user.id, token) } }
        }
    }

    /** Remove this device's token from the server (signing out, push turned off). [bearer] is the session to use. */
    suspend fun unregister(context: Context, api: ApiClient, bearer: String?) {
        wantsRegistration = false
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null)
        if (!isConfigured || bearer == null || token == null || p.getString(KEY_REGISTERED, null) == null) return
        runCatching {
            api.send("/devices/android", method = "DELETE", body = buildJsonObject { put("token", token) }, bearer = bearer)
        }.onSuccess { p.edit { remove(KEY_REGISTERED) } }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { t ->
            val e = t.exception
            when {
                e != null -> cont.resumeWithException(e)
                t.isCanceled -> cont.cancel()
                else -> cont.resume(t.result)
            }
        }
    }
}
