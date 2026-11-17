package education.studyspot.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.ui.components.StubContent

/**
 * Settings > Blackboard (iOS: BlackboardSettings).
 *
 * TODO(settings): STUB. Instance URL (user.blackboardInstanceUrl), functions/blackboardAuthUrl { client: AppConfig.CONNECTOR_CLIENT_ID } -> open in Custom Tab; the app receives studyspot://blackboard?code=… (handled in navigation.DeepLinkHandler); functions/disconnectBlackboard.
 * The top bar is provided by SettingsPageScreen; apply [modifier] to the root.
 */
@Composable
fun BlackboardSettingsScreen(modifier: Modifier = Modifier) {
    StubContent("Blackboard", "Coming soon.", modifier)
}
