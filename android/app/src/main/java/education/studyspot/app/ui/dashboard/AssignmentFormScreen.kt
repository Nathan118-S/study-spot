package education.studyspot.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.Dates
import education.studyspot.app.logic.AssignmentEdits
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentTemplate
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Priority
import education.studyspot.app.model.Subtask
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.ColorDot
import education.studyspot.app.ui.components.DayPickerDialog
import education.studyspot.app.ui.components.DropdownField
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.SectionCard
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.components.TimePickerDialog
import education.studyspot.app.ui.components.clean
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.components.short
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Add (assignmentId == null) or edit an assignment
 * (ios/StudySpot/Views/Dashboard/AssignmentFormView.swift): title, class, due
 * date and time, priority, type, points, notes, description and subtasks. New
 * assignments can start from a template.
 */
@Composable
fun AssignmentFormScreen(assignmentId: String?, navigator: AppNavigator) {
    val repo = LocalAppContainer.current.repository
    val assignments by repo.assignments.collectAsStateWithLifecycle()
    val hasLoaded by repo.hasLoaded.collectAsStateWithLifecycle()
    val existing = assignmentId?.let { id -> assignments.firstOrNull { it.id == id } }

    when {
        assignmentId == null -> AssignmentForm(null, navigator)
        existing != null -> AssignmentForm(existing, navigator)
        else -> SubScreenScaffold(title = "Edit assignment", onBack = navigator::back) { padding ->
            if (!hasLoaded) {
                LoadingBox(Modifier.padding(padding))
            } else {
                EmptyState(Icons.Outlined.SearchOff, "Assignment not found", modifier = Modifier.padding(padding))
            }
        }
    }
}

private val subtaskSaver = listSaver<List<Subtask>, String>(
    save = { list -> list.map { (if (it.done) "1" else "0") + it.text } },
    restore = { saved -> saved.map { Subtask(it.drop(1), it.startsWith("1")) } },
)

