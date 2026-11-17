package education.studyspot.app.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import education.studyspot.app.core.ApiClient
import education.studyspot.app.core.Dates
import education.studyspot.app.core.arrayValue
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.core.userMessage
import education.studyspot.app.data.MessageCenter
import education.studyspot.app.model.AdminUser
import education.studyspot.app.model.User
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The People list (functions/adminListUsers), shared by People, Merge and Send Notification. */
class AdminPeopleModel(private val api: ApiClient, private val messages: MessageCenter) {
    var users by mutableStateOf<List<AdminUser>>(emptyList())
    var loading by mutableStateOf(false)

    suspend fun load() {
        loading = true
        try {
            users = AdminParse.users(api.invoke("adminListUsers"))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Failed to load users", e.userMessage, error = true)
        }
        loading = false
    }
}

/**
 * One account in the admin account manager (AdminAccountModel in
 * ios/StudySpot/Views/Admin/AdminAccountViews.swift). Every call needs the 2FA
 * step-up; [AdminUnlock.run] asks for it and retries once when it has expired.
 */
class AdminAccountModel(val userId: String, private val api: ApiClient, private val messages: MessageCenter) {
    var user by mutableStateOf<User?>(null)
    var hasPassword by mutableStateOf(false)
    var methods by mutableStateOf<List<String>>(emptyList())
    var stats by mutableStateOf(JsonObject(emptyMap()))
    var google by mutableStateOf<List<AdminGoogleConnection>>(emptyList())
    var blackboard by mutableStateOf<JsonElement>(JsonNull)
    var devices by mutableStateOf<List<AdminDevice>>(emptyList())
    var loading by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    /** Set after the account is deleted or merged away. */
    var gone by mutableStateOf(false)

    fun stat(key: String): Int = (stats[key].doubleValue ?: 0.0).toInt()

    suspend fun load() {
        loading = true
        try {
            apply(AdminUnlock.run(api) { api.invoke("adminGetUser", buildJsonObject { put("userId", userId) }) })
            loadError = null
        } catch (_: AdminUnlock.Cancelled) {
            loadError = "Unlock with two-factor authentication to open this account."
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loadError = e.userMessage
        }
        loading = false
    }

    private fun apply(res: JsonElement) {
        res["user"]?.let { u ->
            user = AdminParse.user(u)
            hasPassword = u["has_password"].boolValue == true
            methods = u["twofa_methods"].arrayValue.orEmpty().mapNotNull { it.stringValue }
        }
        stats = res["stats"] as? JsonObject ?: JsonObject(emptyMap())
        google = res["connections"]["google"].arrayValue.orEmpty().map { g ->
            AdminGoogleConnection(
                connector = g["connector"].stringValue.orEmpty(),
                scope = g["scope"].stringValue.orEmpty(),
                expiresAt = Dates.parse(g["expires_at"].stringValue),
                createdAt = Dates.parse(g["created_at"].stringValue),
            )
        }
        blackboard = res["connections"]["blackboard"] ?: JsonNull
        devices = res["devices"].arrayValue.orEmpty().map(AdminDevice::from)
    }

    /** Calls a gated admin function for this user (adds `userId`). Throws [AdminUnlock.Cancelled] if the unlock is dismissed. */
    suspend fun call(name: String, payload: JsonObject = JsonObject(emptyMap())): JsonElement {
        val body = JsonObject(payload + ("userId" to kotlinx.serialization.json.JsonPrimitive(userId)))
        return AdminUnlock.run(api) { api.invoke(name, body) }
    }

    /** [call] with a message for the outcome. Returns the response, or null on failure. */
    suspend fun perform(
        name: String,
        payload: JsonObject = JsonObject(emptyMap()),
        success: String?,
        detail: String? = null,
        reload: Boolean = true,
    ): JsonElement? {
        busy = true
        try {
            val res = call(name, payload)
            if (success != null) messages.show(success, detail)
            if (reload) load()
            return res
        } catch (_: AdminUnlock.Cancelled) {
            return null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Failed", e.userMessage, error = true)
            return null
        } finally {
            busy = false
        }
    }

    /** Changes keys in the user's settings (user.data), updating the screen right away. */
    suspend fun updateSettings(settings: JsonObject) {
        user?.let { u -> user = u.copy(data = JsonObject(u.data + settings)) }
        val res = perform("adminUpdateUserSettings", buildJsonObject { put("settings", settings) }, success = null, reload = false)
        val updated = AdminParse.user(res?.get("user"))
        if (updated != null) user = user?.copy(data = updated.data) ?: updated else load()
    }

    companion object {
        /** Outcome text for adminSendTestNotification. */
        fun describeTest(res: JsonElement): String {
            val devices = (res["devices"].doubleValue ?: 0.0).toInt()
            if (devices == 0) return "Added to their notification center. No phone is registered for push."
            return if (res["push_delivered"].boolValue == true) {
                "Added to their notification center and pushed to $devices device(s)."
            } else {
                "Added to their notification center, but the push wasn't delivered. Check the push secrets."
            }
        }
    }
}
