package education.studyspot.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.model.AdminUser
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.settings.SettingsFootnote
import education.studyspot.app.ui.settings.SettingsSection
import education.studyspot.app.ui.settings.settingsRowColors
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch

/** Admin > People (AdminPeopleView): search, then open an account (after the 2FA unlock). */
@Composable
fun AdminPeopleScreen(nav: AdminNav, people: AdminPeopleModel) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val filtered = people.users.filter {
        search.isBlank() || it.username.contains(search.trim(), ignoreCase = true) || it.displayName.contains(search.trim(), ignoreCase = true)
    }

    // Also runs when coming back from an account, to pick up changes.
    LaunchedEffect(Unit) { people.load() }

    fun open(u: AdminUser) {
        scope.launch {
            try {
                AdminUnlock.ensureUnlocked(c.api)
                nav.push(AdminDest.Account(AdminAccountModel(u.id, c.api, c.messages)))
            } catch (_: AdminUnlock.Cancelled) {
                // Unlock cancelled: stay on the list.
            }
        }
    }

    SubScreenScaffold(
        title = "People",
        onBack = nav::pop,
        actions = {
            IconButton(onClick = { scope.launch { people.load() } }) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh") }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; people.load(); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SettingsSection(footer = { SettingsFootnote("Opening anyone's account needs two-factor authentication. It stays unlocked for 15 minutes.") }) {
                        AdminUnlockStatus()
                    }
                }
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search by name or username") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    SettingsSection {
                        when {
                            people.loading && people.users.isEmpty() -> LoadingBox()
                            filtered.isEmpty() -> Text(
                                "No users found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        filtered.forEachIndexed { i, u ->
                            if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
                            AdminPersonRow(u) { open(u) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPersonRow(user: AdminUser, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        headlineContent = { Text(user.displayName) },
        supportingContent = { Text(user.username) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (user.role != "user") {
                    val color = when (user.role) {
                        "admin" -> MaterialTheme.colorScheme.error
                        "disabled" -> muted
                        else -> StudySpotTheme.colors.success
                    }
                    Pill(user.role, color = color)
                }
                if (user.twofaEnabled) Icon(Icons.Outlined.Lock, contentDescription = "2FA on", tint = muted, modifier = Modifier.size(16.dp))
                Icon(
                    if (user.onboardingCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (user.onboardingCompleted) "Onboarding complete" else "Onboarding pending",
                    tint = if (user.onboardingCompleted) StudySpotTheme.colors.success else muted,
                    modifier = Modifier.size(16.dp),
                )
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = muted)
            }
        },
        colors = settingsRowColors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
