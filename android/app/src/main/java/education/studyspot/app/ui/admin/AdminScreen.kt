package education.studyspot.app.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.settings.SettingsColumn
import education.studyspot.app.ui.settings.SettingsFootnote
import education.studyspot.app.ui.settings.SettingsSection
import education.studyspot.app.ui.settings.settingsRowColors
import education.studyspot.app.ui.theme.StudySpotTheme

/** Admin screens; the admin area keeps its own back stack inside the single admin route. */
sealed interface AdminDest {
    val title: String

    data object Home : AdminDest { override val title = "Admin" }
    data object People : AdminDest { override val title = "People" }
    data class Account(val model: AdminAccountModel) : AdminDest { override val title = "Account" }
    data class Records(val model: AdminAccountModel, val kind: AdminRecordKind) : AdminDest { override val title = kind.title }
    data class RecordEditor(val model: AdminAccountModel, val kind: AdminRecordKind, val record: AdminRecord, val list: AdminRecordsState) : AdminDest {
        override val title = record.title(kind)
    }
    data class Simulate(val model: AdminAccountModel) : AdminDest { override val title = "Simulate on phone" }
    data class Merge(val model: AdminAccountModel) : AdminDest { override val title = "Merge account" }
    data class Grading(val model: AdminAccountModel) : AdminDest { override val title = "Grading scale" }
    data object Notify : AdminDest { override val title = "Send Notification" }
    data object Secrets : AdminDest { override val title = "Integration Secrets" }
    data object Deploy : AdminDest { override val title = "Deploy" }
}

/** Pushes and pops admin screens. */
class AdminNav(private val stack: MutableList<AdminDest>, private val exit: () -> Unit) {
    fun push(dest: AdminDest) { stack.add(dest) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else exit() }
    /** Pops back to (and excluding) the account screen's caller, e.g. after deleting the account. */
    fun popTo(predicate: (AdminDest) -> Boolean) {
        while (stack.size > 1 && !predicate(stack.last())) stack.removeAt(stack.lastIndex)
    }
}

/**
 * Admin (ios/StudySpot/Views/Admin): People (full account manager behind the
 * 2FA unlock), Send Notification, Integration Secrets and Deploy. Admins only.
 */
@Composable
fun AdminScreen(navigator: AppNavigator) {
    val c = LocalAppContainer.current
    val user by c.repository.user.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf<AdminDest>(AdminDest.Home) }
    val nav = remember { AdminNav(stack, navigator::back) }
    val people = remember { AdminPeopleModel(c.api, c.messages) }
    val current = stack.last()

    BackHandler(enabled = stack.size > 1) { nav.pop() }

    if (user?.isAdmin != true) {
        SubScreenScaffold(title = "Admin", onBack = navigator::back) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Outlined.Lock, title = "Admins only")
            }
        }
        return
    }

    when (current) {
        AdminDest.Home -> AdminHome(nav)
        AdminDest.People -> AdminPeopleScreen(nav, people)
        is AdminDest.Account -> AdminAccountScreen(nav, current.model, people)
        is AdminDest.Records -> AdminRecordListScreen(nav, current.model, current.kind)
        is AdminDest.RecordEditor -> AdminRecordEditorScreen(nav, current.model, current.kind, current.record, current.list)
        is AdminDest.Simulate -> AdminSimulateScreen(nav, current.model)
        is AdminDest.Merge -> AdminMergeScreen(nav, current.model, people)
        is AdminDest.Grading -> AdminGradingScreen(nav, current.model)
        AdminDest.Notify -> AdminNotificationScreen(nav, people)
        AdminDest.Secrets -> AdminSecretsScreen(nav)
        AdminDest.Deploy -> AdminDeployScreen(nav)
    }
    AdminUnlockHost()
}

@Composable
private fun AdminHome(nav: AdminNav) {
    SubScreenScaffold(title = "Admin", onBack = nav::pop) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection(footer = { SettingsFootnote("Manage users and send notifications.") }) {
                AdminHomeRow("People", "Everything about each account: profile, security, settings, and data. Needs 2FA.", Icons.Outlined.Group) {
                    nav.push(AdminDest.People)
                }
                HorizontalDivider(color = StudySpotTheme.colors.border)
                AdminHomeRow("Send Notification", "Send a custom in-app notification to a user or everyone.", Icons.Outlined.NotificationAdd) {
                    nav.push(AdminDest.Notify)
                }
                HorizontalDivider(color = StudySpotTheme.colors.border)
                AdminHomeRow("Integration Secrets", "Paste Google, Blackboard, push credentials — write-once, encrypted.", Icons.Outlined.Key) {
                    nav.push(AdminDest.Secrets)
                }
                HorizontalDivider(color = StudySpotTheme.colors.border)
                AdminHomeRow("Deploy", "Pull the latest commit from GitHub and restart the app.", Icons.Outlined.CloudUpload) {
                    nav.push(AdminDest.Deploy)
                }
            }
        }
    }
}

@Composable
private fun AdminHomeRow(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    val tint = StudySpotTheme.colors.secondary
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        },
        colors = settingsRowColors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
