package education.studyspot.app.core

import education.studyspot.app.model.AppNotification
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentTemplate
import education.studyspot.app.model.Connections
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.StudySession
import education.studyspot.app.model.User
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Entity names for /api/entities/<Name> (server/src/routes/entities.js). */
object Entities {
    const val ASSIGNMENT = "Assignment"
    const val CLASS = "Class"
    const val TEMPLATE = "AssignmentTemplate"
    const val STUDY_SESSION = "StudySession"
    const val NOTIFICATION = "Notification"
}

/** Session-level signals the auth layer reacts to. */
sealed interface ApiEvent {
    /** A request with the stored session got 401: the session is gone. */
    data object Unauthorized : ApiEvent
    /** The session still needs its second factor (401 with `mfaRequired`). */
    data object MfaRequired : ApiEvent
}

/**
 * Talks to the Study Spot Express/PostgreSQL backend (`server/`). Mirrors
 * src/api/client.js and ios/StudySpot/Core/APIClient.swift: auth.*, entities.*,
 * functions.invoke, connectors.*.
 *
 * Errors are thrown as [ApiException], built from the server's `{ error }` JSON
 * plus its flags (`mfaRequired`, `elevationRequired`, `notConnected`).
 */
class ApiClient(
    private val config: AppConfig,
    private val tokens: TokenStore,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val _events = MutableSharedFlow<ApiEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ApiEvent> = _events.asSharedFlow()

    /** The bearer session token (encrypted at rest by [TokenStore]). */
    var token: String?
        get() = tokens.token
        set(value) {
            tokens.token = value
            // The admin step-up is tied to the session it was issued for.
            adminElevation = null
        }

    /**
     * Short-lived admin elevation token, sent as `X-Admin-Elevation` on
     * /functions calls. Memory only: gone when the process dies or the session changes.
     */
    @Volatile
    var adminElevation: String? = null

    // region Core request

    /**
     * Performs a request against `<server>/api<path>` and returns the body text.
     * [bearer] overrides the stored session (e.g. to unregister while signing out).
     */
    suspend fun requestRaw(path: String, method: String = "GET", body: JsonElement? = null, bearer: String? = null): String {
        val url = (config.apiBaseUrl + path).toHttpUrlOrNull()
            ?: throw ApiException(0, "Invalid server URL. Check the server address in Server settings.")
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        val requestBody = body?.let { AppJson.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON_MEDIA) }
        builder.method(method, requestBody ?: if (method == "POST" || method == "PATCH" || method == "PUT") ByteArray(0).toRequestBody(JSON_MEDIA) else null)
        val usedStored = bearer == null
        val auth = bearer ?: token
        if (auth != null) builder.header("Authorization", "Bearer $auth")
        if (path.startsWith("/functions/")) adminElevation?.let { builder.header("X-Admin-Elevation", it) }

        val (status, text) = try {
            http.newCall(builder.build()).await().use { res -> res.code to (res.body.string()) }
        } catch (e: IOException) {
            throw ApiException(0, "Couldn't reach the server. ${e.localizedMessage.orEmpty()}".trim())
        }
        if (status !in 200..299) {
            val json = runCatching { AppJson.parseToJsonElement(text) }.getOrNull()
            val error = ApiException(
                status = status,
                message = json["error"].stringValue ?: "Request failed ($status)",
                notConnected = json["notConnected"].boolValue == true,
                elevationRequired = json["elevationRequired"].boolValue == true,
                mfaRequired = json["mfaRequired"].boolValue == true,
            )
            if (status == 401 && usedStored && auth != null) {
                _events.tryEmit(if (error.mfaRequired) ApiEvent.MfaRequired else ApiEvent.Unauthorized)
            }
            throw error
        }
        return text
    }

    suspend fun <T> request(path: String, serializer: KSerializer<T>, method: String = "GET", body: JsonElement? = null): T {
        val text = requestRaw(path, method, body)
        return try {
            AppJson.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw ApiException(0, "Unexpected response from server.", cause = e)
        } catch (e: IllegalArgumentException) {
            throw ApiException(0, "Unexpected response from server.", cause = e)
        }
    }

    suspend inline fun <reified T> request(path: String, method: String = "GET", body: JsonElement? = null): T =
        request(path, serializer<T>(), method, body)

    /** Sends a request and returns the JSON result (JsonNull for an empty body). */
    suspend fun send(path: String, method: String = "POST", body: JsonElement? = null, bearer: String? = null): JsonElement {
        val text = requestRaw(path, method, body, bearer)
        if (text.isBlank()) return JsonNull
        return runCatching { AppJson.parseToJsonElement(text) }.getOrDefault(JsonNull)
    }

    // endregion

    // region Auth (/api/auth)

    suspend fun me(): User = request("/auth/me", User.serializer())

    suspend fun updateMe(patch: JsonObject): User = request("/auth/me", User.serializer(), "PATCH", patch)

    suspend fun changePassword(current: String, newPassword: String) {
        send("/auth/change-password", body = buildJsonObject {
            put("currentPassword", current)
            put("newPassword", newPassword)
        })
    }

    // endregion

    // region Entities (/api/entities/<Name>)

    suspend fun <T> list(entity: String, serializer: KSerializer<T>, sort: String? = null, limit: Int? = null): List<T> {
        val query = buildList {
            sort?.let { add("sort=" + java.net.URLEncoder.encode(it, "UTF-8")) }
            limit?.let { add("limit=$it") }
        }.joinToString("&")
        return request("/entities/$entity" + if (query.isEmpty()) "" else "?$query", ListSerializer(serializer))
    }

    suspend fun <T> create(entity: String, serializer: KSerializer<T>, data: JsonObject): T =
        request("/entities/$entity", serializer, "POST", data)

    suspend fun <T> update(entity: String, id: String, serializer: KSerializer<T>, data: JsonObject): T =
        request("/entities/$entity/$id", serializer, "PATCH", data)

    suspend fun delete(entity: String, id: String) {
        send("/entities/$entity/$id", method = "DELETE")
    }

    suspend inline fun <reified T> list(entity: String, sort: String? = null, limit: Int? = null): List<T> =
        list(entity, serializer<T>(), sort, limit)

    suspend inline fun <reified T> create(entity: String, data: JsonObject): T = create(entity, serializer<T>(), data)

    suspend inline fun <reified T> update(entity: String, id: String, data: JsonObject): T =
        update(entity, id, serializer<T>(), data)

    // Typed helpers (same sorts/limits as the iOS app).
    suspend fun assignments(sort: String = "-due_date", limit: Int = 500): List<Assignment> =
        list(Entities.ASSIGNMENT, Assignment.serializer(), sort, limit)

    suspend fun classes(): List<SchoolClass> = list(Entities.CLASS, SchoolClass.serializer())

    suspend fun templates(): List<AssignmentTemplate> =
        list(Entities.TEMPLATE, AssignmentTemplate.serializer(), "-updated_date", 100)

    suspend fun notifications(): List<AppNotification> =
        list(Entities.NOTIFICATION, AppNotification.serializer(), "-created_date", 30)

    suspend fun studySessions(limit: Int = 1000): List<StudySession> =
        list(Entities.STUDY_SESSION, StudySession.serializer(), "-created_date", limit)

    suspend fun updateAssignment(id: String, data: JsonObject): Assignment =
        update(Entities.ASSIGNMENT, id, Assignment.serializer(), data)

    // endregion

    // region Functions (/api/functions/<name>, server/src/routes/functions.js)

    suspend fun invoke(name: String, payload: JsonObject = JsonObject(emptyMap())): JsonElement =
        send("/functions/$name", body = payload)

    suspend fun <T> invoke(name: String, serializer: KSerializer<T>, payload: JsonObject = JsonObject(emptyMap())): T =
        request("/functions/$name", serializer, "POST", payload)

    suspend inline fun <reified T> invokeAs(name: String, payload: JsonObject = JsonObject(emptyMap())): T =
        invoke(name, serializer<T>(), payload)

    suspend fun connections(): Connections = invoke("checkGoogleConnections", Connections.serializer())

    // endregion

    // region Connectors

    /**
     * URL that starts a Google Calendar/Classroom connection in the browser.
     * NOTE: the server currently only returns to the app for `client=ios`
     * (server/src/routes/googleAuth.js); Android support there is a follow-up.
     */
    suspend fun googleConnectorUrl(connector: String): String {
        val res = send("/connectors/google/start?connector=$connector&client=${AppConfig.CLIENT_ID}", method = "GET")
        return res["url"].stringValue ?: throw ApiException(0, "Couldn't start the Google connection.")
    }

    suspend fun disconnectGoogle(connector: String) {
        send("/connectors/google/$connector", method = "DELETE")
    }

    // endregion

    /** GET <server>/api/health for the Server settings "Test connection" button. */
    suspend fun checkHealth(serverUrl: String): Boolean {
        val url = (AppConfig.apiBase(serverUrl) + "/health").toHttpUrlOrNull() ?: throw ApiException(0, "Invalid address")
        return try {
            http.newCall(Request.Builder().url(url).build()).await().use { it.isSuccessful }
        } catch (e: IOException) {
            throw ApiException(0, e.localizedMessage ?: "Couldn't reach the server.")
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/** Suspends until the call completes; cancels the call if the coroutine is cancelled. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, _, _ -> response.close() }
        }
    })
}
