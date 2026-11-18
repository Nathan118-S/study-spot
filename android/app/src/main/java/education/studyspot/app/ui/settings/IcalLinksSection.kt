package education.studyspot.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.IcalFeed
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch

/**
 * Settings > Google (and onboarding): "Can't connect your Google account?",
 * the button that opens [IcalGuideDialog], and the added calendar links with
 * Sync now / Rename / Import every event / Remove.
 */
@Composable
fun IcalLinksSection(isDemo: Boolean) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val feeds by c.repository.icalFeeds.collectAsStateWithLifecycle()
    var showGuide by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<IcalFeed?>(null) }
    var removing by remember { mutableStateOf<IcalFeed?>(null) }

    fun failed(title: String, e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        c.messages.show(title, e.userMessage, error = true)
    }

    fun syncAll() {
        scope.launch {
            syncing = true
            try {
                val r = c.repository.syncIcalFeeds()
                if (r.errors.isEmpty()) {
                    c.messages.show("Calendars synced", if (r.imported > 0) "${r.imported} new" else "No new assignments")
                } else {
                    c.messages.show("Couldn't sync ${r.errors.joinToString { it.label }}", r.errors.first().error, error = true)
                }
            } catch (e: Exception) {
                failed("Couldn't sync", e)
            } finally {
                syncing = false
            }
        }
    }

    SettingsSection(header = "Can't connect your Google account?") {
        SettingsText("Paste your calendar's public link instead and Study Spot will import your assignments from it.")
        SettingsActions {
            BusyButton(
                "Add a calendar link",
                onClick = { showGuide = true },
                enabled = !isDemo,
                prominent = false,
                icon = Icons.Outlined.Add,
            )
        }
        feeds.forEach { feed ->
            HorizontalDivider(color = StudySpotTheme.colors.border)
            IcalFeedRow(
                feed = feed,
                syncing = syncing,
                enabled = !isDemo,
                onSync = ::syncAll,
                onRename = { renaming = feed },
                onImportAllChange = { all ->
                    scope.launch {
                        try {
                            c.repository.updateIcalFeed(feed.id, mode = if (all) IcalFeed.MODE_ALL else IcalFeed.MODE_AUTO)
                        } catch (e: Exception) {
                            failed("Couldn't save", e)
                        }
                    }
                },
                onRemove = { removing = feed },
            )
        }
    }

    if (showGuide) IcalGuideDialog(onDismiss = { showGuide = false })

    renaming?.let { feed ->
        var name by remember(feed.id) { mutableStateOf(feed.displayName) }
        ConfirmDialog(
            title = "Rename calendar",
            message = null,
            confirmLabel = "Save",
            destructive = false,
            onConfirm = {
                scope.launch {
                    try {
                        c.repository.updateIcalFeed(feed.id, label = name)
                    } catch (e: Exception) {
                        failed("Couldn't rename", e)
                    }
                }
            },
            onDismiss = { renaming = null },
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    removing?.let { feed ->
        var deleteAssignments by remember(feed.id) { mutableStateOf(false) }
        ConfirmDialog(
            title = "Remove ${feed.displayName}?",
            message = "Study Spot will stop checking this calendar.",
            confirmLabel = "Remove",
            onConfirm = {
                scope.launch {
                    try {
                        c.repository.removeIcalFeed(feed.id, deleteAssignments)
                    } catch (e: Exception) {
                        failed("Couldn't remove", e)
                    }
                }
            },
            onDismiss = { removing = null },
        ) {
            Column(Modifier.selectableGroup()) {
                RemoveOption("Keep imported assignments", selected = !deleteAssignments) { deleteAssignments = false }
                RemoveOption("Also delete its assignments", selected = deleteAssignments) { deleteAssignments = true }
            }
        }
    }
}

@Composable
private fun RemoveOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun IcalFeedRow(
    feed: IcalFeed,
    syncing: Boolean,
    enabled: Boolean,
    onSync: () -> Unit,
    onRename: () -> Unit,
    onImportAllChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val navy = StudySpotTheme.colors.secondary
    val status = IcalLinks.status(feed)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Event, contentDescription = null, tint = navy, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(feed.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    status.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.error) StudySpotTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Import every event", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = feed.importsAll, onCheckedChange = onImportAllChange, enabled = enabled)
        }
        FlowRow(Modifier.padding(start = 28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val pad = PaddingValues(horizontal = 8.dp)
            TextButton(onClick = onSync, enabled = enabled && !syncing, contentPadding = pad) {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sync now", color = navy)
                }
            }
            TextButton(onClick = onRename, enabled = enabled, contentPadding = pad) { Text("Rename", color = navy) }
            TextButton(onClick = onRemove, enabled = enabled, contentPadding = pad) {
                Text("Remove", color = StudySpotTheme.colors.danger)
            }
        }
    }
}
