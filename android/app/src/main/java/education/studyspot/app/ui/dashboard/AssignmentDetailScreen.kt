package education.studyspot.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.WebSignIn
import education.studyspot.app.core.userMessage
import education.studyspot.app.core.ApiException
import education.studyspot.app.logic.AssignmentEdits
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.ClassroomAttachment
import education.studyspot.app.model.ClassroomAttachments
import education.studyspot.app.model.Grading
import education.studyspot.app.model.Source
import education.studyspot.app.model.Subtask
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.ColorDot
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.components.ProgressBar
import education.studyspot.app.ui.components.SectionCard
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.components.clean
import education.studyspot.app.ui.components.gradeColor
import education.studyspot.app.ui.components.long
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.components.priorityColor
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * Assignment (ios/StudySpot/Views/Dashboard/AssignmentDetailView.swift):
 * details, Classroom attachments, notes, subtasks, progress, score with letter
 * grade (the user's grading scale), complete / uncomplete, edit and delete.
 *
 * Opened from a deep link the assignment may not be loaded yet: a loader shows
 * until it appears.
 */
@Composable
fun AssignmentDetailScreen(assignmentId: String, navigator: AppNavigator) {
    val repo = LocalAppContainer.current.repository
    val assignments by repo.assignments.collectAsStateWithLifecycle()
    val hasLoaded by repo.hasLoaded.collectAsStateWithLifecycle()
    val a = assignments.firstOrNull { it.id == assignmentId }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    SubScreenScaffold(
        title = "Assignment",
        onBack = navigator::back,
        actions = {
            if (a != null) {
                IconButton(onClick = { navigator.editAssignment(a.id) }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit assignment") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Study timer") },
                            leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                            onClick = { menu = false; navigator.openTimer() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = StudySpotTheme.colors.danger) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = StudySpotTheme.colors.danger) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            a != null -> DetailContent(a, Modifier.padding(padding), onEdit = { navigator.editAssignment(a.id) }, onDelete = { confirmDelete = true })
            !hasLoaded -> LoadingBox(Modifier.padding(padding))
            else -> EmptyState(
                Icons.Outlined.SearchOff,
                "Assignment not found",
                modifier = Modifier.padding(padding),
                message = "It may have been deleted.",
                action = { TextButton(onClick = navigator::back) { Text("Back") } },
            )
        }
    }

    if (confirmDelete && a != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this assignment?") },
            text = { Text("“${a.displayTitle}” will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { if (repo.deleteAssignment(a)) navigator.back() }
                }) { Text("Delete", color = StudySpotTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = StudySpotTheme.colors.secondary) } },
        )
    }
}

