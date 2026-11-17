package education.studyspot.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.WebSignIn
import education.studyspot.app.core.userMessage
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Settings > Google (GoogleSettings in ios/StudySpot/Views/Settings/SettingsView.swift):
 * connect/disconnect Google Calendar and Classroom, and "mark Classroom work done".
 * Connecting opens the server's Google flow in a Custom Tab; it returns to
 * studyspot://connected (navigation.DeepLinkHandler), and the status is also
 * refreshed whenever this screen resumes.
 */
@Composable
fun GoogleSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connections by c.repository.connections.collectAsStateWithLifecycle()
    val user by c.repository.user.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    val isDemo = user?.isDemo == true

    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            c.repository.refreshConnections()
            loading = false
            busy = null
        }
        onPauseOrDispose { job.cancel() }
    }

    fun connect(connector: String) {
        if (isDemo) return
        scope.launch {
            busy = connector
            try {
                val url = c.api.googleConnectorUrl(connector)
                if (!WebSignIn.openInCustomTab(context, url.toUri())) {
                    c.messages.show("Couldn't connect", "No browser is available.", error = true)
                    busy = null
                }
                // busy clears when the screen resumes after the Custom Tab.
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                c.messages.show("Couldn't connect", e.userMessage, error = true)
                busy = null
            }
        }
    }

    fun disconnect(connector: String) {
        if (isDemo) return
        scope.launch {
            busy = connector
            try {
                c.api.disconnectGoogle(connector)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                c.messages.show("Couldn't disconnect", e.userMessage, error = true)
            }
            c.repository.refreshConnections()
            busy = null
        }
    }

    SettingsColumn(modifier) {
        SettingsSection(footer = { SchoolAccessNote(SchoolITEmail.Service.Google) }) {
            SettingsText("Connect your own Google account to import assignments. Each user connects separately — your data stays private to you.")
        }
        SettingsSection {
            ConnectionRow(
                title = "Google Calendar",
                description = "Import calendar events labeled as assignments.",
                icon = Icons.Outlined.CalendarMonth,
                connected = connections.calendar,
                loading = loading,
                busy = busy == "google_calendar",
                enabled = !isDemo,
                onConnect = { connect("google_calendar") },
                onDisconnect = { disconnect("google_calendar") },
            )
        }
        SettingsSection {
            ConnectionRow(
                title = "Google Classroom",
                description = "Import courses and coursework with due dates.",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                connected = connections.classroom,
                loading = loading,
                busy = busy == "google_classroom",
                enabled = !isDemo,
                onConnect = { connect("google_classroom") },
                onDisconnect = { disconnect("google_classroom") },
            )
            if (connections.classroom && !loading) {
                HorizontalDivider(color = StudySpotTheme.colors.border)
                SettingsToggle(
                    title = "Mark Classroom assignments done",
                    description = "When you complete a Google Classroom assignment here, turn it in on Classroom so your teacher sees it as done.",
                    checked = user?.syncCompletionToClassroom ?: true,
                    enabled = !isDemo,
                    onCheckedChange = { v ->
                        scope.launch {
                            try {
                                c.repository.updateUser(buildJsonObject { put("sync_completion_to_classroom", v) })
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                c.messages.show("Couldn't save", e.userMessage, error = true)
                            }
                        }
                    },
                )
            }
        }
    }
}

/** A service with its status pill and a Connect / Disconnect button. */
@Composable
fun ConnectionRow(
    title: String,
    description: String,
    icon: ImageVector,
    connected: Boolean,
    loading: Boolean,
    busy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    enabled: Boolean = true,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = StudySpotTheme.colors.secondary, modifier = Modifier.padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        connected -> Pill("Connected", color = StudySpotTheme.colors.success, filled = true)
                        else -> Pill("Not connected")
                    }
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingsActions(Modifier.padding(0.dp)) {
            if (connected) {
                BusyButton("Disconnect", onClick = onDisconnect, busy = busy, enabled = enabled && !loading, prominent = false)
            } else {
                BusyButton("Connect", onClick = onConnect, busy = busy, enabled = enabled && !loading, icon = Icons.Outlined.Link)
            }
        }
    }
}
