package education.studyspot.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.ui.components.StubContent

/**
 * Settings > Account (iOS: AccountSettings / UsernameSettings / ChangePasswordSettings).
 *
 * TODO(settings): STUB. Name, username (PATCH /auth/me { username }), change password (api.changePassword).
 * The top bar is provided by SettingsPageScreen; apply [modifier] to the root.
 */
@Composable
fun AccountSettingsScreen(modifier: Modifier = Modifier) {
    StubContent("Account", "Coming soon.", modifier)
}
