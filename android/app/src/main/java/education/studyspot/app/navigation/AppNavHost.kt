package education.studyspot.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.push.NotificationPermissionPrompt
import education.studyspot.app.push.PushManager
import education.studyspot.app.ui.admin.AdminScreen
import education.studyspot.app.ui.auth.Verify2FAScreen
import education.studyspot.app.ui.dashboard.AssignmentDetailScreen
import education.studyspot.app.ui.dashboard.AssignmentFormScreen
import education.studyspot.app.ui.notifications.NotificationsScreen
import education.studyspot.app.ui.onboarding.OnboardingScreen
import education.studyspot.app.ui.settings.SettingsPage
import education.studyspot.app.ui.settings.SettingsPageScreen
import education.studyspot.app.ui.settings.SettingsScreen
import education.studyspot.app.ui.templates.TemplatesScreen
import education.studyspot.app.ui.timer.StudyTimerScreen
import education.studyspot.app.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** The signed-in app: the tab shell plus pushed screens. Also runs the post-sign-in loads. */
@Composable
fun AppNavHost() {
    val c = LocalAppContainer.current
    val nav = rememberNavController()
    val navigator = remember(nav) { AppNavigator(nav, c.router) }
    val user by c.repository.user.collectAsStateWithLifecycle()
    val pending by c.router.pendingRoute.collectAsStateWithLifecycle()
    val popToMain by c.router.popToMain.collectAsStateWithLifecycle()

    // First load after sign-in (iOS: MainTabView.task).
    LaunchedEffect(user?.id) {
        if (user == null) return@LaunchedEffect
        c.repository.load()
        launch { WidgetUpdater.refresh(c.context) }
        launch { PushManager.configure(c.context, c.api, user) }
        c.repository.autoSyncIfStale()
    }
    LifecycleResumeEffect(Unit) {
        val job = c.scope.launch { c.repository.loadNotifications() }
        onPauseOrDispose { job.cancel() }
    }
    LaunchedEffect(pending) {
        c.router.consumePendingRoute()?.let { route -> nav.navigate(route) { launchSingleTop = true } }
    }
    LaunchedEffect(popToMain) {
        if (popToMain > 0) nav.popBackStack(Routes.MAIN, inclusive = false)
    }

    // Asks for notification permission once, with a rationale (push/NotificationPermission.kt).
    NotificationPermissionPrompt()

    CompositionLocalProvider(LocalAppNavigator provides navigator) {
        NavHost(navController = nav, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) { MainScaffold(navigator) }
            composable(
                Routes.ASSIGNMENT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                AssignmentDetailScreen(assignmentId = entry.arguments?.getString("id").orEmpty(), navigator = navigator)
            }
            composable(
                Routes.ASSIGNMENT_FORM,
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                AssignmentFormScreen(assignmentId = entry.arguments?.getString("id"), navigator = navigator)
            }
            composable(Routes.SETTINGS) { SettingsScreen(navigator) }
            composable(
                Routes.SETTINGS_PAGE,
                arguments = listOf(navArgument("page") { type = NavType.StringType }),
            ) { entry ->
                SettingsPageScreen(page = SettingsPage.fromKey(entry.arguments?.getString("page")), navigator = navigator)
            }
            composable(Routes.ADMIN) { AdminScreen(navigator) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(navigator) }
            composable(Routes.TIMER) { StudyTimerScreen(navigator) }
            composable(Routes.TEMPLATES) { TemplatesScreen(navigator) }
            composable(Routes.ONBOARDING) { OnboardingScreen() }
            composable(Routes.VERIFY_2FA) { Verify2FAScreen() }
        }
    }
}
