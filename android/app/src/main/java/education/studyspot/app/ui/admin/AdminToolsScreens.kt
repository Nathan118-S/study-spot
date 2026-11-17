package education.studyspot.app.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.AppJson
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.DeployStatus
import education.studyspot.app.model.SecretStatus
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.settings.BusyButton
import education.studyspot.app.ui.settings.ConfirmDialog
import education.studyspot.app.ui.settings.SettingsActions
import education.studyspot.app.ui.settings.SettingsColumn
import education.studyspot.app.ui.settings.SettingsFootnote
import education.studyspot.app.ui.settings.SettingsSection
import education.studyspot.app.ui.settings.SettingsText
import education.studyspot.app.ui.settings.SettingsValueRow
import education.studyspot.app.ui.settings.settingsRowColors
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// region Send notification

/** Admin > Send Notification (NotificationSenderView): an in-app notification to one user or everyone. */
@Composable
fun AdminNotificationScreen(nav: AdminNav, people: AdminPeopleModel) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var recipient by remember { mutableStateOf("all") }
    var type by remember { mutableStateOf("info") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    val field = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)

    LaunchedEffect(Unit) { if (people.users.isEmpty()) people.load() }

    fun send() {
        scope.launch {
            sending = true
            try {
                val res = c.api.invoke("adminSendNotification", buildJsonObject {
                    put("userId", recipient)
                    put("title", title)
                    put("content", content)
                    put("type", type)
                })
                val n = (res["sent"].doubleValue ?: 0.0).toInt()
                c.messages.show("Notification sent", if (recipient == "all") "Delivered to $n user(s)." else "Delivered.")
                title = ""
                content = ""
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                c.messages.show("Failed to send", e.userMessage, error = true)
            }
            sending = false
        }
    }

    SubScreenScaffold(title = "Send Notification", onBack = nav::pop) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection {
                ChoiceField("Recipient", listOf("all" to "All users") + people.users.map { it.id to it.displayName }, recipient, { recipient = it })
                ChoiceField(
                    "Type",
                    listOf("info" to "Info", "reminder" to "Reminder", "streak" to "Streak", "test" to "Test"),
                    type, { type = it },
                )
            }
            SettingsSection(header = "Message") {
                OutlinedTextField(
                    title, { title = it }, label = { Text("Notification title") }, singleLine = true, modifier = field,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    content, { content = it }, label = { Text("Message (optional)") }, minLines = 3, maxLines = 6, modifier = field,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                SettingsActions {
                    BusyButton(
                        "Send notification",
                        icon = Icons.AutoMirrored.Outlined.Send,
                        busy = sending,
                        enabled = title.isNotBlank(),
                        onClick = { if (recipient == "all") confirmAll = true else send() },
                    )
                }
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            title = "Send to all users?",
            message = "“${title.trim()}” goes to every user's notification center.",
            confirmLabel = "Send to all",
            destructive = false,
            onConfirm = ::send,
            onDismiss = { confirmAll = false },
        )
    }
}

// endregion

// region Secrets