@Composable
private fun DetailContent(a: Assignment, modifier: Modifier, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = LocalAppContainer.current
    val repo = c.repository
    val user by repo.user.collectAsStateWithLifecycle()
    val classes by repo.classes.collectAsStateWithLifecycle()
    val colors = StudySpotTheme.colors
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val isDemo = user?.isDemo == true

    var notes by rememberSaveable(a.id) { mutableStateOf(a.notes) }
    var score by rememberSaveable(a.id) { mutableStateOf(a.score?.clean.orEmpty()) }
    var newSubtask by rememberSaveable(a.id) { mutableStateOf("") }
    var sliding by remember { mutableStateOf(false) }
    var slider by remember { mutableFloatStateOf(a.progress.toFloat()) }
    if (!sliding) slider = a.progress.toFloat()

    val classColor = parseHexColor(classes.firstOrNull { it.id == a.classIdOrNull }?.color ?: repo.colorFor(a.classIdOrNull))
    val scale = user?.gradingScale.orEmpty()
    val letter = Grading.letter(a.score, a.points, scale)
    val scoreInvalid = AssignmentEdits.parseScore(score) is AssignmentEdits.ScoreInput.Invalid

    fun persist(patch: JsonObject, optimistic: Assignment) {
        // App scope: a save started as the screen closes must still finish.
        c.scope.launch { repo.updateAssignment(a.id, patch, optimistic) }
    }
    /** Saves notes and score if they changed (an invalid score is left unsaved). */
    fun commitFields(current: Assignment, notesText: String, scoreText: String) {
        if (repo.assignment(current.id) == null) return // deleted meanwhile
        val parsed = AssignmentEdits.parseScore(scoreText) as? AssignmentEdits.ScoreInput.Valid
        val scoreChanged = parsed != null && parsed.value != current.score
        val notesChanged = notesText != current.notes
        if (!scoreChanged && !notesChanged) return
        val patch = buildJsonObject {
            if (notesChanged) put("notes", notesText)
            if (scoreChanged) put("score", parsed.value) // null clears the score
        }
        persist(patch, current.copy(notes = notesText, score = if (scoreChanged) parsed.value else current.score))
    }
    fun persistSubtasks(list: List<Subtask>) {
        val p = AssignmentEdits.progress(list)
        persist(
            buildJsonObject { put("subtasks", AssignmentEdits.subtasksJson(list)); put("progress", p) },
            a.copy(subtasks = list, progress = p),
        )
    }
    fun addSubtask() {
        val text = newSubtask.trim()
        if (text.isEmpty()) return
        newSubtask = ""
        persistSubtasks(a.subtasks + Subtask(text, false))
    }

    // Save unsaved notes / score when leaving the screen.
    val latest by rememberUpdatedState(Triple(a, notes, score))
    DisposableEffect(a.id) {
        onDispose { latest.let { (cur, n, s) -> commitFields(cur, n, s) } }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorDot(classColor, size = 9.dp)
                Text(
                    a.className?.takeIf { it.isNotBlank() } ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (a.source != Source.Manual) Pill(a.source.label)
            }
            Text(
                a.displayTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (a.completed) colors.mutedText else colors.text,
                textDecoration = if (a.completed) TextDecoration.LineThrough else null,
            )
            if (a.description.isNotBlank()) {
                Text(a.description, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("${a.priority.label} priority", color = priorityColor(a.priority))
                Pill(a.type.label)
                if (a.points > 0) Pill("${a.points.clean} pts")
                if (a.isOverdue) Pill("Overdue", color = colors.danger, filled = true, icon = Icons.Filled.Warning)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Outlined.EventNote, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                Text(
                    DateText.long(a.dueDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (a.isOverdue) colors.danger else colors.mutedText,
                )
            }
        }

        // Complete
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mark as complete", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (a.source == Source.GoogleClassroom && a.externalId != null && user?.syncCompletionToClassroom != false) {
                        Text("Also turns it in on Google Classroom.", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
                    }
                }
                Switch(
                    checked = a.completed,
                    onCheckedChange = { scope.launch { repo.toggleComplete(a, withProgress = true) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                )
            }
        }

        if (a.source == Source.GoogleClassroom && a.externalId != null && !isDemo) {
            ClassroomAttachmentsSection(a.id)
        }

        // Notes
        SectionCard(title = "Notes") {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Add notes about this assignment…") },
                minLines = 3,
                maxLines = 10,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) commitFields(a, notes, score) },
            )
            if (notes != a.notes) {
                TextButton(onClick = { focus.clearFocus(); commitFields(a, notes, score) }, modifier = Modifier.align(Alignment.End)) {
                    Text("Save notes")
                }
            }
        }

        // Subtasks
        val doneCount = a.subtasks.count { it.done }
        SectionCard(
            title = "Subtasks",
            trailing = if (a.subtasks.isNotEmpty()) {
                { Text("$doneCount/${a.subtasks.size} done", style = MaterialTheme.typography.labelMedium, color = colors.mutedText) }
            } else null,
        ) {
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
            a.subtasks.forEachIndexed { i, s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            persistSubtasks(a.subtasks.mapIndexed { j, t -> if (j == i) t.copy(done = !t.done) else t })
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = s.done,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.mutedText),
                        modifier = Modifier.padding(8.dp),
                    )
                    Text(
                        s.text,
                        modifier = Modifier.weight(1f),
                        color = if (s.done) colors.mutedText else colors.text,
                        textDecoration = if (s.done) TextDecoration.LineThrough else null,
                    )
                    IconButton(onClick = { persistSubtasks(a.subtasks.filterIndexed { j, _ -> j != i }) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Delete subtask ${s.text}", tint = colors.mutedText)
                    }
                }
            }
        }

        // Progress
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Progress", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    "${slider.roundToInt()}%",
                    fontWeight = FontWeight.SemiBold,
                    color = if (slider >= 100f) colors.success else colors.secondary,
                )
            }
            if (a.subtasks.isEmpty()) {
                Slider(
                    value = slider,
                    onValueChange = { sliding = true; slider = it },
                    onValueChangeFinished = {
                        sliding = false
                        val p = slider.roundToInt().toDouble()
                        if (p != a.progress) persist(buildJsonObject { put("progress", p) }, a.copy(progress = p))
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                )
            } else {
                ProgressBar(a.progress)
                Text("Progress is calculated from completed subtasks.", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
            }
        }

        // Grade
        if (a.points > 0) {
            SectionCard(title = "Grade") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = score,
                        onValueChange = { score = it },
                        label = { Text("Score") },
                        singleLine = true,
                        isError = scoreInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier
                            .widthIn(max = 120.dp)
                            .onFocusChanged { if (!it.isFocused) commitFields(a, notes, score) },
                    )
                    Text(
                        "/ ${a.points.clean} pts" + (Grading.percent(a)?.let { " · $it%" } ?: ""),
                        color = colors.mutedText,
                        modifier = Modifier.weight(1f),
                    )
                    if (letter != null) {
                        val lc = gradeColor(letter)
                        Box(
                            Modifier.clip(MaterialTheme.shapes.small).background(lc.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(letter, color = lc, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    }
                }
                if (scoreInvalid) {
                    Text("Enter a number, or leave it empty.", style = MaterialTheme.typography.bodySmall, color = colors.danger)
                }
            }
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                Text("  Edit", color = colors.secondary)
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.5f)),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.danger, modifier = Modifier.size(18.dp))
                Text("  Delete", color = colors.danger)
            }
        }
    }
}

