package education.studyspot.app.ui.settings

import education.studyspot.app.core.userMessage
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.Dates
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Settings > Danger Zone (DangerZone in ios/StudySpot/Views/Settings/SettingsView.swift):
 * log out of all devices, and permanently delete the account (DELETE /api/auth/me,
 * confirmed with the password; the server deletes everything it owns).
 */
@Composable
fun DangerSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val user by c.repository.user.collectAsStateWithLifecycle()
    var confirmLogoutAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val isDemo = user?.isDemo == true

    SettingsColumn(modifier) {
        SettingsSection {
            DangerRow(
                title = "Log out of all devices",
                description = "Signs out this device and any other devices currently logged into your account.",
                icon = Icons.AutoMirrored.Outlined.Logout,
                destructive = false,
                enabled = !busy,
                onClick = { confirmLogoutAll = true },
            )
        }
        SettingsSection {
            DangerRow(
                title = "Delete account",
                description = "Permanently deletes your account and everything in it. This can't be undone.",
                icon = Icons.Outlined.Delete,
                destructive = true,
                enabled = !busy && !isDemo,
                busy = busy,
                onClick = { confirmDelete = true },
            )
        }
    }

    if (confirmLogoutAll) {
        ConfirmDialog(
            title = "Log out of all devices?",
            message = "You'll be signed out here, and any other device signed into your account will be signed out the next time it opens the app.",
            confirmLabel = "Log out all",
            onConfirm = {
                // App scope: the request must finish even though this screen goes away.
                c.scope.launch {
                    busy = true
                    if (!isDemo) {
                        runCatching { c.repository.updateUser(buildJsonObject { put("sessions_invalidated_at", Dates.format(Instant.now())) }) }
                    }
                    c.auth.signOut()
                }
            },
            onDismiss = { confirmLogoutAll = false },
        )
    }
    if (confirmDelete) {
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Delete account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This permanently deletes your account, assignments, classes, study history, connections and settings. This can't be undone.")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.isNotEmpty() && !busy && !isDemo,
                    onClick = {
                        // App scope: finish even if this screen goes away.
                        c.scope.launch {
                            busy = true
                            try {
                                c.api.send("/auth/me", method = "DELETE", body = buildJsonObject { put("password", password) })
                                confirmDelete = false
                                c.auth.signOut()
                            } catch (e: Exception) {
                                error = e.userMessage
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DangerRow(
    title: String,
    description: String,
    icon: ImageVector,
    destructive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else StudySpotTheme.colors.secondary
    ListItem(
        headlineContent = { Text(title, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        trailingContent = if (busy) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
        colors = settingsRowColors(),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}
