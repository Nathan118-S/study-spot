package education.studyspot.app.core

import android.content.Context
import androidx.core.content.edit
import education.studyspot.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

/**
 * Server and website addresses (mirrors ios/StudySpot/Core/AppConfig.swift).
 *
 * The server defaults to [BuildConfig.DEFAULT_SERVER_URL]
 * (`studyspot.serverUrl` in gradle.properties / local.properties, normally
 * https://studyspot.education) and can be overridden in the hidden Server
 * settings screen (long-press the logo on the welcome screen).
 */
class AppConfig(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val defaultServerUrl: String =
        normalize(BuildConfig.DEFAULT_SERVER_URL).ifEmpty { PRODUCTION_SERVER_URL }

    private val _serverUrl = MutableStateFlow(resolveServerUrl())

    /** Base URL of the server, e.g. "https://studyspot.education" (no trailing slash). */
    val serverUrlFlow: StateFlow<String> = _serverUrl.asStateFlow()

    var serverUrl: String
        get() = _serverUrl.value
        set(value) {
            val n = normalize(value)
            prefs.edit { if (n.isEmpty()) remove(KEY_SERVER) else putString(KEY_SERVER, n) }
            _serverUrl.value = resolveServerUrl()
        }

    /** Explicit website override, or null when the website lives on the server address. */
    var webUrlOverride: String?
        get() = prefs.getString(KEY_WEB, null)?.takeIf { it.isNotBlank() }
        set(value) {
            val n = normalize(value.orEmpty())
            prefs.edit { if (n.isEmpty()) remove(KEY_WEB) else putString(KEY_WEB, n) }
        }

    /** The Study Spot website, where users sign in. */
    val webUrl: String
        get() {
            val stored = webUrlOverride?.let(::normalize).orEmpty()
            if (stored.isEmpty() || (isLocalHost(hostOf(stored)) && !isLocalHost(hostOf(serverUrl)))) {
                return serverUrl
            }
            return stored
        }

    /** The API lives under /api on the server. */
    val apiBaseUrl: String
        get() = apiBase(serverUrl)

    /** Relying-party ID for passkeys: the server's host. */
    val passkeyRelyingParty: String
        get() = hostOf(serverUrl) ?: "localhost"

    fun resetToDefaults() {
        prefs.edit { remove(KEY_SERVER); remove(KEY_WEB) }
        _serverUrl.value = resolveServerUrl()
    }

    private fun resolveServerUrl(): String {
        val stored = normalize(prefs.getString(KEY_SERVER, null).orEmpty())
        // A localhost address saved by a development build never works on a
        // phone when the default is a real server: fall back to the default.
        if (stored.isEmpty() || (isLocalHost(hostOf(stored)) && !isLocalHost(hostOf(defaultServerUrl)))) {
            return defaultServerUrl
        }
        return stored
    }

    companion object {
        const val PRODUCTION_SERVER_URL = "https://studyspot.education"
        const val URL_SCHEME = "studyspot"
        /** Where the website hands the session back (see src/lib/iosHandoff.js). */
        const val AUTH_CALLBACK = "studyspot://oauth/callback"
        /** Sent as `?client=` to the website's /login and /register (src/lib/iosHandoff.js). */
        const val CLIENT_ID = "android"

        /**
         * `client` value for the Google connector (?client=) and Blackboard
         * (blackboardAuthUrl { client }) flows. The server only recognises "ios"
         * there (server/src/routes/googleAuth.js, functions/blackboard.js); it
         * means "return to studyspot://", which is this app's scheme too.
         * Switch to [CLIENT_ID] once the server accepts "android".
         */
        const val CONNECTOR_CLIENT_ID = "android"

        private const val PREFS = "app_config"
        private const val KEY_SERVER = "ss_server_url"
        private const val KEY_WEB = "ss_web_url"

        fun apiBase(server: String): String {
            val base = normalize(server)
            return if (base.endsWith("/api")) base else "$base/api"
        }

        fun normalize(raw: String): String {
            var s = raw.trim()
            while (s.endsWith("/")) s = s.dropLast(1)
            if (s.isNotEmpty() && !s.startsWith("http://", true) && !s.startsWith("https://", true)) {
                s = "https://$s"
            }
            return s
        }

        fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()

        fun isLocalHost(host: String?): Boolean {
            val h = host?.lowercase() ?: return false
            return h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "[::1]" || h.endsWith(".localhost")
        }
    }
}
