package education.studyspot.app.ui.admin

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.model.TwoFactorMethod
import education.studyspot.app.model.User
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.settings.BusyButton
import education.studyspot.app.ui.settings.ConfirmDialog
import education.studyspot.app.ui.settings.HourMinute
import education.studyspot.app.ui.settings.ReminderLeads
import education.studyspot.app.ui.settings.SettingsActions
import education.studyspot.app.ui.settings.SettingsColumn
import education.studyspot.app.ui.settings.SettingsFootnote
import education.studyspot.app.ui.settings.SettingsSection
import education.studyspot.app.ui.settings.SettingsText
import education.studyspot.app.ui.settings.SettingsToggle
import education.studyspot.app.ui.settings.SettingsValueRow
import education.studyspot.app.ui.settings.TimePickerAlert
import education.studyspot.app.ui.settings.icon
import education.studyspot.app.ui.settings.settingsRowColors
import education.studyspot.app.ui.components.clean
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Admin > People > account: the full account manager
 * (ios/StudySpot/Views/Admin/AdminAccountViews.swift and AdminRecordViews.swift).
 */
@Composable
fun AdminAccountScreen(nav: AdminNav, model: AdminAccountModel, people: AdminPeopleModel) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val me by c.repository.user.collectAsStateWithLifecycle()
    val user = model.user

    LaunchedEffect(model) { if (model.user == null) model.load() }
    LaunchedEffect(model.gone) {
        if (model.gone) {
            nav.popTo { it == AdminDest.People }
            people.load()
        }
    }

    SubScreenScaffold(
        title = user?.displayName ?: "Account",
        onBack = nav::pop,
        actions = {
            IconButton(onClick = { scope.launch { model.load() } }, enabled = !model.loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reload")
            }
        },
    ) { padding ->
        when {
            user != null -> AdminAccountForm(nav, model, user, isSelf = user.id == me?.id, modifier = Modifier.padding(padding))
            model.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingBox() }
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.PersonOff,
                    title = "Couldn't open account",
                    message = model.loadError.orEmpty(),
                    action = { BusyButton("Try again", onClick = { scope.launch { model.load() } }) },
                )
            }
        }
    }
}

@Composable
private fun AdminAccountForm(nav: AdminNav, model: AdminAccountModel, user: User, isSelf: Boolean, modifier: Modifier) {
    SettingsColumn(modifier) {
        SettingsSection { AdminUnlockStatus() }
        ProfileSection(model, user)
        StatusSection(model, user, isSelf)
        TwoFactorSection(model, user)
        PasswordSection(model, user)
        UserSettingsSection(nav, model, user)
        ReminderSettingsSection(model, user)
        ConnectionsSection(model)
        DevicesSection(model)
        SettingsSection {
            NavRow("Simulate on phone", "Send test notifications and class countdowns to their phones.", Icons.Outlined.PhoneAndroid) {
                nav.push(AdminDest.Simulate(model))
            }
        }
        DataSection(nav, model)
        ActionsSection(nav, model, user, isSelf)
    }
}

@Composable
private fun NavRow(title: String, description: String?, icon: ImageVector?, trailing: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null, tint = StudySpotTheme.colors.secondary) } },
        trailingContent = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = settingsRowColors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ActionRow(
    title: String,
    description: String?,
    icon: ImageVector,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else StudySpotTheme.colors.secondary
    ListItem(
        headlineContent = { Text(title, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = if (enabled) color else color.copy(alpha = 0.4f)) },
        colors = settingsRowColors(),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

private val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)

// region Profile & status

@Composable
private fun ProfileSection(model: AdminAccountModel, user: User) {
    val scope = rememberCoroutineScope()
    var username by remember(user) { mutableStateOf(user.username) }
    var name by remember(user) { mutableStateOf(user.name.orEmpty()) }
    var fullName by remember(user) { mutableStateOf(user.fullName.orEmpty()) }
    val patch = buildJsonObject {
        val u = username.trim().lowercase()
        if (u.isNotEmpty() && u != user.username.lowercase()) put("username", u)
        if (name.trim() != user.name.orEmpty()) put("name", name)
        if (fullName.trim() != user.fullName.orEmpty()) put("full_name", fullName)
    }

    SettingsSection(header = "Profile", footer = { SelectionContainer { SettingsFootnote("ID ${user.id}") } }) {
        OutlinedTextField(
            value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = fieldModifier,
        )
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }, placeholder = { Text("None") }, singleLine = true, modifier = fieldModifier)
        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") }, placeholder = { Text("None") }, singleLine = true, modifier = fieldModifier)
        SettingsActions {
            BusyButton(
                "Save profile",
                busy = model.busy,
                enabled = patch.isNotEmpty(),
                onClick = { scope.launch { model.perform("adminUpdateUser", patch, success = "Profile saved") } },
            )
        }
    }
}

