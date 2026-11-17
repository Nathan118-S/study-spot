package education.studyspot.app.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.model.AssignmentTemplate
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Priority
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.DropdownField
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.components.clean
import education.studyspot.app.ui.components.priorityColor
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Templates (ios/StudySpot/Views/Templates/TemplateViews.swift): list, create,
 * edit and delete assignment templates. The assignment form applies them.
 */
@Composable
fun TemplatesScreen(navigator: AppNavigator) {
    val c = LocalAppContainer.current
    val repo = c.repository
    val templates by repo.templates.collectAsStateWithLifecycle()
    val user by repo.user.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    var loading by remember { mutableStateOf(templates.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AssignmentTemplate?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<AssignmentTemplate?>(null) }
    val isDemo = user?.isDemo == true

    LaunchedEffect(Unit) {
        repo.loadTemplates()
        loading = false
    }

    SubScreenScaffold(
        title = "Templates",
        onBack = navigator::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New template") },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; repo.loadTemplates(); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    loading -> item { LoadingBox() }
                    templates.isEmpty() -> item {
                        EmptyState(
                            Icons.Outlined.ContentCopy,
                            "No templates yet",
                            message = if (isDemo) "Templates aren't available in the demo." else "Create one to prefill new assignments.",
                        )
                    }
                    else -> items(templates, key = { it.id }) { t ->
                        TemplateCard(t, onClick = { editing = t }, onDelete = { confirmDelete = t })
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        TemplateFormSheet(
            template = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { fields ->
                val target = editing
                scope.launch {
                    if (isDemo) {
                        c.messages.show("Demo account", "Changes aren't saved in the demo.")
                        creating = false; editing = null
                        return@launch
                    }
                    val saved = if (target == null) repo.createTemplate(fields) else repo.updateTemplate(target.id, fields)
                    if (saved != null) {
                        creating = false; editing = null
                        c.messages.show(if (target == null) "Template created" else "Template saved")
                    }
                }
            },
        )
    }

    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${t.name.ifBlank { "template" }}”?") },
            text = { Text("This template will be removed. Existing assignments are unaffected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { repo.deleteTemplate(t) }
                }) { Text("Delete", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = colors.secondary) } },
        )
    }
}

@Composable
private fun TemplateCard(t: AssignmentTemplate, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = StudySpotTheme.colors
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.name.ifBlank { "Untitled template" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (t.title.isNotBlank()) {
                    Text("Title: ${t.title}", style = MaterialTheme.typography.bodySmall, color = colors.mutedText, maxLines = 1)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(t.type.label)
                    Pill(t.priority.label, color = priorityColor(t.priority))
                    if (t.points > 0) Pill("${t.points.clean} pts")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete template ${t.name}", tint = colors.mutedText)
            }
        }
    }
}

@Composable
private fun TemplateFormSheet(
    template: AssignmentTemplate?,
    onDismiss: () -> Unit,
    onSave: (kotlinx.serialization.json.JsonObject) -> Unit,
) {
    val colors = StudySpotTheme.colors
    val key = template?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(template?.name.orEmpty()) }
    var title by rememberSaveable(key) { mutableStateOf(template?.title.orEmpty()) }
    var priority by rememberSaveable(key) { mutableStateOf(template?.priority ?: Priority.Medium) }
    var type by rememberSaveable(key) { mutableStateOf(template?.type ?: AssignmentType.Homework) }
    var points by rememberSaveable(key) { mutableStateOf(template?.points?.takeIf { it > 0 }?.clean.orEmpty()) }
    var notes by rememberSaveable(key) { mutableStateOf(template?.notes.orEmpty()) }
    val pointsValue = points.trim().replace(',', '.').let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }
    val pointsInvalid = pointsValue == null || pointsValue < 0
    val canSave = name.isNotBlank() && !pointsInvalid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (template == null) "New template" else "Edit template",
                style = MaterialTheme.typography.titleLarge,
                color = colors.secondary,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Template name") }, placeholder = { Text("e.g. Chapter Reading") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Default title") }, placeholder = { Text("Prefilled assignment title") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
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
                value = points, onValueChange = { points = it },
                label = { Text("Points") }, placeholder = { Text("0") },
                singleLine = true, isError = pointsInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Default notes") }, minLines = 3, maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.secondary) }
                Button(
                    onClick = {
                        onSave(
                            buildJsonObject {
                                put("name", name.trim())
                                put("title", title.trim())
                                put("priority", priority.wire)
                                put("type", type.wire)
                                put("points", pointsValue ?: 0.0)
                                put("notes", notes)
                            },
                        )
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) { Text("Save") }
            }
        }
    }
}