@Composable
private fun AssignmentForm(existing: Assignment?, navigator: AppNavigator) {
    val c = LocalAppContainer.current
    val repo = c.repository
    val classes by repo.classes.collectAsStateWithLifecycle()
    val templates by repo.templates.collectAsStateWithLifecycle()
    val user by repo.user.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    val zone = ZoneId.systemDefault()
    val dueLocal = existing?.dueDate?.atZone(zone)

    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var classId by rememberSaveable { mutableStateOf(if (existing != null) existing.classIdOrNull else classes.firstOrNull()?.id) }
    var hasDue by rememberSaveable { mutableStateOf(existing == null || existing.dueDate != null) }
    var dueDay by rememberSaveable { mutableStateOf(dueLocal?.toLocalDate() ?: LocalDate.now(zone)) }
    var dueTime by rememberSaveable { mutableStateOf(dueLocal?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(23, 59)) }
    var priority by rememberSaveable { mutableStateOf(existing?.priority ?: Priority.Medium) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: AssignmentType.Homework) }
    var points by rememberSaveable { mutableStateOf(existing?.points?.takeIf { it > 0 }?.clean.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var subtasks by rememberSaveable(stateSaver = subtaskSaver) { mutableStateOf(existing?.subtasks.orEmpty()) }
    var newSubtask by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (existing == null) repo.loadTemplates() }

    val pointsValue = points.trim().replace(',', '.').let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }
    val pointsInvalid = pointsValue == null || pointsValue < 0
    val canSave = title.isNotBlank() && !pointsInvalid && !saving

    fun applyTemplate(t: AssignmentTemplate) {
        if (t.title.isNotBlank()) title = t.title
        type = t.type
        priority = t.priority
        points = t.points.takeIf { it > 0 }?.clean.orEmpty()
        notes = t.notes
    }

    fun addSubtask() {
        val text = newSubtask.trim()
        if (text.isEmpty()) return
        subtasks = subtasks + Subtask(text)
        newSubtask = ""
    }

    fun save() {
        if (!canSave) return
        if (user?.isDemo == true) {
            c.messages.show("Demo account", "Changes aren't saved in the demo.")
            navigator.back()
            return
        }
        val pending = newSubtask.trim()
        val finalSubtasks = if (pending.isNotEmpty()) subtasks + Subtask(pending) else subtasks
        val cls = classes.firstOrNull { it.id == classId }
        val due = if (hasDue) dueDay.atTime(dueTime).atZone(zone).toInstant() else null
        val fields = buildJsonObject {
            put("title", title.trim())
            if (cls != null) put("class_id", cls.id) else put("class_id", JsonNull)
            put("class_name", cls?.displayName ?: "Uncategorized")
            if (due != null) put("due_date", Dates.format(due)) else put("due_date", JsonNull)
            put("priority", priority.wire)
            put("type", type.wire)
            put("points", pointsValue)
            put("notes", notes)
            put("description", description)
            put("subtasks", AssignmentEdits.subtasksJson(finalSubtasks))
            if (finalSubtasks.isNotEmpty() || existing?.subtasks?.isNotEmpty() == true) {
                put("progress", AssignmentEdits.progress(finalSubtasks))
            }
            if (existing == null) {
                put("source", "manual")
                put("completed", false)
            }
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
        actions = {
            TextButton(onClick = ::save, enabled = canSave) {
                Text(if (saving) "Saving…" else "Save", color = if (canSave) colors.accent else colors.mutedText, fontWeight = FontWeight.SemiBold)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (existing == null) {
                TemplatePicker(templates, onApply = ::applyTemplate, onManage = navigator::openTemplates)
            }

            SectionCard(title = "Assignment") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Chapter 5 homework") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                ClassPicker(classId, classes) { classId = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Due date", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = hasDue,
                        onCheckedChange = { hasDue = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                    )
                }
                if (hasDue) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1.4f)) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                            Text(
                                "  " + dueDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                                color = colors.secondary,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                            Text("  " + dueTime.short(), color = colors.secondary, maxLines = 1)
                        }
                    }
                }
            }

            SectionCard(title = "Details") {
                Text("Priority", style = MaterialTheme.typography.labelLarge, color = colors.secondary)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = priority == p,
                            onClick = { priority = p },
                            shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = colors.accent.copy(alpha = 0.16f),
                                activeContentColor = colors.secondary,
                                inactiveContentColor = colors.secondary,
                            ),
                        ) { Text(p.label) }
                    }
                }
                DropdownField("Type", type.label, AssignmentType.entries.map { it to it.label }) { type = it }
                OutlinedTextField(
                    value = points,
                    onValueChange = { points = it },
                    label = { Text("Points (max)") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    isError = pointsInvalid,
                    supportingText = if (pointsInvalid) { { Text("Enter a number (0 or more).") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Description") {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("What the assignment asks for") },
                    minLines = 2,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Notes") {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Additional notes…") },
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Subtasks") {
                subtasks.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = s.done,
                            onCheckedChange = { done -> subtasks = subtasks.mapIndexed { j, t -> if (j == i) t.copy(done = done) else t } },
                            colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.mutedText),
                        )
                        Text(
                            s.text,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (s.done) TextDecoration.LineThrough else null,
                            color = if (s.done) colors.mutedText else colors.text,
                        )
                        IconButton(onClick = { subtasks = subtasks.filterIndexed { j, _ -> j != i } }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove subtask ${s.text}", tint = colors.mutedText)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSubtask,
                        onValueChange = { newSubtask = it },
                        placeholder = { Text("Add a subtask…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addSubtask() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = ::addSubtask, enabled = newSubtask.isNotBlank()) {
                        Icon(Icons.Outlined.AddCircle, contentDescription = "Add subtask", tint = if (newSubtask.isNotBlank()) colors.accent else colors.mutedText)
                    }
                }
                if (subtasks.isNotEmpty()) {
                    Text("Progress is calculated from completed subtasks.", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
                }
            }
        }
    }

    if (pickDate) {
        DayPickerDialog(initial = dueDay, onDismiss = { pickDate = false }, onPick = { dueDay = it })
    }
    if (pickTime) {
        TimePickerDialog(initial = dueTime, onDismiss = { pickTime = false }, onPick = { dueTime = it }, title = "Due time")
    }
}

@Composable
private fun ClassPicker(classId: String?, classes: List<education.studyspot.app.model.SchoolClass>, onSelect: (String?) -> Unit) {
    val selected = classes.firstOrNull { it.id == classId }
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            ColorDot(parseHexColor(selected?.color ?: education.studyspot.app.model.SchoolClass.UNCATEGORIZED_COLOR), size = 10.dp)
            Text(
                "  Class: " + (selected?.displayName ?: "Uncategorized"),
                color = StudySpotTheme.colors.secondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Uncategorized") },
                leadingIcon = { ColorDot(parseHexColor(education.studyspot.app.model.SchoolClass.UNCATEGORIZED_COLOR), size = 10.dp) },
                onClick = { onSelect(null); open = false },
            )
            classes.forEach { cls ->
                DropdownMenuItem(
                    text = { Text(cls.displayName) },
                    leadingIcon = { ColorDot(parseHexColor(cls.color), size = 10.dp) },
                    onClick = { onSelect(cls.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun TemplatePicker(templates: List<AssignmentTemplate>, onApply: (AssignmentTemplate) -> Unit, onManage: () -> Unit) {
    val colors = StudySpotTheme.colors
    var open by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf<String?>(null) }
    SectionCard(title = "Use a template") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { open = true }, enabled = templates.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        "  " + when {
                            templates.isEmpty() -> "No templates yet"
                            applied != null -> applied.orEmpty()
                            else -> "Apply…"
                        },
                        maxLines = 1,
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    templates.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.name.ifBlank { t.title.ifBlank { "Untitled template" } }) },
                            onClick = { open = false; applied = t.name.ifBlank { t.title }; onApply(t) },
                        )
                    }
                }
            }
            OutlinedButton(onClick = onManage) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                Text("  Manage", color = colors.secondary)
            }
        }
    }
}
