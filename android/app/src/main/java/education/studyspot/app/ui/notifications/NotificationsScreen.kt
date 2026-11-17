package education.studyspot.app.ui.notifications

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent
import education.studyspot.app.ui.components.SubScreenScaffold

/**
 * Notifications (ios/StudySpot/Views/Notifications/NotificationsView.swift).
 *
 * TODO(notifications): STUB. List repository.notifications (unread first styling), tap -> repository.markRead + navigator.openWebPath(actionUrl), "Mark all read" (repository.markAllRead), "Clear" (repository.clearNotifications), swipe to delete (repository.deleteNotification).
 */
@Composable
fun NotificationsScreen(navigator: AppNavigator) {
    SubScreenScaffold(title = "Notifications", onBack = navigator::back) { padding ->
        StubContent("Notifications", "Coming soon.", Modifier.padding(padding))
    }
}
