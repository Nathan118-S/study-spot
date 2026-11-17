package education.studyspot.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.ui.auth.ServerSettingsDialog

/** Settings > Server (ServerInfoView in ios/StudySpot/Views/Settings/SettingsView.swift). */
@Composable
fun ServerSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val server by c.config.serverUrlFlow.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    SettingsColumn(modifier) {
        SettingsSection(footer = { SettingsFootnote("Changing the server signs you out.") }) {
            SettingsValueRow("Server", server)
            SettingsValueRow("Website", c.config.webUrl)
            SettingsValueRow("Passkey domain", c.config.passkeyRelyingParty)
            SettingsActions {
                BusyButton("Change server…", onClick = { editing = true }, prominent = false, icon = Icons.Outlined.Dns)
            }
        }
    }
    if (editing) ServerSettingsDialog(onDismiss = { editing = false })
}
