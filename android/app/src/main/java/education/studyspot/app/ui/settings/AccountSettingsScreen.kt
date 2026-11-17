package education.studyspot.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Settings > Account (AccountSettings / UsernameSettings / ChangePasswordSettings in
 * ios/StudySpot/Views/Settings/SettingsView.swift): name, username, password.
 * "Log out of all devices" lives in the Danger Zone, as on iOS.
 */
@Composable
fun AccountSettingsScreen(modifier: Modifier = Modifier) {
    SettingsColumn(modifier) {
        NameSection()
        UsernameSection()
        PasswordSection()
    }
}

private val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)

@Composable
private fun NameSection() {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val current = (user?.name ?: user?.fullName).orEmpty()
    var name by remember { mutableStateOf(current) }
    var saving by remember { mutableStateOf(false) }

    SettingsSection(header = "Name", footer = { SettingsFootnote("Used to personalize the app and its notifications.") }) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = fieldModifier.semantics { contentType = ContentType.PersonFullName },
        )
        SettingsActions {
            BusyButton(
                "Save",
                busy = saving,
                enabled = name.trim() != current.trim() && user?.isDemo != true,
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            c.repository.updateUser(buildJsonObject { put("name", name.trim()) })
                            c.messages.show("Name saved")
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            c.messages.show("Failed to save", e.userMessage, error = true)
                        }
                        saving = false
                    }
                },
            )
        }
    }
}

@Composable
private fun UsernameSection() {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val current = user?.username.orEmpty()
    var username by remember { mutableStateOf(current) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val trimmed = username.trim().lowercase()

    LaunchedEffect(current) { if (username.isEmpty()) username = current }

    SettingsSection(
        header = "Username",
        footer = {
            if (error.isNotEmpty()) {
                SettingsFootnote(error, error = true)
            } else {
                SettingsFootnote(
                    "You sign in with your username. 3–30 characters: lowercase letters, numbers, periods, underscores, " +
                        "and hyphens, starting with a letter or number.",
                )
            }
        },
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { error = ""; username = it.take(30) },
            label = { Text("Username") },
            singleLine = true,
            isError = error.isNotEmpty(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            modifier = fieldModifier.semantics { contentType = ContentType.Username },
        )
        SettingsActions {
            BusyButton(
                "Save username",
                busy = saving,
                enabled = trimmed.isNotEmpty() && trimmed != current && user?.isDemo != true,
                onClick = {
                    scope.launch {
                        error = ""
                        saving = true
                        try {
                            val updated = c.repository.updateUser(buildJsonObject { put("username", trimmed) })
                            username = updated.username
                            c.messages.show("Username saved")
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = e.userMessage // e.g. "That username is taken"
                        }
                        saving = false
                    }
                },
            )
        }
    }
}

@Composable
private fun PasswordSection() {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && newPassword != confirm

    SettingsSection(
        header = "Password",
        footer = when {
            error.isNotEmpty() -> ({ SettingsFootnote(error, error = true) })
            mismatch -> ({ SettingsFootnote("The new passwords don't match.", error = true) })
            else -> null
        },
    ) {
        PasswordField("Current password", current, { current = it; error = "" }, ContentType.Password)
        PasswordField("New password", newPassword, { newPassword = it; error = "" }, ContentType.NewPassword)
        PasswordField("Confirm new password", confirm, { confirm = it }, ContentType.NewPassword, isError = mismatch)
        SettingsActions {
            BusyButton(
                "Change password",
                busy = saving,
                enabled = current.isNotEmpty() && newPassword.isNotEmpty() && newPassword == confirm && user?.isDemo != true,
                onClick = {
                    scope.launch {
                        error = ""
                        saving = true
                        try {
                            c.api.changePassword(current, newPassword)
                            current = ""; newPassword = ""; confirm = ""
                            c.messages.show("Password changed")
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = e.userMessage
                        }
                        saving = false
                    }
                },
            )
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit, type: ContentType, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = fieldModifier.semantics { contentType = type },
    )
}