/** Google Classroom materials (functions/getClassroomAttachments), opened in a Custom Tab. */
@Composable
private fun ClassroomAttachmentsSection(assignmentId: String) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val colors = StudySpotTheme.colors
    var attachments by remember(assignmentId) { mutableStateOf<List<ClassroomAttachment>?>(null) }
    var error by remember(assignmentId) { mutableStateOf<String?>(null) }

    LaunchedEffect(assignmentId) {
        try {
            attachments = c.api.invokeAs<ClassroomAttachments>(
                "getClassroomAttachments",
                buildJsonObject { put("assignment_id", assignmentId) },
            ).attachments.filter { it.url.isNotBlank() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = if ((e as? ApiException)?.notConnected == true) {
                "Connect Google Classroom in Settings to see attached files."
            } else {
                e.userMessage.ifBlank { "Unable to load attachments" }
            }
        }
    }

    val list = attachments
    when {
        list == null && error == null -> SectionCard(title = "Attached files") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Loading attachments…", color = colors.mutedText)
            }
        }
        error != null -> SectionCard(title = "Attached files") {
            Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
        }
        !list.isNullOrEmpty() -> SectionCard(title = "Attached files", contentPadding = false) {
            list.forEachIndexed { i, att ->
                if (i > 0) HorizontalDivider(color = colors.border)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val ok = WebSignIn.openInCustomTab(context, att.url.toUri(), colors.base.toArgb())
                            if (!ok) c.messages.show("No browser found to open the file", error = true)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        when (att.kind) {
                            "drive" -> Icons.Outlined.Description
                            "video" -> Icons.Outlined.OndemandVideo
                            "form" -> Icons.AutoMirrored.Outlined.ListAlt
                            else -> Icons.Outlined.Link
                        },
                        contentDescription = null,
                        tint = colors.secondary,
                    )
                    Text(att.title.ifBlank { att.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open", tint = colors.mutedText, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
