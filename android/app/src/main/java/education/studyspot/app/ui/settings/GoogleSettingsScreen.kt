package education.studyspot.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.ui.components.StubContent

/**
 * Settings > Google (iOS: GoogleSettings in SettingsView.swift).
 *
 * TODO(settings): STUB. Connect/disconnect Google Calendar and Classroom: api.googleConnectorUrl("google_calendar"|"google_classroom") opened with auth.WebSignIn.openInCustomTab; api.disconnectGoogle(connector); state from repository.connections (refreshConnections on resume). (ApiClient uses AppConfig.CONNECTOR_CLIENT_ID; the server returns to studyspot://connected, handled by DeepLinkHandler).
 * The top bar is provided by SettingsPageScreen; apply [modifier] to the root.
 */
@Composable
fun GoogleSettingsScreen(modifier: Modifier = Modifier) {
    StubContent("Google", "Coming soon.", modifier)
}
