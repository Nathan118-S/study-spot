package education.studyspot.app.navigation

import android.net.Uri
import education.studyspot.app.AppContainer
import education.studyspot.app.auth.WebSignIn
import education.studyspot.app.core.AppConfig
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.IcalKind
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Handles studyspot:// links (mirrors StudySpotApp.handle(_:) on iOS):
 *
 * - `studyspot://oauth/callback#token=…`   website sign-in hand-off
 * - `studyspot://assignment/<id>`          open an assignment
 * - `studyspot://tab/<dashboard|classes|calendar|streaks|insights>`
 * - `studyspot://timer`                    open the study timer
 * - `studyspot://blackboard?code=…&state=…` finish connecting Blackboard
 * - `studyspot://connected`                a Google connection finished
 */
class DeepLinkHandler(private val c: AppContainer) {
    suspend fun handle(uri: Uri) {
        if (uri.scheme != AppConfig.URL_SCHEME) return
        when (uri.host) {
            "oauth" -> {
                val token = WebSignIn.param(uri, "token")
                if (!token.isNullOrEmpty()) {
                    c.auth.didSignIn(token)
                } else {
                    WebSignIn.param(uri, "error")?.let { c.messages.show("Sign-in didn't finish", it, error = true) }
                }
            }
            "assignment" -> uri.lastPathSegment?.let { id ->
                c.router.showTab(AppTab.Dashboard)
                c.router.navigate(Routes.assignmentDetail(id))
            }
            "tab" -> c.router.showTab(AppTab.fromKey(uri.lastPathSegment))
            "timer" -> c.router.navigate(Routes.TIMER)
            "blackboard" -> {
                val code = WebSignIn.param(uri, "code")
                if (code == null) {
                    val description = WebSignIn.param(uri, "error_description").orEmpty()
                    val notApproved = Regex("not registered|unregistered|invalid application", RegexOption.IGNORE_CASE).containsMatchIn(description)
                    if (notApproved) {
                        c.messages.show(
                            "Your school needs to approve Study Spot",
                            "Your school hasn’t approved Study Spot in Blackboard yet. A Blackboard admin at your school needs to add Study Spot under REST API Integrations. Ask your school’s IT team, or add your assignments yourself until then.",
                            error = true,
                            actionLabel = "Use a calendar link instead",
                            onAction = { c.router.openCalendarLinkGuide(IcalKind.Blackboard) },
                        )
                    } else {
                        c.messages.show(
                            "Blackboard connection failed",
                            description.ifEmpty { "Blackboard didn't return an authorization code." },
                            error = true,
                        )
                    }
                    return
                }
                try {
                    c.api.invoke("blackboardCallback", buildJsonObject {
                        put("code", code)
                        WebSignIn.param(uri, "state")?.let { put("state", it) }
                    })
                    c.repository.refreshConnections()
                    c.messages.show("Blackboard connected")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    c.messages.show("Blackboard connection failed", e.userMessage, error = true)
                }
            }
            "connected" -> c.repository.refreshConnections()
        }
    }
}
