package education.studyspot.app.ui.settings

import education.studyspot.app.model.User
import java.net.URLEncoder

/**
 * A ready-to-send email asking a school's IT team to approve Study Spot, for
 * when the school blocks connecting third-party apps. Opened in the user's
 * mail app; they add the recipient and the school's name.
 * Same text as ios/StudySpot/Views/Settings/SchoolITEmail.swift and the
 * website's src/lib/schoolItEmail.js.
 */
object SchoolITEmail {
    enum class Service { Google, Blackboard, Both }

    const val PLACEHOLDER = "[School name]"

    /** The user's full name, else their name, else their username (before any "@"), else "A student". */
    fun senderName(user: User?): String = senderName(user?.fullName, user?.name, user?.username)

    fun senderName(fullName: String?, name: String?, username: String?): String {
        fullName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val u = username.orEmpty().split("@").firstOrNull().orEmpty().trim()
        return u.ifEmpty { "A student" }
    }

    /** "school Google" / "Blackboard", as used in the on-screen notice. */
    fun serviceLabel(service: Service): String = when (service) {
        Service.Google -> "school Google"
        Service.Blackboard -> "Blackboard"
        Service.Both -> "school Google or Blackboard"
    }

    fun noticeText(service: Service): String =
        "Some schools block connecting third-party apps like Study Spot to ${serviceLabel(service)} accounts. " +
            "If the connection is blocked or fails, ask your school's IT team, or add your assignments manually."

    private fun what(service: Service): String = when (service) {
        Service.Google -> "my school Google account (Google Calendar and Google Classroom)"
        Service.Blackboard -> "my Blackboard account"
        Service.Both -> "my school Google account (Google Calendar and Google Classroom) and my Blackboard account"
    }

    private val googleDetails = listOf(
        "Study Spot only asks for read-only access to Google Calendar and to my Google Classroom courses and coursework (and, if I choose, marking my own Classroom work as turned in). It doesn't ask for access to Gmail or Drive.",
        "In the Google Admin console this is under Security → Access and data control → API controls → App access control, where Study Spot can be marked as trusted.",
    )
    private val blackboardDetails = listOf(
        "Study Spot uses Blackboard's REST API to read my courses and the due dates in the gradebook. It doesn't change anything in Blackboard.",
        "In Blackboard this is done by an administrator under REST API Integrations.",
    )

    val subject: String get() = "Request to approve Study Spot at $PLACEHOLDER"

    fun body(name: String, service: Service): String {
        val details = buildList {
            if (service != Service.Blackboard) { addAll(googleDetails); add("") }
            if (service != Service.Google) { addAll(blackboardDetails); add("") }
        }
        return (
            listOf(
                "Hello $PLACEHOLDER IT team,",
                "",
                "My name is $name, and I'm a student at $PLACEHOLDER. I use Study Spot (https://studyspot.education), a study planner that brings my assignments and due dates into one place so I can keep up with my schoolwork.",
                "",
                "When I try to connect ${what(service)}, it looks like the school has to approve third-party apps first. Could you please approve Study Spot so students can connect it?",
                "",
            ) + details + listOf("Thank you for your help!", "", name)
            ).joinToString("\n")
    }

    /** `mailto:?subject=…&body=…` with RFC 6068 percent-encoding (spaces as %20, not +). */
    fun mailto(name: String, service: Service): String =
        "mailto:?subject=${encode(subject)}&body=${encode(body(name, service))}"

    fun mailto(user: User?, service: Service): String = mailto(senderName(user), service)

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
