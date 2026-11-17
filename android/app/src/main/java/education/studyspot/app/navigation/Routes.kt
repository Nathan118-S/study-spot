package education.studyspot.app.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.ui.graphics.vector.ImageVector
import education.studyspot.app.ui.settings.SettingsPage

/** Bottom-navigation tabs. [key] is also the studyspot://tab/<key> deep-link name. */
enum class AppTab(val key: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Outlined.Dashboard),
    Classes("classes", "Classes", Icons.AutoMirrored.Outlined.MenuBook),
    Calendar("calendar", "Calendar", Icons.Outlined.CalendarMonth),
    Streaks("streaks", "Streaks", Icons.Outlined.LocalFireDepartment),
    Insights("insights", "Insights", Icons.AutoMirrored.Outlined.ShowChart);

    companion object {
        fun fromKey(key: String?): AppTab = entries.firstOrNull { it.key == key?.lowercase() } ?: Dashboard
    }
}

/** Navigation Compose routes (see AppNavHost). Build them with the helpers, not by hand. */
object Routes {
    /** The tabbed shell (MainScaffold). */
    const val MAIN = "main"

    const val ASSIGNMENT_DETAIL = "assignment/{id}"
    fun assignmentDetail(id: String) = "assignment/${Uri.encode(id)}"

    /** Create (no id) or edit an assignment. */
    const val ASSIGNMENT_FORM = "assignmentForm?id={id}"
    fun assignmentForm(id: String? = null) = if (id == null) "assignmentForm" else "assignmentForm?id=${Uri.encode(id)}"

    const val SETTINGS = "settings"
    const val SETTINGS_PAGE = "settings/{page}"
    fun settingsPage(page: SettingsPage) = "settings/${page.key}"

    const val ADMIN = "admin"
    const val NOTIFICATIONS = "notifications"
    const val TIMER = "timer"
    const val TEMPLATES = "templates"

    /** Gated by AppRoot (shown automatically when the user needs them); routes exist for explicit use. */
    const val ONBOARDING = "onboarding"
    const val VERIFY_2FA = "verify2fa"
}
