package education.studyspot.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.Dates
import education.studyspot.app.core.arrayValue
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.model.Grading
import education.studyspot.app.ui.components.ColorDot
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.settings.BusyButton
import education.studyspot.app.ui.settings.ConfirmDialog
import education.studyspot.app.ui.settings.GradeRow
import education.studyspot.app.ui.settings.GradeScaleEditor
import education.studyspot.app.ui.settings.SettingsActions
import education.studyspot.app.ui.settings.SettingsColumn
import education.studyspot.app.ui.settings.SettingsFootnote
import education.studyspot.app.ui.settings.SettingsSection
import education.studyspot.app.ui.settings.SettingsText
import education.studyspot.app.ui.settings.SettingsToggle
import education.studyspot.app.ui.settings.SettingsValueRow
import education.studyspot.app.ui.settings.TimePickerAlert
import education.studyspot.app.ui.settings.gradeScaleFrom
import education.studyspot.app.ui.settings.gradeScaleJson
import education.studyspot.app.ui.settings.settingsRowColors
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.runtime.mutableStateListOf
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

val AdminRecordKind.icon: ImageVector
    get() = when (this) {
        AdminRecordKind.Assignments -> Icons.AutoMirrored.Outlined.Assignment
        AdminRecordKind.Classes -> Icons.AutoMirrored.Outlined.MenuBook
        AdminRecordKind.Templates -> Icons.Outlined.ContentCopy
        AdminRecordKind.StudySessions -> Icons.Outlined.Timer
        AdminRecordKind.Notifications -> Icons.Outlined.Notifications
    }

/** The loaded rows of one record list, shared with the editor so saves show up on return. */
class AdminRecordsState {
    val records: SnapshotStateList<AdminRecord> = mutableStateListOf()
    var loading by mutableStateOf(true)

    fun replace(r: AdminRecord) {
        val i = records.indexOfFirst { it.id == r.id }
        if (i >= 0) records[i] = r
    }

    fun remove(id: String) { records.removeAll { it.id == id } }
}