/** Admin > Integration Secrets (AdminSecretsView): write-once encrypted values. */
@Composable
fun AdminSecretsScreen(nav: AdminNav) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var secrets by remember { mutableStateOf<List<SecretStatus>>(emptyList()) }
    val drafts = remember { mutableStateMapOf<String, String>() }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf<SecretStatus?>(null) }
    val pending = drafts.values.count { it.isNotBlank() }

    fun apply(res: JsonElement) {
        runCatching { AppJson.decodeFromJsonElement(ListSerializer(SecretStatus.serializer()), res["secrets"] ?: JsonNull) }
            .getOrNull()?.let { secrets = it }
    }

    suspend fun load() {
        loading = true
        try {
            apply(c.api.invoke("adminListSecrets"))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            c.messages.show("Failed to load secrets", e.userMessage, error = true)
        }
        loading = false
    }

    fun save() {
        val values = drafts.filterValues { it.isNotBlank() }
        if (values.isEmpty()) return
        scope.launch {
            saving = true
            try {
                val res = c.api.invoke("adminSetSecrets", buildJsonObject {
                    put("values", JsonObject(values.mapValues { JsonPrimitive(it.value) }))
                })
                apply(res)
                drafts.clear()
                val failed = (res["results"] as? JsonObject).orEmpty().filter { it.value["ok"].boolValue != true }
                if (failed.isEmpty()) {
                    c.messages.show("Secrets saved", "They are encrypted and cannot be viewed again — clear one to replace it.")
                } else {
                    c.messages.show(
                        "Some secrets were not saved",
                        failed.entries.joinToString("; ") { "${it.key}: ${it.value["error"].stringValue ?: "error"}" },
                        error = true,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                c.messages.show("Save failed", e.userMessage, error = true)
            }
            saving = false
        }
    }

    LaunchedEffect(Unit) { load() }

    SubScreenScaffold(
        title = "Integration Secrets",
        onBack = nav::pop,
        actions = { IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh") } },
    ) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection {
                SettingsText("Each value is pasted once, encrypted at rest, and can't be viewed again — only cleared so a new value can be entered.")
            }
            if (loading && secrets.isEmpty()) CircularProgressIndicator(Modifier.padding(16.dp))
            secrets.groupBy { it.group }.forEach { (group, items) ->
                SettingsSection(header = group) {
                    items.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
                        val label = s.label ?: s.key
                        if (s.isSet) {
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = {
                                    Text(s.setAt?.let { "Set " + AdminParse.shortDateTime(it) } ?: "Set", color = StudySpotTheme.colors.success)
                                },
                                trailingContent = {
                                    TextButton(onClick = { confirmClear = s }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                                },
                                colors = settingsRowColors(),
                            )
                        } else {
                            OutlinedTextField(
                                value = drafts[s.key].orEmpty(),
                                onValueChange = { drafts[s.key] = it },
                                label = { Text(label) },
                                placeholder = { Text("Paste ${label.lowercase()}") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            SettingsSection {
                SettingsActions {
                    BusyButton(if (pending > 0) "Save $pending secret(s)" else "Save", onClick = ::save, busy = saving, enabled = pending > 0)
                }
            }
        }
    }

    confirmClear?.let { s ->
        ConfirmDialog(
            title = "Clear ${s.label ?: s.key}?",
            message = "The stored value is deleted so it can be re-entered.",
            confirmLabel = "Clear",
            onConfirm = {
                scope.launch {
                    try {
                        apply(c.api.invoke("adminClearSecret", buildJsonObject { put("key", s.key) }))
                        c.messages.show("Cleared", "${s.key} can now be re-entered.")
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        c.messages.show("Failed to clear", e.userMessage, error = true)
                    }
                }
            },
            onDismiss = { confirmClear = null },
        )
    }
}

// endregion

// region Deploy

/** Admin > Deploy (AdminDeployView): pull the latest commit, rebuild, restart; polls while running. */
@Composable
fun AdminDeployScreen(nav: AdminNav) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<DeployStatus?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val busy = status?.status == "running" || status?.status == "restarting"
    val label = when (status?.status) {
        "running" -> "Pulling & rebuilding…"
        "restarting" -> "Restarting…"
        "failed" -> "Failed"
        else -> "Idle"
    }

    suspend fun fetch() {
        runCatching { c.api.invokeAs<DeployStatus>("adminDeployStatus") }.getOrNull()?.let { status = it }
    }

    LaunchedEffect(busy) {
        fetch()
        while (busy) {
            delay(2_000)
            fetch()
            if (status?.status == "restarting") {
                delay(6_000)
                c.auth.refreshUser()
                fetch()
            }
            if (status?.status != "running" && status?.status != "restarting") break
        }
    }

    SubScreenScaffold(title = "Deploy", onBack = nav::pop) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection {
                SettingsText("Pull the latest commit from GitHub, rebuild, and restart the app.")
                SettingsValueRow("Status", "", trailing = {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).heightIn(max = 18.dp), strokeWidth = 2.dp)
                        Pill(
                            label,
                            color = when {
                                status?.status == "failed" -> MaterialTheme.colorScheme.error
                                busy -> StudySpotTheme.colors.accent
                                else -> StudySpotTheme.colors.secondary
                            },
                        )
                    }
                })
                status?.startedAt?.let { SettingsValueRow("Started", AdminParse.shortDateTime(it)) }
                status?.finishedAt?.let { SettingsValueRow("Finished", AdminParse.shortDateTime(it)) }
                status?.error?.let { SettingsFootnote(it, error = true, modifier = Modifier.padding(horizontal = 16.dp)) }
                SettingsActions {
                    BusyButton("Deploy latest", icon = Icons.Outlined.CloudUpload, busy = starting, enabled = !busy, onClick = { confirm = true })
                }
            }
            val log = status?.log.orEmpty()
            if (log.isNotEmpty()) {
                SettingsSection(header = "Log") {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp).heightIn(min = 120.dp)) {
                        SelectionContainer {
                            Text(log, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Pull latest and restart now?",
            message = "The server will be briefly unavailable while it restarts.",
            confirmLabel = "Deploy",
            onConfirm = {
                scope.launch {
                    starting = true
                    try {
                        status = c.api.invokeAs<DeployStatus>("adminDeployLatest")
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        c.messages.show("Failed to start deploy", e.userMessage, error = true)
                    }
                    starting = false
                }
            },
            onDismiss = { confirm = false },
        )
    }
}

// endregion
