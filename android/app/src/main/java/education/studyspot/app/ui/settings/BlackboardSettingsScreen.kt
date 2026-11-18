package education.studyspot.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.WebSignIn
import education.studyspot.app.core.AppConfig
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.IcalKind
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Settings > Blackboard (BlackboardSettings in ios/StudySpot/Views/Settings/SettingsView.swift).
 * functions/blackboardAuthUrl { instanceUrl, client } opens in a Custom Tab; the
 * website hands the code back as studyspot://blackboard?code=…, which
 * navigation.DeepLinkHandler exchanges with functions/blackboardCallback.
 * Below it, Blackboard calendar links for when the school hasn't approved
 * Study Spot ([IcalLinksSection] with [IcalKind.Blackboard]).
 */
@Composable
fun BlackboardSettingsScreen(modifier: Modifier = Modifier, embedded: Boolean = false) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connections by c.repository.connections.collectAsStateWithLifecycle()
    val user by c.repository.user.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val isDemo = user?.isDemo == true

    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            launch { c.repository.refreshIcalFeeds() }
            c.repository.refreshConnections()
            c.auth.refreshUser() // picks up blackboard_instance_url after connecting
            busy = false
        }
        onPauseOrDispose { job.cancel() }
    }

    fun connect() {
        error = ""
        val trimmed = url.trim().trimEnd('/')
        if (trimmed.isEmpty()) { error = "Enter your Blackboard Learn URL."; return }
        if (isDemo) return
        scope.launch {
            busy = true
            try {
                val res = c.api.invoke("blackboardAuthUrl", buildJsonObject {
                    put("instanceUrl", trimmed)
                    put("client", AppConfig.CONNECTOR_CLIENT_ID)
                })
                val authUrl = res["authUrl"].stringValue
                if (authUrl == null) {
                    error = "Failed to start connection"
                    busy = false
                } else if (!WebSignIn.openInCustomTab(context, authUrl.toUri())) {
                    error = "No browser is available."
                    busy = false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
                busy = false
            }
        }
    }

    fun disconnect() {
        scope.launch {
            busy = true
            try {
                c.api.invoke("disconnectBlackboard")
                c.repository.refreshConnections()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
            }
            busy = false
        }
    }

    SettingsColumn(modifier, embedded = embedded) {
        SettingsSection {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = StudySpotTheme.colors.secondary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Title and status pill wrap rather than clip the pill on narrow screens.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Blackboard Learn", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (connections.blackboard) Pill("Connected", color = StudySpotTheme.colors.success, filled = true) else Pill("Not connected")
                    }
                    Text(
                        "Import courses and due-dated gradebook items from your school's Blackboard instance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (connections.blackboard) {
            SettingsSection(footer = if (error.isEmpty()) null else ({ SettingsFootnote(error, error = true) })) {
                Text(
                    user?.blackboardInstanceUrl ?: "Connected",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                SettingsActions {
                    BusyButton("Disconnect", onClick = { confirmDisconnect = true }, busy = busy, destructive = true, enabled = !isDemo)
                }
            }
        } else {
            SettingsSection(
                footer = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (error.isNotEmpty()) SettingsFootnote(error, error = true)
                        SchoolAccessNote(SchoolITEmail.Service.Blackboard)
                    }
                },
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = "" },
                    label = { Text("Blackboard Learn URL") },
                    placeholder = { Text("https://your-school.blackboard.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { connect() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SettingsActions {
                    BusyButton("Connect", onClick = ::connect, busy = busy, enabled = !isDemo, icon = Icons.Outlined.Link)
                }
            }
        }
        IcalLinksSection(isDemo = isDemo, kind = IcalKind.Blackboard)
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Disconnect Blackboard?",
            message = "Assignments already imported stay in Study Spot. You can connect again anytime.",
            confirmLabel = "Disconnect",
            onConfirm = ::disconnect,
            onDismiss = { confirmDisconnect = false },
        )
    }
}
