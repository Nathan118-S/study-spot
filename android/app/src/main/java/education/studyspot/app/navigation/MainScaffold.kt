package education.studyspot.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.ui.calendar.CalendarScreen
import education.studyspot.app.ui.classes.ClassesScreen
import education.studyspot.app.ui.components.LocalSnackbarHostState
import education.studyspot.app.ui.dashboard.DashboardScreen
import education.studyspot.app.ui.insights.InsightsScreen
import education.studyspot.app.ui.streaks.StreaksScreen
import education.studyspot.app.ui.theme.StudySpotTheme

/**
 * The tabbed shell (iOS MainTabView + AppToolbarModifier): a top app bar with
 * the notifications bell and account menu, and bottom navigation for
 * Dashboard, Classes, Calendar, Streaks and Insights.
 *
 * Tab screens receive the scaffold's content padding and must apply it.
 */
@Composable
fun MainScaffold(navigator: AppNavigator) {
    val c = LocalAppContainer.current
    val tab by c.router.selectedTab.collectAsStateWithLifecycle()
    val colors = StudySpotTheme.colors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                actions = {
                    if (tab == AppTab.Dashboard) {
                        IconButton(onClick = navigator::openTimer) {
                            Icon(Icons.Outlined.Timer, contentDescription = "Study timer")
                        }
                    }
                    NotificationsBell(onClick = navigator::openNotifications)
                    AccountMenu(navigator)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = colors.secondary,
                    actionIconContentColor = colors.secondary,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                AppTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { c.router.selectTab(t) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.accent,
                            selectedTextColor = colors.accent,
                            indicatorColor = colors.accent.copy(alpha = 0.14f),
                            unselectedIconColor = colors.secondary.copy(alpha = 0.6f),
                            unselectedTextColor = colors.secondary.copy(alpha = 0.6f),
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
    ) { padding ->
        when (tab) {
            AppTab.Dashboard -> DashboardScreen(navigator, padding)
            AppTab.Classes -> ClassesScreen(navigator, padding)
            AppTab.Calendar -> CalendarScreen(navigator, padding)
            AppTab.Streaks -> StreaksScreen(navigator, padding)
            AppTab.Insights -> InsightsScreen(navigator, padding)
        }
    }
}

@Composable
private fun NotificationsBell(onClick: () -> Unit) {
    val unread by LocalAppContainer.current.repository.unreadCount.collectAsStateWithLifecycle()
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) {
            Icon(Icons.Outlined.Notifications, contentDescription = if (unread > 0) "Notifications, $unread unread" else "Notifications")
        }
    }
}

@Composable
private fun AccountMenu(navigator: AppNavigator) {
    val c = LocalAppContainer.current
    val user by c.repository.user.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.AccountCircle, contentDescription = "Account")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        user?.let { u ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(u.displayName, style = MaterialTheme.typography.titleSmall)
                Text(u.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (u.isDemo) {
                DropdownMenuItem(
                    text = { Text("Demo account") },
                    leadingIcon = { Icon(Icons.Outlined.Visibility, null) },
                    onClick = {},
                    enabled = false,
                )
            }
            HorizontalDivider()
        }
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { Icon(Icons.Outlined.Settings, null) },
            onClick = { open = false; navigator.openSettings() },
        )
        if (user?.isAdmin == true) {
            DropdownMenuItem(
                text = { Text("Admin") },
                leadingIcon = { Icon(Icons.Outlined.AdminPanelSettings, null) },
                onClick = { open = false; navigator.openAdmin() },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { open = false; c.auth.signOut() },
        )
    }
}