@Composable
private fun StatusSection(model: AdminAccountModel, user: User, isSelf: Boolean) {
    val scope = rememberCoroutineScope()
    var showDisable by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    SettingsSection(header = "Status", footer = if (isSelf) ({ SettingsFootnote("You can't change your own role.") }) else null) {
        ChoiceField(
            label = "Role",
            options = listOf("user" to "User", "admin" to "Admin", "demo" to "Demo", "disabled" to "Disabled"),
            selected = user.role,
            enabled = !isSelf && !model.busy,
            onSelect = { role ->
                if (role == user.role) return@ChoiceField
                if (role == "disabled") {
                    reason = ""
                    showDisable = true
                } else {
                    scope.launch {
                        model.perform(
                            "adminUpdateUserRole", buildJsonObject { put("role", role) },
                            success = "Role updated", detail = "${user.username} is now $role.",
                        )
                    }
                }
            },
        )
        if (user.isDisabled) {
            SettingsValueRow("Reason", user.disabledReason?.takeIf { it.isNotBlank() } ?: "None given", trailing = {
                TextButton(onClick = { reason = user.disabledReason.orEmpty(); showDisable = true }, enabled = !isSelf) { Text("Change") }
            })
        }
        SettingsValueRow("Verified", if (user.isVerified) "Yes" else "No", trailing = if (user.isVerified) null else ({
            TextButton(onClick = { scope.launch { model.perform("adminVerifyUser", success = "Marked verified") } }) { Text("Mark verified") }
        }))
    }

    if (showDisable) {
        ConfirmDialog(
            title = "Disable ${user.username}?",
            message = "They're signed out and see the reason when they try to log in.",
            confirmLabel = "Disable",
            onConfirm = {
                val why = reason
                scope.launch {
                    model.perform(
                        "adminUpdateUserRole", buildJsonObject { put("role", "disabled"); put("reason", why) },
                        success = "Account disabled", detail = "${user.username} can no longer log in.",
                    )
                }
            },
            onDismiss = { showDisable = false },
        ) {
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// endregion

// region Security

@Composable
private fun TwoFactorSection(model: AdminAccountModel, user: User) {
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<String?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }

    SettingsSection(header = "Two-factor authentication") {
        if (model.methods.isEmpty()) SettingsText("Two-factor authentication is off.")
        model.methods.forEach { m ->
            val method = TwoFactorMethod.from(m)
            ListItem(
                headlineContent = { Text(method?.label ?: m) },
                leadingContent = { Icon(method?.icon ?: Icons.Outlined.Key, contentDescription = null, tint = StudySpotTheme.colors.secondary) },
                trailingContent = { TextButton(onClick = { removing = m }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
                colors = settingsRowColors(),
            )
        }
        SettingsActions {
            BusyButton("Reset all 2FA", onClick = { confirmResetAll = true }, destructive = true, enabled = model.methods.isNotEmpty() && !model.busy)
        }
    }

    removing?.let { m ->
        val label = TwoFactorMethod.from(m)?.label ?: m
        ConfirmDialog(
            title = "Remove this method?",
            message = "Removes $label from ${user.username}'s second factors.",
            confirmLabel = "Remove $label",
            onConfirm = { scope.launch { model.perform("adminRemove2faMethod", buildJsonObject { put("method", m) }, success = "2FA method removed") } },
            onDismiss = { removing = null },
        )
    }
    if (confirmResetAll) {
        ConfirmDialog(
            title = "Reset all 2FA for ${user.username}?",
            message = "Removes every second factor so they can sign in with just their password.",
            confirmLabel = "Reset 2FA",
            onConfirm = {
                scope.launch { model.perform("adminReset2fa", success = "2FA reset", detail = "${user.username} can sign in without a second factor.") }
            },
            onDismiss = { confirmResetAll = false },
        )
    }
}

@Composable
private fun PasswordSection(model: AdminAccountModel, user: User) {
    val c = LocalAppContainer.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var confirmTemp by remember { mutableStateOf(false) }
    var tempPassword by remember { mutableStateOf<String?>(null) }
    var showSet by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }

    SettingsSection(header = "Password & sessions") {
        SettingsValueRow("Password", if (model.hasPassword) "Set" else "Not set")
        HorizontalDivider(color = StudySpotTheme.colors.border)
        ActionRow("Reset to a temporary password", "Generates a password to give them, and signs them out everywhere.", Icons.Outlined.Key, enabled = !model.busy) {
            confirmTemp = true
        }
        ActionRow("Set a password", "Choose their new password yourself.", Icons.Outlined.Edit, enabled = !model.busy) {
            newPassword = ""
            showSet = true
        }
        ActionRow("Sign out everywhere", "Ends every session on every device.", Icons.AutoMirrored.Outlined.Logout, enabled = !model.busy) {
            confirmSignOut = true
        }
    }

    if (confirmTemp) {
        ConfirmDialog(
            title = "Reset ${user.username}'s password?",
            message = "A temporary password replaces their current one, and they're signed out on every device.",
            confirmLabel = "Reset password",
            onConfirm = {
                scope.launch {
                    val res = model.perform("adminResetPassword", success = "Password reset", detail = "${user.username} was signed out everywhere.")
                    tempPassword = res["password"].stringValue
                }
            },
            onDismiss = { confirmTemp = false },
        )
    }
    tempPassword?.let { password ->
        AlertDialog(
            onDismissRequest = { tempPassword = null },
            title = { Text("Temporary password") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("Give this to ${user.username} so they can sign in, then have them change it in Settings → Account.")
                    SelectionContainer {
                        Text(password, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Temporary password", password)))
                        c.messages.show("Password copied")
                    }
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { tempPassword = null }) { Text("Done", color = StudySpotTheme.colors.secondary) } },
        )
    }
    if (showSet) {
        fun set(signOut: Boolean) {
            val password = newPassword
            newPassword = ""
            showSet = false
            scope.launch {
                model.perform(
                    "adminSetPassword", buildJsonObject { put("password", password); put("signOut", signOut) },
                    success = "Password set", detail = if (signOut) "${user.username} was signed out everywhere." else null,
                )
            }
        }
        AlertDialog(
            onDismissRequest = { showSet = false },
            title = { Text("Set password") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("Choose a new password for ${user.username}.")
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New password (8+ characters)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    TextButton(onClick = { set(signOut = false) }, enabled = newPassword.length >= 8, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Set, keep sessions")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { set(signOut = true) }, enabled = newPassword.length >= 8) { Text("Set and sign them out") }
            },
            dismissButton = { TextButton(onClick = { showSet = false }) { Text("Cancel", color = StudySpotTheme.colors.secondary) } },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign ${user.username} out everywhere?",
            message = "Ends every session on every device.",
            confirmLabel = "Sign out everywhere",
            onConfirm = { scope.launch { model.perform("adminSignOutUser", success = "Signed out everywhere", reload = false) } },
            onDismiss = { confirmSignOut = false },
        )
    }
}

// endregion

// region Settings

@Composable
private fun UserSettingsSection(nav: AdminNav, model: AdminAccountModel, user: User) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var timezone by remember(user) { mutableStateOf(user.timezone.orEmpty()) }

    fun save(settings: JsonObject) { scope.launch { model.updateSettings(settings) } }
    fun flag(key: String, default: Boolean) = user.data[key].boolValue ?: default

    @Composable
    fun toggle(key: String, title: String, default: Boolean) = SettingsToggle(
        title = title,
        checked = flag(key, default),
        enabled = !model.busy,
        onCheckedChange = { v -> save(buildJsonObject { put(key, v) }) },
    )

    SettingsSection(header = "Settings") {
        toggle("onboarding_completed", "Onboarding completed", false)
        ChoiceField(
            label = "Welcome message",
            options = listOf("large" to "Large", "small" to "Small", "off" to "Off"),
            selected = user.welcomeStyle,
            enabled = !model.busy,
            onSelect = { v -> save(buildJsonObject { put("welcome_style", v) }) },
        )
        toggle("leaderboard_enabled", "Leaderboard", false)
        toggle("sync_completion_to_classroom", "Sync completion to Classroom", true)
        toggle("class_live_activities", "Class countdowns (Live Activities)", true)
        OutlinedTextField(
            value = timezone,
            onValueChange = { timezone = it },
            label = { Text("Time zone") },
            placeholder = { Text("America/New_York") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            trailingIcon = if (timezone.trim() != user.timezone.orEmpty()) ({
                TextButton(onClick = {
                    val tz = timezone.trim()
                    if (tz.isEmpty()) return@TextButton
                    if (runCatching { ZoneId.of(tz) }.isFailure) {
                        c.messages.show("Unknown time zone", "Use an identifier like America/New_York.", error = true)
                    } else {
                        save(buildJsonObject { put("timezone", tz) })
                    }
                }) { Text("Save") }
            }) else null,
            modifier = fieldModifier,
        )
        NavRow("Grading scale", null, null, trailing = user.gradingScale.joinToString(" ") { it.letter }) {
            nav.push(AdminDest.Grading(model))
        }
    }
}

@Composable
private fun ReminderSettingsSection(model: AdminAccountModel, user: User) {
    val scope = rememberCoroutineScope()
    val savedLeads = ReminderLeads.from(user).joinToString(", ") { it.clean }
    var leadText by remember(user) { mutableStateOf(savedLeads) }
    var picking by remember { mutableStateOf<String?>(null) }
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val dndStart = HourMinute.parse(user.data["dnd_start"].stringValue, LocalTime.of(22, 0))
    val dndEnd = HourMinute.parse(user.data["dnd_end"].stringValue, LocalTime.of(7, 0))

    fun save(settings: JsonObject) { scope.launch { model.updateSettings(settings) } }

    @Composable
    fun toggle(key: String, title: String, default: Boolean) = SettingsToggle(
        title = title,
        checked = user.data[key].boolValue ?: default,
        enabled = !model.busy,
        onCheckedChange = { v -> save(buildJsonObject { put(key, v) }) },
    )

    fun saveLeads() {
        val hours = leadText.split(',', ' ').mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0 }.distinct().sorted()
        save(buildJsonObject { put("reminder_lead_hours", buildJsonArray { hours.forEach { add(JsonPrimitive(it)) } }) })
    }

    SettingsSection(header = "Reminders", footer = { SettingsFootnote("Changes are saved right away.") }) {
        toggle("reminders_enabled", "Assignment reminders", true)
        toggle("push_reminders", "Phone notifications", true)
        OutlinedTextField(
            value = leadText,
            onValueChange = { leadText = it },
            label = { Text("Hours before") },
            placeholder = { Text("24, 6") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            trailingIcon = if (leadText != savedLeads) ({ TextButton(onClick = ::saveLeads) { Text("Save") } }) else null,
            modifier = fieldModifier,
        )
        toggle("dnd_enabled", "Do not disturb", false)
        SettingsValueRow("Quiet from", dndStart.format(timeFormat), modifier = Modifier.clickable { picking = "dnd_start" })
        SettingsValueRow("Quiet until", dndEnd.format(timeFormat), modifier = Modifier.clickable { picking = "dnd_end" })
    }

    picking?.let { key ->
        TimePickerAlert(
            title = if (key == "dnd_start") "Quiet from" else "Quiet until",
            initial = if (key == "dnd_start") dndStart else dndEnd,
            onConfirm = { t -> save(buildJsonObject { put(key, HourMinute.format(t)) }) },
            onDismiss = { picking = null },
        )
    }
}

// endregion

// region Connections, devices, data

private val connectorNames = mapOf(
    "google_calendar" to "Google Calendar",
    "google_classroom" to "Google Classroom",
    "blackboard" to "Blackboard",
)

@Composable
private fun ConnectionsSection(model: AdminAccountModel) {
    val scope = rememberCoroutineScope()
    var disconnecting by remember { mutableStateOf<String?>(null) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    @Composable
    fun row(connector: String, detail: String?) {
        ListItem(
            headlineContent = { Text(connectorNames[connector] ?: connector) },
            supportingContent = {
                Text(detail ?: "Not connected", color = if (detail == null) MaterialTheme.colorScheme.onSurfaceVariant else StudySpotTheme.colors.success)
            },
            trailingContent = if (detail != null) ({
                TextButton(onClick = { disconnecting = connector }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            }) else null,
            colors = settingsRowColors(),
        )
    }

    SettingsSection(header = "Connections") {
        listOf("google_calendar", "google_classroom").forEach { connector ->
            val g = model.google.firstOrNull { it.connector == connector }
            row(connector, g?.let { conn -> conn.createdAt?.let { "Connected " + it.atZone(ZoneId.systemDefault()).format(dateFormat) } ?: "Connected" })
        }
        val bbConnected = model.blackboard["connected"].boolValue == true
        row("blackboard", if (bbConnected) model.blackboard["domain"].stringValue ?: "Connected" else null)
    }

    disconnecting?.let { connector ->
        val name = connectorNames[connector] ?: connector
        ConfirmDialog(
            title = "Disconnect $name?",
            message = "Their synced data stays. They can connect again from Settings.",
            confirmLabel = "Disconnect",
            onConfirm = {
                scope.launch { model.perform("adminDisconnectUser", buildJsonObject { put("connector", connector) }, success = "$name disconnected") }
            },
            onDismiss = { disconnecting = null },
        )
    }
}

@Composable
private fun DevicesSection(model: AdminAccountModel) {
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<AdminDevice?>(null) }

    SettingsSection(header = "Devices") {
        if (model.devices.isEmpty()) SettingsText("No devices registered for push.")
        model.devices.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
            ListItem(
                headlineContent = { Text(d.kindLabel) },
                supportingContent = {
                    androidx.compose.foundation.layout.Column {
                        Text(listOf(d.token, d.environment).filter { it.isNotEmpty() }.joinToString(" · "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        d.updatedAt?.let {
                            Text(
                                listOf(d.bundleId, "updated " + AdminParse.shortDateTime(it)).filter { s -> s.isNotEmpty() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                leadingContent = {
                    Icon(if (d.kind == "fcm") Icons.Outlined.PhoneAndroid else Icons.Outlined.Notifications, contentDescription = null, tint = StudySpotTheme.colors.secondary)
                },
                trailingContent = {
                    IconButton(onClick = { removing = d }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove device", tint = MaterialTheme.colorScheme.error) }
                },
                colors = settingsRowColors(),
            )
        }
    }

    removing?.let { d ->
        ConfirmDialog(
            title = "Remove this device?",
            message = "Stops sending pushes to ${d.kindLabel} ${d.token}. It registers again the next time the app opens signed in.",
            confirmLabel = "Remove",
            onConfirm = { scope.launch { model.perform("adminRemoveDevice", buildJsonObject { put("deviceId", d.id) }, success = "Device removed") } },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun DataSection(nav: AdminNav, model: AdminAccountModel) {
    SettingsSection(header = "Data") {
        AdminRecordKind.entries.forEachIndexed { i, kind ->
            if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
            NavRow(kind.title, kind.summary(model::stat), kind.icon) { nav.push(AdminDest.Records(model, kind)) }
        }
    }
}

// endregion

// region Actions

@Composable
private fun ActionsSection(nav: AdminNav, model: AdminAccountModel, user: User, isSelf: Boolean) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    SettingsSection(header = "Actions") {
        ActionRow("Send test notification", "To their notification center and their phones.", Icons.Outlined.Notifications, enabled = !model.busy) {
            scope.launch {
                model.perform("adminSendTestNotification", success = null, reload = false)?.let {
                    c.messages.show("Test notification sent", AdminAccountModel.describeTest(it))
                }
            }
        }
        ActionRow("Restore streak", "Mark overdue assignments complete to rebuild their streak.", Icons.Outlined.LocalFireDepartment, enabled = !model.busy) {
            confirmRestore = true
        }
        ActionRow("Reset onboarding", "Run the setup wizard again on their next visit.", Icons.Outlined.RestartAlt, enabled = user.onboardingCompleted && !model.busy) {
            scope.launch { model.perform("adminResetOnboarding", success = "Onboarding reset") }
        }
        if (!isSelf) {
            ActionRow("Merge into another account", "Move their assignments and classes, then delete this account.", Icons.AutoMirrored.Outlined.MergeType, enabled = !model.busy) {
                nav.push(AdminDest.Merge(model))
            }
            ActionRow("Delete account", "Permanently deletes the account and all of its data.", Icons.Outlined.Delete, enabled = !model.busy, destructive = true) {
                confirmDelete = true
            }
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore ${user.username}'s streak?",
            message = "Marks their overdue assignments complete.",
            confirmLabel = "Restore streak",
            destructive = false,
            onConfirm = {
                scope.launch {
                    model.perform("adminRestoreStreak", success = null)?.let { res ->
                        c.messages.show("Streak restored", "${(res["restored"].doubleValue ?: 0.0).toInt()} overdue assignment(s) marked complete.")
                    }
                }
            },
            onDismiss = { confirmRestore = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${user.username}?",
            message = "This permanently deletes the account and all of its data. This can't be undone.",
            confirmLabel = "Delete account",
            onConfirm = {
                scope.launch {
                    if (model.perform("adminDeleteUser", success = "User deleted", detail = user.username, reload = false) != null) model.gone = true
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// endregion
