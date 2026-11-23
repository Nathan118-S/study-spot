package education.studyspot.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.ui.theme.isDark

/**
 * Settings > Appearance (iOS AppearanceSettings): dark mode, sync with device,
 * animations (stored on-device by data.ThemeStore), colour theme (also synced
 * in user.data.theme) and app icon (ThemePickers.kt).
 *
 * TODO(settings): welcome greeting style picker (user.data.welcome_style: large / small / off).
 */
@Composable
fun AppearanceSettingsScreen(modifier: Modifier = Modifier) {
    val theme = LocalAppContainer.current.theme
    val settings by theme.settings.collectAsStateWithLifecycle()
    val dark = settings.isDark()
    val rowColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)

    Column(modifier.verticalScroll(rememberScrollState())) {
        ListItem(
            headlineContent = { Text("Dark mode") },
            supportingContent = { Text("Turning this on or off stops following the device.") },
            trailingContent = { Switch(checked = dark, onCheckedChange = theme::setDarkMode) },
            colors = rowColors,
        )
        ListItem(
            headlineContent = { Text("Sync with device") },
            supportingContent = { Text("Match the system light/dark setting.") },
            trailingContent = { Switch(checked = settings.syncWithDevice, onCheckedChange = theme::setSyncWithDevice) },
            colors = rowColors,
        )
        ListItem(
            headlineContent = { Text("Animations") },
            supportingContent = { Text("Motion and transitions throughout the app.") },
            trailingContent = { Switch(checked = settings.animations, onCheckedChange = theme::setAnimations) },
            colors = rowColors,
        )
        SettingsSection(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            header = "Theme",
            footer = { SettingsFootnote("Pick your colors. Your theme syncs to Study Spot on the web and your other devices.") },
        ) { ColorThemePicker() }
        SettingsSection(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            header = "App icon",
            footer = { SettingsFootnote("Choose the icon on your home screen. Some launchers take a moment to update it.") },
        ) { AppIconPicker() }
    }
}
