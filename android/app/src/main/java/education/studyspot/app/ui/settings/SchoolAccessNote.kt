package education.studyspot.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.model.User
import education.studyspot.app.ui.theme.StudySpotTheme

/**
 * Shown with the Google / Blackboard connect UI (settings and onboarding):
 * many schools restrict which third-party apps can reach school accounts.
 * Offers a drafted email to the school's IT team (see [SchoolITEmail]) and,
 * for Google, the calendar-link guide ([IcalGuideDialog]) as a way around it.
 * Mirrors SchoolAccessNote in ios/StudySpot/Views/Settings/SchoolITEmail.swift.
 */
@Composable
fun SchoolAccessNote(service: SchoolITEmail.Service, modifier: Modifier = Modifier) {
    var confirming by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    val navy = StudySpotTheme.colors.secondary

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = navy, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Text(SchoolITEmail.noticeText(service), style = MaterialTheme.typography.bodySmall, color = navy)
        }
        TextButton(onClick = { confirming = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Icon(Icons.Outlined.Email, contentDescription = null, tint = navy, modifier = Modifier.size(16.dp))
            Text(
                "Email my school's IT team",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = navy,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (service != SchoolITEmail.Service.Blackboard) {
            TextButton(onClick = { showGuide = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = navy, modifier = Modifier.size(16.dp))
                Text(
                    "Use a calendar link instead",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = navy,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }

    if (confirming) SchoolITEmailConfirmDialog(service, onDismiss = { confirming = false })
    if (showGuide) IcalGuideDialog(onDismiss = { showGuide = false })
}

/** Explains the drafted email, then opens it in the user's mail app. */
@Composable
fun SchoolITEmailConfirmDialog(service: SchoolITEmail.Service, onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val user by c.repository.user.collectAsStateWithLifecycle()
    ConfirmDialog(
        title = "Before you send it",
        message = "We'll open a drafted email in your mail app asking your school to approve Study Spot, signed with your name.\n\n" +
            "Replace every “${SchoolITEmail.PLACEHOLDER}” with your school's name, and add your IT team's email address as the recipient.",
        confirmLabel = "Open email",
        destructive = false,
        onConfirm = {
            if (!openSchoolITEmail(context, user, service)) {
                c.messages.show("No email app found", "Install or set up an email app to send this.", error = true)
            }
        },
        onDismiss = onDismiss,
    )
}

/** Opens the drafted email in the user's mail app. Returns false if there is none. */
fun openSchoolITEmail(context: Context, user: User?, service: SchoolITEmail.Service): Boolean {
    val name = SchoolITEmail.senderName(user)
    val intent = Intent(Intent.ACTION_SENDTO, SchoolITEmail.mailto(name, service).toUri()).apply {
        // Some mail apps read the extras rather than the mailto: query.
        putExtra(Intent.EXTRA_SUBJECT, SchoolITEmail.subject)
        putExtra(Intent.EXTRA_TEXT, SchoolITEmail.body(name, service))
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
