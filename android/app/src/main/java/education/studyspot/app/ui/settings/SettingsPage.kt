package education.studyspot.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Settings sub-pages (ios/StudySpot/Views/Settings/SettingsView.swift). Each has
 * its own file `<Name>SettingsScreen.kt` in this package; route: settings/<key>.
 * [listed] pages appear in the main list; the rest are linked separately.
 */
enum class SettingsPage(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val listed: Boolean = true,
) {
    Appearance("appearance", "Appearance", "Theme, app icon, light/dark mode", Icons.Outlined.Palette),
    Google("google", "Google", "Calendar and Classroom connections", Icons.Outlined.Power),
    Blackboard("blackboard", "Blackboard", "Connect your school’s Blackboard", Icons.Outlined.AccountBalance),
    Lms("lms", "Canvas, Schoology & Brightspace", "Add your school's calendar by link", Icons.Outlined.EditCalendar),
    Sync("sync", "Sync", "Refresh synced data", Icons.Outlined.Sync),
    Leaderboard("leaderboard", "Leaderboard", "Show study-streak rankings", Icons.Outlined.EmojiEvents),
    Onboarding("onboarding", "Onboarding", "Run the setup wizard again", Icons.Outlined.RestartAlt),
    Data("data", "Data", "Export your assignments", Icons.Outlined.Storage),
    Reminders("reminders", "Reminders", "Assignment notifications and class updates", Icons.Outlined.Alarm),
    Grading("grading", "Grading", "Letter-grade thresholds", Icons.Outlined.School),
    Security("security", "Security", "Two-factor authentication", Icons.Outlined.Lock),
    Account("account", "Account", "Your name, username, and password", Icons.Outlined.Person),
    Danger("danger", "Danger Zone", "Log out all devices, delete account", Icons.Outlined.Warning, danger = true),
    Server("server", "Server", "The server this app talks to", Icons.Outlined.Dns, listed = false),
    Privacy("privacy", "Privacy Policy", "What we collect and why", Icons.Outlined.PrivacyTip, listed = false);

    companion object {
        fun fromKey(key: String?): SettingsPage = entries.firstOrNull { it.key == key } ?: Appearance
    }
}
