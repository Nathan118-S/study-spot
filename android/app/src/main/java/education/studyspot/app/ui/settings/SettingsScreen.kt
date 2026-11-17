package education.studyspot.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.theme.StudySpotTheme
import androidx.compose.foundation.clickable

/** The settings list; each row opens a [SettingsPage]. */
@Composable
fun SettingsScreen(navigator: AppNavigator) {
    val c = LocalAppContainer.current
    val user by c.repository.user.collectAsStateWithLifecycle()
    SubScreenScaffold(title = "Settings", onBack = navigator::back) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.padding(padding)) {
            items(SettingsPage.entries.filter { it.listed }) { page -> SettingsRow(page) { navigator.openSettings(page) } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            items(SettingsPage.entries.filter { !it.listed }) { page -> SettingsRow(page) { navigator.openSettings(page) } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                ListItem(
                    headlineContent = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Signed in as ${user?.username.orEmpty()}") },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { c.auth.signOut() },
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(page: SettingsPage, onClick: () -> Unit) {
    val tint = if (page.danger) MaterialTheme.colorScheme.error else StudySpotTheme.colors.secondary
    ListItem(
        headlineContent = { Text(page.title, color = if (page.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = { Text(page.description) },
        leadingContent = {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(page.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Hosts one settings sub-page with the standard chrome. Sub-page bodies live in their own files. */
@Composable
fun SettingsPageScreen(page: SettingsPage, navigator: AppNavigator) {
    SubScreenScaffold(title = page.title, onBack = navigator::back) { padding ->
        val modifier = Modifier.padding(padding)
        when (page) {
            SettingsPage.Appearance -> AppearanceSettingsScreen(modifier)
            SettingsPage.Google -> GoogleSettingsScreen(modifier)
            SettingsPage.Blackboard -> BlackboardSettingsScreen(modifier)
            SettingsPage.Sync -> SyncSettingsScreen(modifier)
            SettingsPage.Leaderboard -> LeaderboardSettingsScreen(modifier)
            SettingsPage.Onboarding -> OnboardingSettingsScreen(modifier)
            SettingsPage.Data -> DataSettingsScreen(modifier)
            SettingsPage.Reminders -> RemindersSettingsScreen(modifier)
            SettingsPage.Grading -> GradingSettingsScreen(modifier)
            SettingsPage.Security -> SecuritySettingsScreen(modifier, navigator)
            SettingsPage.Account -> AccountSettingsScreen(modifier)
            SettingsPage.Danger -> DangerSettingsScreen(modifier)
            SettingsPage.Server -> ServerSettingsScreen(modifier)
            SettingsPage.Privacy -> PrivacySettingsScreen(modifier)
        }
    }
}
