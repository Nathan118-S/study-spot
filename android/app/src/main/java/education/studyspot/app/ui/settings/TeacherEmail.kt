package education.studyspot.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.model.User
import java.net.URLEncoder

/**
 * A drafted email asking a teacher to make the class's Google Calendar public,
 * so students can add it with a calendar link even when the school blocks
 * connecting Google accounts. No recipient; the student adds it and the
 * teacher's name. The steps for teachers are on the website's /teachers page.
 */
object TeacherEmail {
    const val PLACEHOLDER = "[Teacher's name]"

    const val SUBJECT = "Could you share our class calendar?"

    fun body(name: String): String = listOf(
        "Hi $PLACEHOLDER,",
        "",
        "I use Study Spot (https://studyspot.education) to keep track of my assignments, but our school doesn't let me connect my Google account to it.",
        "",
        "If you make our class's Google Calendar public, I (and anyone in our class) can add our assignments to Study Spot with a link. " +
            "It only shares the assignment titles and due dates already on the class calendar, not any student information, and it takes about a minute. " +
            "The steps are here: https://studyspot.education/teachers",
        "",
        "Thank you!",
        "",
        name,
    ).joinToString("\n")

    /** The body signed with the user's name (same rule as [SchoolITEmail.senderName]). */
    fun body(user: User?): String = body(SchoolITEmail.senderName(user))

    /** `mailto:?subject=…&body=…` with RFC 6068 percent-encoding (spaces as %20, not +). */
    fun mailto(name: String): String = "mailto:?subject=${encode(SUBJECT)}&body=${encode(body(name))}"

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** "Before you send it", then the drafted email to a teacher in the user's mail app. */
@Composable
fun TeacherEmailConfirmDialog(onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val user by c.repository.user.collectAsStateWithLifecycle()
    ConfirmDialog(
        title = "Before you send it",
        message = "We'll open a drafted email to your teacher. Add your teacher's email address and their name where it says ${TeacherEmail.PLACEHOLDER}.",
        confirmLabel = "Open email",
        destructive = false,
        onConfirm = {
            if (!openTeacherEmail(context, user)) {
                c.messages.show("No email app found", "Install or set up an email app to send this.", error = true)
            }
        },
        onDismiss = onDismiss,
    )
}

/** Opens the drafted email in the user's mail app. Returns false if there is none. */
fun openTeacherEmail(context: Context, user: User?): Boolean {
    val name = SchoolITEmail.senderName(user)
    val intent = Intent(Intent.ACTION_SENDTO, TeacherEmail.mailto(name).toUri()).apply {
        // Some mail apps read the extras rather than the mailto: query.
        putExtra(Intent.EXTRA_SUBJECT, TeacherEmail.SUBJECT)
        putExtra(Intent.EXTRA_TEXT, TeacherEmail.body(name))
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
