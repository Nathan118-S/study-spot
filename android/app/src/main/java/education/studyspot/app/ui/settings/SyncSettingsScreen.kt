package education.studyspot.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Settings > Sync (SyncSettings in ios/StudySpot/Views/Settings/SettingsView.swift):
 * last sync time, Sync now, refresh connection status.
 */
@Composable
fun SyncSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val connections by c.repository.connections.collectAsStateWithLifecycle()
    val syncing by c.repository.isSyncing.collectAsStateWithLifecycle()
    val message by c.repository.syncMessage.collectAsStateWithLifecycle()
    val last = user?.lastSync

    SettingsColumn(modifier) {
        SettingsSection(
            footer = when {
                message.isNotEmpty() -> ({ SettingsFootnote(message) })
                !connections.any -> ({ SettingsFootnote("Connect Google or Blackboard to sync assignments automatically.") })
                else -> null
            },
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Auto-refresh", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (last != null) {
                        "Last synced " + last.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
                    } else {
                        "Not synced yet. The app auto-syncs every 6 hours."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsActions {
                BusyButton(
                    "Refresh status",
                    onClick = { scope.launch { c.repository.refreshConnections() } },
                    prominent = false,
                    icon = Icons.Outlined.Refresh,
                )
                BusyButton(
                    "Sync now",
                    // App scope: the sync keeps going if the user leaves this page.
                    onClick = { c.scope.launch { c.repository.runSync(silent = false) } },
                    busy = syncing,
                    enabled = connections.any && user?.isDemo != true,
                    icon = Icons.Outlined.Sync,
                )
            }
        }
    }
}
