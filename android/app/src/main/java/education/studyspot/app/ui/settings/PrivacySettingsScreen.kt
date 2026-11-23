package education.studyspot.app.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import education.studyspot.app.ui.theme.StudySpotTheme

/** Settings > Privacy Policy (PrivacyPolicyView in ios/StudySpot/Views/Auth/AuthViews.swift). */
@Composable
fun PrivacySettingsScreen(modifier: Modifier = Modifier) {
    SettingsColumn(modifier) {
        PolicySection(
            "What we collect",
            listOf(
                "Account information" to "your name, username, and password, used to create and sign in to your account.",
                "Assignment and class data" to "the assignments, classes, scores, notes, and study sessions you create or import from connected services.",
                "Connected service data" to "when you link Google Calendar, Google Classroom, or Blackboard, we retrieve course work, due dates, and attachments you have access to, on your behalf.",
                "Device information" to "if you allow notifications, a push token for this device, used only to deliver your reminders and class countdowns. No advertising, analytics or tracking.",
            ),
        )
        PolicySection(
            "How we use it",
            listOf(
                "To display and organize your assignments, classes, and progress.",
                "To send you notifications about upcoming due dates based on your reminder settings.",
                "To sync completion status back to Google Classroom when you enable that option.",
                "To provide analytics, streaks, and grading features.",
                "To maintain, secure, and improve the app.",
            ).map { null to it },
        )
        PolicySection(
            "Your choices",
            listOf(
                "Connect or disconnect Google and Blackboard integrations anytime in Settings.",
                "Customize or turn off reminders and set a do-not-disturb window.",
                "Enable or disable two-factor authentication.",
                "Share a read-only weekly summary with a parent or guardian from Settings → Parent view. Anyone with the link can see it until you turn it off.",
                "Export your data from Settings, or permanently delete your account and everything in it from Settings → Danger Zone.",
            ).map { null to it },
        )
    }
}

@Composable
private fun PolicySection(title: String, items: List<Pair<String?, String>>) {
    SettingsSection(header = title) {
        items.forEachIndexed { i, (bold, text) ->
            if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
            Text(
                buildAnnotatedString {
                    if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$bold: ") }
                    append(text)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
