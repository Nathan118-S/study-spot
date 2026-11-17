package education.studyspot.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.ui.components.StubContent

/**
 * Settings > Reminders (iOS: ReminderSettings).
 *
 * TODO(settings): STUB. reminders_enabled, reminder_lead_hours, push_reminders, dnd_enabled/start/end, class live updates; request POST_NOTIFICATIONS on API 33+; push.PushManager.configure after changes.
 * The top bar is provided by SettingsPageScreen; apply [modifier] to the root.
 */
@Composable
fun RemindersSettingsScreen(modifier: Modifier = Modifier) {
    StubContent("Reminders", "Coming soon.", modifier)
}
