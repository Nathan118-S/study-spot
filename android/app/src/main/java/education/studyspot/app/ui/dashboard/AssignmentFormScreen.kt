package education.studyspot.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.Dates
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Priority
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.components.SubScreenScaffold
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Add (assignmentId == null) or edit an assignment
 * (ios/StudySpot/Views/Dashboard/AssignmentFormView.swift).
 *
 * Minimal version: title, class, due date (11:59 PM local), priority, type.
 * TODO(dashboard): due time, points/score, notes/description, subtasks,
 * progress, templates (repository.templates), validation.
 */
@Composable
fun AssignmentFormScreen(assignmentId: String?, navigator: AppNavigator) {
    val repo = LocalAppContainer.current.repository
    val classes by repo.classes.collectAsStateWithLifecycle()
    val existing = remember(assignmentId) { assignmentId?.let(repo::assignment) }
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()

    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var classId by rememberSaveable { mutableStateOf(existing?.classIdOrNull) }
    var dueMillis by rememberSaveable { mutableStateOf(existing?.dueDate?.toEpochMilli()) }
    var priority by rememberSaveable { mutableStateOf(existing?.priority ?: Priority.Medium) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: AssignmentType.Homework) }
    var saving by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    fun save() {
        val cls = classes.firstOrNull { it.id == classId }
        val fields = buildJsonObject {
            put("title", title.trim())
            if (cls != null) { put("class_id", cls.id); put("class_name", cls.displayName) } else { put("class_id", JsonNull); put("class_name", JsonNull) }
            dueMillis?.let { put("due_date", Dates.format(Instant.ofEpochMilli(it))) } ?: put("due_date", JsonNull)
            put("priority", priority.wire)
            put("type", type.wire)
        }
        scope.launch {
            saving = true
            val ok = if (existing == null) repo.createAssignment(fields) != null else repo.updateAssignment(existing.id, fields) != null
            saving = false
            if (ok) navigator.back()
        }
    }

    SubScreenScaffold(
        title = if (existing == null) "New assignment" else "Edit assignment",
        onBack = navigator::back,
        actions = { TextButton(onClick = ::save, enabled = title.isNotBlank() && !saving) { Text("Save") } },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Dropdown(
                label = "Class",
                selected = classes.firstOrNull { it.id == classId }?.displayName ?: "No class",
                options = listOf<Pair<String?, String>>(null to "No class") + classes.map { it.id to it.displayName },
                onSelect = { classId = it },
            )
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Due: " + (dueMillis?.let { DateText.short(Instant.ofEpochMilli(it)) } ?: "No due date"))
            }
            Dropdown("Priority", priority.label, Priority.entries.map { it to it.label }) { priority = it }
            Dropdown("Type", type.label, AssignmentType.entries.map { it to it.label }) { type = it }
        }
    }

    if (pickDate) {
        // DatePicker works in UTC midnight; convert to 11:59 PM local on that day.
        val initialUtc = dueMillis?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        dueMillis = day.atTime(LocalTime.of(23, 59)).atZone(zone).toInstant().toEpochMilli()
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { dueMillis = null; pickDate = false }) { Text("No due date") }
            },
        ) { DatePicker(state) }
    }
}

@Composable
private fun <T> Dropdown(label: String, selected: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}
