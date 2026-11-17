package education.studyspot.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.AppConfig
import education.studyspot.app.core.userMessage
import education.studyspot.app.ui.components.SubScreenScaffold
import kotlinx.coroutines.launch

/**
 * Hidden server settings (long-press the logo on the welcome screen), like iOS
 * ServerSettingsView: server address, optional website address, test, reset.
 * Changing the server signs out.
 */
@Composable
fun ServerSettingsDialog(onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(c.config.serverUrl) }
    var web by remember { mutableStateOf(c.config.webUrlOverride.orEmpty()) }
    var status by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SubScreenScaffold(
            title = "Server",
            onBack = onDismiss,
            modifier = Modifier.fillMaxSize(),
            actions = {
                TextButton(onClick = {
                    val changed = AppConfig.normalize(url) != c.config.serverUrl
                    c.config.serverUrl = url
                    c.config.webUrlOverride = web
                    if (changed && c.repository.user.value != null) c.auth.signOut()
                    onDismiss()
                }) { Text("Save") }
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server address") },
                    placeholder = { Text("https://studyspot.example.com") },
                    supportingText = { Text("The Study Spot server. The app talks to its /api endpoints.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = web,
                    onValueChange = { web = it },
                    label = { Text("Website address") },
                    placeholder = { Text("Same as server address") },
                    supportingText = { Text("Where you log in. Leave empty if the website is served from the server address.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !checking,
                        onClick = {
                            scope.launch {
                                checking = true
                                status = try {
                                    if (c.api.checkHealth(url)) "Connected" else "The server responded, but /api/health failed."
                                } catch (e: Exception) {
                                    e.userMessage
                                }
                                checking = false
                            }
                        },
                    ) { Text("Test connection") }
                    if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { url = c.config.defaultServerUrl; web = "" }) { Text("Reset to defaults") }
            }
        }
    }
}
