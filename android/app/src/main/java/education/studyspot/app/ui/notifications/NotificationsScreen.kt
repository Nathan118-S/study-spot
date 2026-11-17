package education.studyspot.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.model.AppNotification
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.components.ago
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch

/**
 * Notifications (ios/StudySpot/Views/Notifications/NotificationsView.swift):
 * the in-app notifications with unread dots; tap to mark read and follow its
 * action_url, swipe left to delete, and "Mark all read" / "Clear all".
 */
@Composable
fun NotificationsScreen(navigator: AppNavigator) {
    val repo = LocalAppContainer.current.repository
    val notifications by repo.notifications.collectAsStateWithLifecycle()
    val unread by repo.unreadCount.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    var loading by remember { mutableStateOf(notifications.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.loadNotifications()
        loading = false
    }

    fun open(n: AppNotification) {
        scope.launch { repo.markRead(n) }
        n.actionUrl?.takeIf { it.isNotBlank() }?.let(navigator::openWebPath)
    }

    SubScreenScaffold(
        title = "Notifications",
        onBack = navigator::back,
        actions = {
            IconButton(onClick = { scope.launch { repo.markAllRead() } }, enabled = unread > 0) {
                Icon(Icons.Outlined.DoneAll, contentDescription = "Mark all read")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Mark all read") },
                        leadingIcon = { Icon(Icons.Outlined.DoneAll, null) },
                        enabled = unread > 0,
                        onClick = { menu = false; scope.launch { repo.markAllRead() } },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear all", color = if (notifications.isEmpty()) colors.mutedText else colors.danger) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null, tint = if (notifications.isEmpty()) colors.mutedText else colors.danger) },
                        enabled = notifications.isNotEmpty(),
                        onClick = { menu = false; confirmClear = true },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; repo.loadNotifications(); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    loading -> item { LoadingBox() }
                    notifications.isEmpty() -> item {
                        EmptyState(Icons.Outlined.NotificationsOff, "No notifications yet", message = "Reminders, streak updates and news show up here.")
                    }
                    else -> items(notifications, key = { it.id }) { n ->
                        NotificationRow(
                            n,
                            onOpen = { open(n) },
                            onDelete = { scope.launch { repo.deleteNotification(n) } },
                            modifier = if (StudySpotTheme.animations) Modifier.animateItem() else Modifier,
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all notifications?") },
            text = { Text("This removes every notification. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; scope.launch { repo.clearNotifications() } }) { Text("Clear all", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = colors.secondary) } },
        )
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StudySpotTheme.colors
    val state = rememberSwipeToDismissBoxState()
    val (icon, tint) = style(n.type)
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(colors.danger).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) { Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White) }
        },
    ) {
        Card(
            onClick = onOpen,
            colors = CardDefaults.cardColors(
                containerColor = if (n.read) colors.surface else colors.accent.copy(alpha = 0.08f).compositeOver(colors.surface),
            ),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (n.read) "Read" else "Unread" },
        ) {
            Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(n.title.ifBlank { "Notification" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (n.content.isNotBlank()) {
                        Text(n.content, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
                    }
                    val meta = listOfNotNull(DateText.ago(n.createdDate).ifBlank { null }, n.actionLabel?.takeIf { it.isNotBlank() })
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = colors.mutedText.copy(alpha = 0.8f))
                    }
                }
                if (!n.read) {
                    Box(Modifier.padding(top = 6.dp, start = 6.dp).size(8.dp).clip(CircleShape).background(colors.accent))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete notification", tint = colors.mutedText, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun style(type: String): Pair<ImageVector, Color> {
    val colors = StudySpotTheme.colors
    return when (type) {
        "streak" -> Icons.Filled.LocalFireDepartment to colors.accent
        "test" -> Icons.Filled.Science to colors.secondary
        "reminder" -> Icons.Filled.Notifications to colors.accent
        else -> Icons.Filled.Info to colors.secondary
    }
}
