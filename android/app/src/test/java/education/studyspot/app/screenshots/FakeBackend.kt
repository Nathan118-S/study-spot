package education.studyspot.app.screenshots

import education.studyspot.app.core.AppJson
import education.studyspot.app.model.User
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Canned answers for the few requests screens make on their own while they
 * are shown (connections, the admin user list, /auth/me). Everything else gets
 * an empty JSON object, so nothing ever reaches the network.
 */
object FakeBackend {
    @Volatile var user: User? = null

    /** GET /api/entities/<Name> answers, as JSON arrays. */
    @Volatile var entities: Map<String, String> = emptyMap()

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain -> respond(chain) })
        .build()

    private fun respond(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath.removePrefix("/api")
        val body = when {
            path == "/auth/me" -> user?.let { AppJson.encodeToString(User.serializer(), it) } ?: "{}"
            path == "/functions/checkGoogleConnections" -> """{"calendar":true,"classroom":true,"blackboard":true}"""
            path == "/functions/icalListFeeds" -> ICAL_FEEDS
            path == "/functions/adminListUsers" -> ADMIN_USERS
            path.startsWith("/entities/") && chain.request().method == "GET" ->
                entities[path.removePrefix("/entities/").substringBefore('/')] ?: "[]"
            else -> "{}"
        }
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private val ICAL_FEEDS = """
        {"feeds":[{"id":"f1","label":"AP Biology","calendar_name":"AP Biology","mode":"auto","last_synced_at":"${java.time.Instant.now().minusSeconds(300)}","last_imported":3},{"id":"f2","label":"Blackboard","kind":"blackboard","mode":"all","last_synced_at":"${java.time.Instant.now().minusSeconds(3600)}","last_imported":0},{"id":"f3","label":"","kind":"canvas","mode":"auto","last_synced_at":"${java.time.Instant.now().minusSeconds(300)}","last_imported":4},{"id":"f4","label":"","kind":"brightspace","mode":"auto","last_error":"The link has expired"}]}
    """.trimIndent()

    private val ADMIN_USERS = """
        {"users":[
          {"id":"u1","username":"alex.rivera","full_name":"Alex Rivera","role":"admin","twofa_enabled":true,"twofa_method":"totp","is_verified":true,"onboarding_completed":true,"created_date":"2025-08-21T15:04:00Z"},
          {"id":"u2","username":"aisha.khan","full_name":"Aisha Khan","role":"user","twofa_enabled":true,"twofa_method":"passkey","is_verified":true,"onboarding_completed":true,"created_date":"2025-09-02T12:00:00Z"},
          {"id":"u3","username":"marcus.t","full_name":"Marcus Thompson","role":"user","is_verified":true,"onboarding_completed":true,"created_date":"2025-09-14T09:30:00Z"},
          {"id":"u4","username":"sofia.r","full_name":"Sofia Ramirez","role":"user","onboarding_completed":false,"created_date":"2025-10-01T18:45:00Z"},
          {"id":"u5","username":"demo","full_name":"Demo Student","role":"demo","onboarding_completed":true,"created_date":"2025-06-01T08:00:00Z"},
          {"id":"u6","username":"liam.p","full_name":"Liam Park","role":"disabled","disabled_reason":"Graduated","created_date":"2024-09-01T08:00:00Z"},
          {"id":"u7","username":"jordan.lee.with.a.really.long.username","full_name":"Jordan Lee-Montgomery","role":"user","is_verified":true,"onboarding_completed":true,"created_date":"2025-11-03T10:00:00Z"}
        ]}
    """.trimIndent()
}
