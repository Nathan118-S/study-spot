package education.studyspot.app.ui.classes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.model.ClassMeeting
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.Source
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.components.TimePickerDialog
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.components.short
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Class color palette (ios ClassFormView.colors). */
val ClassColors = listOf("#6366f1", "#ec4899", "#f59e0b", "#10b981", "#06b6d4", "#8b5cf6", "#ef4444", "#14b8a6", "#f97316", "#0ea5e9")

/**
 * Classes tab (ios/StudySpot/Views/Classes/ClassesView.swift): classes with
 * color, details, next meeting, assignment count and source; add / edit /
 * delete with name, color, subject, room, teacher and weekly meeting times.
 */
@Composable
fun ClassesScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    val c = LocalAppContainer.current
    val repo = c.repository
    val classes by repo.classes.collectAsStateWithLifecycle()
    val assignments by repo.assignments.collectAsStateWithLifecycle()
    val isLoading by repo.isLoading.collectAsStateWithLifecycle()
    val hasLoaded by repo.hasLoaded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    var refreshing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SchoolClass?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SchoolClass?>(null) }
    val counts = remember(assignments) { assignments.groupingBy { it.classIdOrNull }.eachCount() }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; repo.reloadData(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    isLoading && !hasLoaded -> item { LoadingBox() }
                    classes.isEmpty() -> item {
                        EmptyState(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            "No classes yet",
                            message = "Add one or connect Google Classroom or Blackboard in Settings.",
                            action = {
                                Button(
                                    onClick = { creating = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                ) { Text("Add a class") }
                            },
                        )
                    }
                    else -> {
                        items(classes, key = { it.id }) { cls ->
                            ClassCard(
                                cls,
                                assignmentCount = counts[cls.id] ?: 0,
                                onEdit = { editing = cls },
                                onDelete = { confirmDelete = cls },
                                modifier = if (StudySpotTheme.animations) Modifier.animateItem() else Modifier,
                            )
                        }
                        item(key = "footer") {
                            Text(
                                "Google Classroom and Blackboard classes are imported automatically when connected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.mutedText,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        // The empty state has its own "Add a class" button; don't show two.
        if (classes.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add class") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (creating || editing != null) {
        val target = editing
        ClassFormSheet(
            schoolClass = target,
            onDismiss = { creating = false; editing = null },
            onSave = { fields ->
                scope.launch {
                    if (repo.user.value?.isDemo == true) {
                        c.messages.show("Demo account", "Changes aren't saved in the demo.")
                        creating = false; editing = null
                        return@launch
                    }
                    val saved = if (target == null) {
                        repo.createClass(JsonObject(fields + ("source" to JsonPrimitive("manual"))))
                    } else {
                        repo.updateClass(target.id, fields)
                    }
                    if (saved != null) { creating = false; editing = null }
                }
            },
        )
    }

    confirmDelete?.let { cls ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${cls.displayName}?") },
            text = {
                Text(
                    if (cls.source == Source.Manual) "The class is removed. Its assignments stay, without a class."
                    else "The class is removed. It may be imported again on the next sync while ${cls.source.label} is connected.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; scope.launch { repo.deleteClass(cls) } }) { Text("Delete", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = colors.secondary) } },
        )
    }
}

@Composable
private fun ClassCard(cls: SchoolClass, assignmentCount: Int, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StudySpotTheme.colors
    var menu by remember { mutableStateOf(false) }
    val next = remember(cls.schedule) { cls.schedule.mapNotNull { it.nextStart() }.minOrNull() }
    Card(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, colors.border),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).size(14.dp).clip(CircleShape).background(parseHexColor(cls.color)))
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(cls.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (cls.source != Source.Manual) Pill(if (cls.source.isGoogle) "Google" else cls.source.label)
                }
                val details = listOfNotNull(
                    cls.subject?.takeIf { it.isNotBlank() },
                    cls.room?.takeIf { it.isNotBlank() }?.let { "Room $it" },
                    cls.teacherName?.takeIf { it.isNotBlank() },
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
                }
                if (cls.schedule.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(14.dp))
                        Text(
                            meetingSummary(cls.schedule) + (next?.let { " · next ${formatNext(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedText,
                        )
                    }
                }
                Text(
                    "$assignmentCount assignment${if (assignmentCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More for ${cls.displayName}", tint = colors.secondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.danger) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = colors.danger) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** "Mon, Wed 9:00 AM" style summary; groups days that share a start time. */
private fun meetingSummary(schedule: List<ClassMeeting>): String =
    schedule.sortedWith(compareBy({ it.startTime }, { it.day }))
        .groupBy { it.startTime }
        .entries.joinToString("; ") { (time, ms) ->
            ms.sortedBy { it.day }.joinToString(", ") { ClassMeeting.weekdayShort.getOrElse(it.day) { "?" } } +
                (time?.let { " ${it.short()}" } ?: "")
        }

private fun formatNext(t: ZonedDateTime): String =
    t.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())) + " " + t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** A meeting being edited (times as LocalTime for the pickers). */
private data class EditableMeeting(val day: Int, val start: LocalTime, val end: LocalTime) {
    val valid: Boolean get() = end > start
}

private val meetingsSaver = listSaver<List<EditableMeeting>, String>(
    save = { list -> list.map { "${it.day}|${hhmm(it.start)}|${hhmm(it.end)}" } },
    restore = { saved ->
        saved.mapNotNull { s ->
            val p = s.split("|")
            val start = ClassMeeting.parse(p.getOrNull(1)) ?: return@mapNotNull null
            EditableMeeting(p[0].toIntOrNull() ?: 1, start, ClassMeeting.parse(p.getOrNull(2)) ?: start.plusMinutes(50))
        }
    },
)

private fun hhmm(t: LocalTime) = "%02d:%02d".format(Locale.US, t.hour, t.minute)

/** The add / edit class form (ios ClassFormView). [onSave] gets the server fields. */
@Composable
internal fun ClassFormSheet(schoolClass: SchoolClass?, onDismiss: () -> Unit, onSave: (JsonObject) -> Unit) {
    val colors = StudySpotTheme.colors
    val key = schoolClass?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(schoolClass?.name.orEmpty()) }
    var color by rememberSaveable(key) { mutableStateOf(schoolClass?.color ?: ClassColors.first()) }
    var subject by rememberSaveable(key) { mutableStateOf(schoolClass?.subject.orEmpty()) }
    var room by rememberSaveable(key) { mutableStateOf(schoolClass?.room.orEmpty()) }
    var teacher by rememberSaveable(key) { mutableStateOf(schoolClass?.teacherName.orEmpty()) }
    var meetings by rememberSaveable(key, stateSaver = meetingsSaver) {
        mutableStateOf(
            schoolClass?.schedule.orEmpty().mapNotNull { m ->
                val start = m.startTime ?: return@mapNotNull null
                EditableMeeting(m.day.coerceIn(0, 6), start, m.endTime ?: start.plusMinutes(50))
            },
        )
    }
    var picking by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // (meeting index, isStart)
    val canSave = name.isNotBlank() && meetings.all { it.valid }

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
            Text(if (schoolClass == null) "Add class" else "Edit class", style = MaterialTheme.typography.titleLarge, color = colors.secondary)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Class name") }, placeholder = { Text("e.g. AP Calculus") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            SheetLabel("Color")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                (ClassColors + listOfNotNull(color.takeIf { it !in ClassColors })).forEach { hex ->
                    val isSelected = hex.equals(color, ignoreCase = true)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(if (isSelected) 3.dp else 0.dp, colors.secondary, CircleShape)
                            .padding(if (isSelected) 5.dp else 0.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(hex))
                            .clickable(role = Role.RadioButton) { color = hex }
                            .semantics {
                                contentDescription = "Color $hex"
                                selected = isSelected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            SheetLabel("Details (optional)")
            OutlinedTextField(
                value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, placeholder = { Text("e.g. Math") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            OutlinedTextField(
                value = room, onValueChange = { room = it }, label = { Text("Room") }, placeholder = { Text("e.g. 204") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = teacher, onValueChange = { teacher = it }, label = { Text("Teacher") }, placeholder = { Text("e.g. Mr. Smith") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            SheetLabel("Meeting times")
            meetings.forEachIndexed { i, m ->
                MeetingRow(
                    m,
                    onDay = { d -> meetings = meetings.mapIndexed { j, x -> if (j == i) x.copy(day = d) else x } },
                    onPickStart = { picking = i to true },
                    onPickEnd = { picking = i to false },
                    onRemove = { meetings = meetings.filterIndexed { j, _ -> j != i } },
                )
            }
            TextButton(onClick = {
                // Same time as the last meeting, on the next day (like iOS).
                val last = meetings.lastOrNull()
                meetings = meetings + EditableMeeting(
                    day = last?.let { (it.day + 1) % 7 } ?: 1,
                    start = last?.start ?: LocalTime.of(9, 0),
                    end = last?.end ?: LocalTime.of(9, 50),
                )
            }) {
                Icon(Icons.Outlined.AddCircle, contentDescription = null, tint = colors.accent)
                Text("  Add meeting time", color = colors.accent)
            }
            Text(
                "Study Spot shows a countdown notification 10 minutes before each class.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.secondary) }
                Button(
                    onClick = {
                        val sorted = meetings.sortedWith(compareBy({ it.day }, { it.start }))
                        onSave(
                            buildJsonObject {
                                put("name", name.trim())
                                put("color", color)
                                put("teacher_name", teacher.trim())
                                put("subject", subject.trim())
                                put("room", room.trim())
                                put(
                                    "schedule",
                                    buildJsonArray {
                                        sorted.forEach { m ->
                                            add(buildJsonObject { put("day", m.day); put("start", hhmm(m.start)); put("end", hhmm(m.end)) })
                                        }
                                    },
                                )
                            },
                        )
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) { Text("Save") }
            }
        }
    }

    picking?.let { (i, isStart) ->
        val m = meetings.getOrNull(i)
        if (m == null) {
            picking = null
        } else {
            TimePickerDialog(
                initial = if (isStart) m.start else m.end,
                title = if (isStart) "Starts" else "Ends",
                onDismiss = { picking = null },
                onPick = { t ->
                    meetings = meetings.mapIndexed { j, x ->
                        when {
                            j != i -> x
                            // Moving the start keeps the meeting's length.
                            isStart -> {
                                val length = Duration.between(x.start, x.end)
                                val end = t.plus(length)
                                x.copy(start = t, end = if (x.valid && end > t) end else x.end)
                            }
                            else -> x.copy(end = t)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = StudySpotTheme.colors.secondary)
}

@Composable
private fun MeetingRow(m: EditableMeeting, onDay: (Int) -> Unit, onPickStart: () -> Unit, onPickEnd: () -> Unit, onRemove: () -> Unit) {
    val colors = StudySpotTheme.colors
    var dayMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.secondaryTint.copy(alpha = 0.6f))
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { dayMenu = true }) {
                    Text(ClassMeeting.weekdayNames.getOrElse(m.day) { "Day" }, color = colors.secondary, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                    ClassMeeting.weekdayNames.forEachIndexed { d, label ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { dayMenu = false; onDay(d) })
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove ${ClassMeeting.weekdayNames.getOrElse(m.day) { "" }} meeting", tint = colors.mutedText)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
            OutlinedButton(onClick = onPickStart, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(m.start.short(), color = colors.secondary, maxLines = 1)
            }
            Text("to", color = colors.mutedText)
            OutlinedButton(
                onClick = onPickEnd,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
                border = if (m.valid) ButtonDefaults.outlinedButtonBorder(enabled = true) else BorderStroke(1.dp, colors.danger),
            ) {
                Text(m.end.short(), color = if (m.valid) colors.secondary else colors.danger, maxLines = 1)
            }
        }
        if (!m.valid) {
            Text("Ends before it starts.", style = MaterialTheme.typography.bodySmall, color = colors.danger, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
