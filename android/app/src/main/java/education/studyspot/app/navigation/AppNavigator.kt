package education.studyspot.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import education.studyspot.app.ui.settings.SettingsPage

/**
 * What screens call to move around. Passed to every screen (and available as
 * `LocalAppNavigator.current`), so screens never touch the NavController.
 */
class AppNavigator(private val nav: NavHostController, private val router: Router) {
    fun back() {
        if (!nav.popBackStack()) router.showTab(router.selectedTab.value)
    }

    fun selectTab(tab: AppTab) = router.showTab(tab)

    fun openAssignment(id: String) = nav.navigate(Routes.assignmentDetail(id)) { launchSingleTop = true }
    fun newAssignment() = nav.navigate(Routes.assignmentForm()) { launchSingleTop = true }
    fun editAssignment(id: String) = nav.navigate(Routes.assignmentForm(id)) { launchSingleTop = true }

    fun openSettings() = nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
    fun openSettings(page: SettingsPage) = nav.navigate(Routes.settingsPage(page)) { launchSingleTop = true }
    fun openAdmin() = nav.navigate(Routes.ADMIN) { launchSingleTop = true }
    fun openNotifications() = nav.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
    fun openTimer() = nav.navigate(Routes.TIMER) { launchSingleTop = true }
    fun openTemplates() = nav.navigate(Routes.TEMPLATES) { launchSingleTop = true }

    /** A website path from a notification's action_url. */
    fun openWebPath(path: String) = router.openWebPath(path)

    /** Any route from [Routes]. */
    fun navigate(route: String) = nav.navigate(route) { launchSingleTop = true }
}

val LocalAppNavigator = staticCompositionLocalOf<AppNavigator> { error("LocalAppNavigator not provided") }