/** Admin > account > Data > kind (AdminRecordListView). */
@Composable
fun AdminRecordListScreen(nav: AdminNav, model: AdminAccountModel, kind: AdminRecordKind) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val state = remember(model, kind) { AdminRecordsState() }
    var search by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AdminRecord?>(null) }
    val filtered = state.records.filter {
        search.isBlank() || it.title(kind).contains(search, true) || it.subtitle(kind).contains(search, true)
    }

    suspend fun load() {
        state.loading = true
        adminAttempt(c.messages, "Failed to load") {
            val res = model.call("adminListRecords", buildJsonObject { put("type", kind.wire) })
            state.records.clear()
            state.records.addAll(AdminRecord.list(res))
        }
        state.loading = false
    }

    LaunchedEffect(model, kind) { if (state.records.isEmpty()) load() }

    SubScreenScaffold(
        title = kind.title,
        onBack = nav::pop,
        actions = {
            IconButton(onClick = { confirmClear = true }, enabled = state.records.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Delete all", tint = if (state.records.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsSection {
                when {
                    state.loading && state.records.isEmpty() -> LoadingBox()
                    state.records.isEmpty() -> SettingsText("Nothing here.")
                }
                filtered.forEachIndexed { i, record ->
                    if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
                    val unread = kind == AdminRecordKind.Notifications && record.values["read"].boolValue != true
                    ListItem(
                        headlineContent = { Text(record.title(kind), fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) },
                        supportingContent = record.subtitle(kind).takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 2) } },
                        leadingContent = if (kind == AdminRecordKind.Classes) ({ ColorDot(parseHexColor(record.string("color") ?: "#6366f1"), size = 12.dp) }) else null,
                        trailingContent = {
                            IconButton(onClick = { deleting = record }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = settingsRowColors(),
                        modifier = Modifier.clickable { nav.push(AdminDest.RecordEditor(model, kind, record, state)) },
                    )
                }
            }
        }
    }

    deleting?.let { record ->
        ConfirmDialog(
            title = "Delete this record?",
            message = "“${record.title(kind)}” is deleted permanently.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    adminAttempt(c.messages, "Delete failed") {
                        model.call("adminDeleteRecord", buildJsonObject { put("type", kind.wire); put("id", record.id) })
                        state.remove(record.id)
                        model.load()
                    }
                }
            },
            onDismiss = { deleting = null },
        )
    }
    if (confirmClear) {
        val noun = kind.title.lowercase()
        ConfirmDialog(
            title = "Delete all $noun?",
            message = "This permanently deletes every one of this user's $noun. This can't be undone.",
            confirmLabel = "Delete all ${state.records.size}",
            onConfirm = {
                scope.launch {
                    adminAttempt(c.messages, "Delete failed") {
                        val res = model.call("adminClearRecords", buildJsonObject { put("type", kind.wire) })
                        state.records.clear()
                        c.messages.show("Deleted", "${(res["deleted"].doubleValue ?: 0.0).toInt()} $noun removed.")
                        model.load()
                    }
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** Admin > account > Data > record (AdminRecordEditor). Saves only the changed columns. */
@Composable
fun AdminRecordEditorScreen(nav: AdminNav, model: AdminAccountModel, kind: AdminRecordKind, record: AdminRecord, list: AdminRecordsState) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val values = remember(record) { mutableStateMapOf<String, JsonElement>().apply { putAll(record.values) } }
    var classes by remember { mutableStateOf<List<AdminRecord>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val changes = adminRecordChanges(kind, record.values, values)

    LaunchedEffect(kind) {
        if (kind == AdminRecordKind.Assignments) {
            runCatching { model.call("adminListRecords", buildJsonObject { put("type", AdminRecordKind.Classes.wire) }) }
                .getOrNull()?.let { classes = AdminRecord.list(it) }
        }
    }

    fun save() {
        if (changes.isEmpty()) return
        scope.launch {
            saving = true
            adminAttempt(c.messages, "Couldn't save") {
                val res = model.call("adminUpdateRecord", buildJsonObject {
                    put("type", kind.wire)
                    put("id", record.id)
                    put("changes", changes)
                })
                res["record"]?.let { list.replace(AdminRecord.from(it)) }
                c.messages.show("Saved")
                nav.pop()
                model.load()
            }
            saving = false
        }
    }

    SubScreenScaffold(
        title = record.title(kind),
        onBack = nav::pop,
        actions = { TextButton(onClick = ::save, enabled = !saving && changes.isNotEmpty()) { Text("Save") } },
    ) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection {
                kind.fields.filter { it.kind != AdminFieldKind.Subtasks }.forEach { field ->
                    AdminFieldEditor(field, values, classes)
                }
            }
            if (kind == AdminRecordKind.Assignments) {
                SettingsSection(header = "Subtasks") { SubtasksEditor(values) }
            }
            SettingsSection {
                SettingsValueRow("ID", record.id)
                record.date("created_at")?.let { SettingsValueRow("Created", AdminParse.shortDateTime(it)) }
                record.date("updated_at")?.let { SettingsValueRow("Updated", AdminParse.shortDateTime(it)) }
            }
            SettingsSection {
                SettingsActions {
                    BusyButton("Save", onClick = ::save, busy = saving, enabled = changes.isNotEmpty())
                    BusyButton("Delete", onClick = { confirmDelete = true }, destructive = true)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this record?",
            message = "This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    adminAttempt(c.messages, "Delete failed") {
                        model.call("adminDeleteRecord", buildJsonObject { put("type", kind.wire); put("id", record.id) })
                        list.remove(record.id)
                        c.messages.show("Deleted")
                        nav.pop()
                        model.load()
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private val editorField = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)

private fun JsonElement?.text(): String = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()

@Composable
private fun AdminFieldEditor(field: AdminField, values: SnapshotStateMap<String, JsonElement>, classes: List<AdminRecord>) {
    val key = field.key
    when (field.kind) {
        AdminFieldKind.Text -> OutlinedTextField(
            value = values[key].text(), onValueChange = { values[key] = JsonPrimitive(it) },
            label = { Text(field.label) }, singleLine = true, modifier = editorField,
        )
        AdminFieldKind.Multiline -> OutlinedTextField(
            value = values[key].text(), onValueChange = { values[key] = JsonPrimitive(it) },
            label = { Text(field.label) }, minLines = 2, maxLines = 8, modifier = editorField,
        )
        AdminFieldKind.Number -> {
            var text by remember(key) { mutableStateOf(values[key].text()) }
            OutlinedTextField(
                value = text,
                onValueChange = { s ->
                    text = s
                    val t = s.trim()
                    values[key] = when {
                        t.isEmpty() -> JsonNull
                        t.toDoubleOrNull() != null -> JsonPrimitive(t.toDouble())
                        else -> JsonPrimitive(t)
                    }
                },
                label = { Text(field.label) },
                placeholder = { Text("None") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = editorField,
            )
        }
        AdminFieldKind.Bool -> SettingsToggle(field.label, checked = values[key].boolValue == true, onCheckedChange = { values[key] = JsonPrimitive(it) })
        AdminFieldKind.Choice -> ChoiceField(
            label = field.label,
            options = field.options.map { it to it.replaceFirstChar(Char::uppercase) },
            selected = values[key].text(),
            onSelect = { values[key] = JsonPrimitive(it) },
        )
        AdminFieldKind.Color -> OutlinedTextField(
            value = values[key].text(), onValueChange = { values[key] = JsonPrimitive(it.trim()) },
            label = { Text(field.label) }, placeholder = { Text("#6366f1") }, singleLine = true,
            leadingIcon = { ColorDot(parseHexColor(values[key].text().ifEmpty { "#6366f1" }), size = 14.dp) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = editorField,
        )
        AdminFieldKind.Date -> DateFieldEditor(field, values)
        AdminFieldKind.ClassRef -> ChoiceField(
            label = field.label,
            options = listOf("" to "None") + classes.map { it.id to (it.string("name") ?: "Class") },
            selected = values["class_id"].text(),
            onSelect = { id ->
                if (id.isEmpty()) {
                    values["class_id"] = JsonNull
                } else {
                    values["class_id"] = JsonPrimitive(id)
                    values["class_name"] = classes.firstOrNull { it.id == id }?.string("name")?.let(::JsonPrimitive) ?: JsonNull
                }
            },
        )
        AdminFieldKind.Subtasks -> Unit
    }
}

@Composable
private fun DateFieldEditor(field: AdminField, values: SnapshotStateMap<String, JsonElement>) {
    val key = field.key
    val zone = ZoneId.systemDefault()
    val instant = Dates.parse(values[key].stringValue)
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    fun set(i: Instant?) { values[key] = i?.let { JsonPrimitive(Dates.format(it)) } ?: JsonNull }

    SettingsToggle(field.label, checked = instant != null, onCheckedChange = { on -> set(if (on) Instant.now() else null) })
    if (instant != null) {
        val local = instant.atZone(zone)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { pickingDate = true }) { Text(education.studyspot.app.ui.components.DateText.date(instant)) }
            TextButton(onClick = { pickingTime = true }) { Text(AdminParse.time(instant)) }
        }
        if (pickingDate) {
            val state = rememberDatePickerState(initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            set(ZonedDateTime.of(day, local.toLocalTime(), zone).toInstant())
                        }
                        pickingDate = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
            ) { DatePicker(state = state) }
        }
        if (pickingTime) {
            TimePickerAlert(field.label, local.toLocalTime(), onConfirm = { t: LocalTime ->
                set(ZonedDateTime.of(local.toLocalDate(), t, zone).toInstant())
            }, onDismiss = { pickingTime = false })
        }
    }
}

@Composable
private fun SubtasksEditor(values: SnapshotStateMap<String, JsonElement>) {
    val items = values["subtasks"].arrayValue.orEmpty()

    fun update(i: Int, key: String, value: JsonElement) {
        val list = items.toMutableList()
        if (i !in list.indices) return
        list[i] = JsonObject(((list[i] as? JsonObject).orEmpty()) + (key to value))
        values["subtasks"] = JsonArray(list)
    }

    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        items.forEachIndexed { i, item ->
            val done = item["done"].boolValue == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { update(i, "done", JsonPrimitive(!done)) }) {
                    Icon(
                        if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (done) "Done" else "Not done",
                        tint = if (done) StudySpotTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = item["text"].text(),
                    onValueChange = { update(i, "text", JsonPrimitive(it)) },
                    placeholder = { Text("Subtask") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { values["subtasks"] = JsonArray(items.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove subtask")
                }
            }
        }
        TextButton(onClick = {
            values["subtasks"] = JsonArray(items + buildJsonObject { put("text", ""); put("done", false) })
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add subtask", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Admin > account > Grading scale (AdminGradingScaleEditor). */
@Composable
fun AdminGradingScreen(nav: AdminNav, model: AdminAccountModel) {
    val scope = rememberCoroutineScope()
    val rows = remember(model) {
        mutableStateListOf<GradeRow>().apply { addAll((model.user?.gradingScale ?: Grading.defaultScale).map(GradeRow::from)) }
    }
    SubScreenScaffold(
        title = "Grading scale",
        onBack = nav::pop,
        actions = {
            TextButton(enabled = !model.busy, onClick = {
                scope.launch {
                    model.updateSettings(buildJsonObject { put("grading_scale", gradeScaleJson(gradeScaleFrom(rows))) })
                    nav.pop()
                }
            }) { Text("Save") }
        },
    ) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection(footer = { SettingsFootnote("The minimum percentage for each letter grade.") }) { GradeScaleEditor(rows) }
            SettingsSection {
                SettingsActions {
                    TextButton(onClick = { rows.clear(); rows.addAll(Grading.defaultScale.map(GradeRow::from)) }) {
                        Text("Reset to default", color = StudySpotTheme.colors.secondary)
                    }
                }
            }
        }
    }
}

/** Admin > account > Merge (AdminMergeView). */
@Composable
fun AdminMergeScreen(nav: AdminNav, model: AdminAccountModel, people: AdminPeopleModel) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user = model.user ?: return
    var targetId by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (people.users.isEmpty()) people.load() }

    SubScreenScaffold(title = "Merge account", onBack = nav::pop) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection {
                SettingsText("Move ${user.username}'s assignments and classes into another account, then delete ${user.username}.")
            }
            SettingsSection(header = "Keep which account?") {
                ChoiceField(
                    label = "Target account",
                    options = listOf("" to "Choose…") + people.users.filter { it.id != user.id }.map { u ->
                        u.id to (u.fullName?.takeIf { it.isNotBlank() }?.let { "$it — ${u.username}" } ?: u.username)
                    },
                    selected = targetId,
                    onSelect = { targetId = it },
                )
                SettingsActions {
                    BusyButton("Merge accounts", onClick = { confirm = true }, destructive = true, busy = model.busy, enabled = targetId.isNotEmpty())
                }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Merge accounts?",
            message = "${user.username} will be deleted after its data is moved. This can't be undone.",
            confirmLabel = "Merge and delete ${user.username}",
            onConfirm = {
                scope.launch {
                    model.busy = true
                    // Names both accounts, so it doesn't go through model.call (which adds userId).
                    adminAttempt(c.messages, "Merge failed") {
                        val res = AdminUnlock.run(c.api) {
                            c.api.invoke("adminMergeAccounts", buildJsonObject { put("sourceId", user.id); put("targetId", targetId) })
                        }
                        val a = (res["movedAssignments"].doubleValue ?: 0.0).toInt()
                        val cl = (res["movedClasses"].doubleValue ?: 0.0).toInt()
                        c.messages.show("Accounts merged", "Moved $a assignment(s) and $cl class(es).")
                        model.gone = true
                        nav.popTo { it == AdminDest.People }
                        people.load()
                    }
                    model.busy = false
                }
            },
            onDismiss = { confirm = false },
        )
    }
}
