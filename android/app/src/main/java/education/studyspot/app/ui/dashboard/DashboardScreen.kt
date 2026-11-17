package education.studyspot.app.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.logic.AssignmentQuery
import education.studyspot.app.logic.DashboardTab
import education.studyspot.app.logic.DueFilter
import education.studyspot.app.logic.SortOrder
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.GradeEntry
import education.studyspot.app.model.Grading
import education.studyspot.app.model.Priority
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.components.DayPickerDialog
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.StatTile
import education.studyspot.app.ui.components.clean
import education.studyspot.app.ui.components.gradeColor
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate

/**
 * Dashboard (ios/StudySpot/Views/Dashboard/DashboardView.swift): stats,
 * Unfinished / Completed / Graded tabs, filters and sort, assignment rows
 * (swipe right to complete, left to delete; long-press to select), bulk
 * complete / reschedule / priority / delete, pull to refresh and a sync button
 * (when services are connected), and the once-per-launch welcome greeting.
 *
 * The pattern for feature screens: read StateFlows from
 * `LocalAppContainer.current.repository`, mutate through the repository in
 * `rememberCoroutineScope()`, navigate through [AppNavigator].
 */
@Composable
fun DashboardScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    val repo = LocalAppContainer.current.repository
    val assignments by repo.assignments.collectAsStateWithLifecycle()
    val classes by repo.classes.collectAsStateWithLifecycle()
    val user by repo.user.collectAsStateWithLifecycle()
    val connections by repo.connections.collectAsStateWithLifecycle()
    val isLoading by repo.isLoading.collectAsStateWithLifecycle()
    val hasLoaded by repo.hasLoaded.collectAsStateWithLifecycle()
    val isRefreshing by repo.isRefreshing.collectAsStateWithLifecycle()
    val isSyncing by repo.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by repo.syncMessage.collectAsStateWithLifecycle()
    val lastError by repo.lastError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors

    var tab by rememberSaveable { mutableStateOf(DashboardTab.Unfinished) }
    // Filters are saved individually so they survive configuration changes.
    var classFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var dueFilter by rememberSaveable { mutableStateOf(DueFilter.All) }
    var priorityFilter by rememberSaveable { mutableStateOf<Priority?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf<AssignmentType?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.Soonest) }
    val query = AssignmentQuery(classFilter, dueFilter, priorityFilter, typeFilter, sort)
    fun setQuery(q: AssignmentQuery) {
        classFilter = q.classId; dueFilter = q.due; priorityFilter = q.priority; typeFilter = q.type; sort = q.sort
    }

    var selectMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showFilters by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var pickBulkDate by remember { mutableStateOf(false) }
    val auth = LocalAppContainer.current.auth
    var greeting by remember { mutableStateOf(WelcomeGreeting.shouldShow(user, auth.hasGreetedThisLaunch)) }
    if (greeting) SideEffect { auth.hasGreetedThisLaunch = true }

    val classColors = remember(classes) { classes.associate { it.id to parseHexColor(it.color) } }
    val uncategorized = parseHexColor(SchoolClass.UNCATEGORIZED_COLOR)
    fun colorFor(a: Assignment) = a.classIdOrNull?.let(classColors::get) ?: uncategorized

    val now = Instant.now()
    val visible = remember(assignments, tab, query) {
        // Filters don't apply to the Graded tab (iOS hides the filter menu there).
        (if (tab == DashboardTab.Graded) AssignmentQuery() else query).apply(assignments, tab)
    }
    val selectedSet = selected.toSet()
    fun exitSelect() { selectMode = false; selected = emptyList() }
    fun toggleSelect(id: String) { selected = if (id in selectedSet) selected - id else selected + id }
    fun bulk(action: suspend (Set<String>) -> Unit) {
        val ids = selectedSet
        exitSelect()
        scope.launch { action(ids) }
    }

    BackHandler(enabled = selectMode) { exitSelect() }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { repo.refresh() } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "stats") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("Total", "${assignments.size}", Modifier.weight(1f))
                            StatTile("Overdue", "${assignments.count { it.isOverdue(now) }}", Modifier.weight(1f), tint = colors.danger)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("Due Today", "${assignments.count { it.isDueToday() }}", Modifier.weight(1f), tint = colors.dueToday)
                            StatTile("Completed", "${assignments.count { it.completed }}", Modifier.weight(1f), tint = colors.success)
                        }
                    }
                }
                if (syncMessage.isNotEmpty()) {
                    item(key = "sync") { Text(syncMessage, style = MaterialTheme.typography.bodySmall, color = colors.mutedText) }
                }
                item(key = "tabs") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        DashboardTab.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = tab == t,
                                onClick = { tab = t; selected = emptyList(); if (t == DashboardTab.Graded) selectMode = false },
                                shape = SegmentedButtonDefaults.itemShape(i, DashboardTab.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colors.accent.copy(alpha = 0.16f),
                                    activeContentColor = colors.secondary,
                                    inactiveContentColor = colors.secondary,
                                ),
                            ) { Text(t.label, maxLines = 1) }
                        }
                    }
                }
                if (tab != DashboardTab.Graded) {
                    item(key = "controls") {
                        ControlsRow(
                            query = query,
                            classes = classes,
                            selectMode = selectMode,
                            showSync = connections.any,
                            isSyncing = isSyncing,
                            onOpenFilters = { showFilters = true },
                            onQuery = ::setQuery,
                            onToggleSelect = { if (selectMode) exitSelect() else selectMode = true },
                            onSync = { scope.launch { repo.runSync(silent = false) } },
                        )
                    }
                }
                if (selectMode && visible.isNotEmpty()) {
                    item(key = "selectAll") {
                        val all = visible.all { it.id in selectedSet }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { selected = if (all) emptyList() else visible.map { it.id } }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (all) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = if (all) colors.accent else colors.mutedText,
                            )
                            Text(if (all) "All selected" else "Select all on this tab", color = colors.secondary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                when {
                    isLoading && !hasLoaded -> item(key = "loading") { LoadingBox() }
                    !hasLoaded && lastError != null -> item(key = "error") {
                        EmptyState(
                            Icons.Outlined.CloudOff,
                            "Couldn't load your assignments",
                            message = lastError,
                            action = {
                                Button(
                                    onClick = { scope.launch { repo.load() } },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                ) { Text("Try again") }
                            },
                        )
                    }
                    visible.isEmpty() -> item(key = "empty") {
                        when {
                            tab == DashboardTab.Graded -> EmptyState(
                                Icons.Outlined.WorkspacePremium,
                                "No graded assignments yet",
                                message = "Open an assignment with points and enter a score to see its letter grade here.",
                            )
                            query.filtersActive -> EmptyState(
                                Icons.Outlined.Inbox,
                                "No assignments match your filters.",
                                action = { TextButton(onClick = { setQuery(query.clearedFilters()) }) { Text("Clear filters") } },
                            )
                            assignments.isEmpty() -> EmptyState(
                                Icons.Outlined.Inbox,
                                "No assignments yet",
                                message = "Tap + to add one, or connect Google Classroom or Blackboard in Settings to import them.",
                            )
                            else -> EmptyState(
                                Icons.Outlined.Inbox,
                                if (tab == DashboardTab.Unfinished) "All caught up!" else "Nothing completed yet.",
                            )
                        }
                    }
                    tab == DashboardTab.Graded -> gradedItems(visible, user?.gradingScale.orEmpty(), navigator::openAssignment)
                    else -> items(visible, key = { it.id }) { a ->
                        SwipeableAssignmentRow(
                            assignment = a,
                            classColor = colorFor(a),
                            selectMode = selectMode,
                            selected = a.id in selectedSet,
                            onToggleComplete = { scope.launch { repo.toggleComplete(a) } },
                            onDelete = { scope.launch { repo.deleteAssignment(a) } },
                            onClick = { if (selectMode) toggleSelect(a.id) else navigator.openAssignment(a.id) },
                            onLongClick = { if (!selectMode) { selectMode = true; selected = listOf(a.id) } else toggleSelect(a.id) },
                            modifier = if (StudySpotTheme.animations) Modifier.animateItem() else Modifier,
                        )
                    }
                }
            }
        }

        if (!selectMode) {
            FloatingActionButton(
                onClick = navigator::newAssignment,
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Add assignment") }
        }

        AnimatedVisibility(
            visible = selectMode,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BulkBar(
                count = selectedSet.size,
                onComplete = { bulk { repo.bulkUpdate(it, buildJsonObject { put("completed", true) }) } },
                onIncomplete = { bulk { repo.bulkUpdate(it, buildJsonObject { put("completed", false) }) } },
                onReschedule = { pickBulkDate = true },
                onPriority = { p -> bulk { repo.bulkUpdate(it, buildJsonObject { put("priority", p.wire) }) } },
                onDelete = { confirmBulkDelete = true },
                onClose = ::exitSelect,
            )
        }

        user?.let { u -> if (greeting) WelcomeGreetingHost(u) { greeting = false } }
    }

    if (showFilters) {
        FilterSortSheet(query = query, classes = classes, onChange = ::setQuery, onDismiss = { showFilters = false })
    }
    if (pickBulkDate) {
        DayPickerDialog(
            initial = LocalDate.now(),
            title = "Move ${selectedSet.size} to…",
            confirmLabel = "Move",
            onDismiss = { pickBulkDate = false },
            onPick = { day -> bulk { repo.bulkReschedule(it, day) } },
        )
    }
    if (confirmBulkDelete) {
        val n = selectedSet.size
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text("Delete $n assignment${if (n == 1) "" else "s"}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmBulkDelete = false; bulk { repo.bulkDelete(it) } }) {
                    Text("Delete", color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("Cancel", color = colors.secondary) } },
        )
    }
}

/** Filter & sort button, removable chips for active filters, select and sync buttons. */
@Composable
private fun ControlsRow(
    query: AssignmentQuery,
    classes: List<SchoolClass>,
    selectMode: Boolean,
    showSync: Boolean,
    isSyncing: Boolean,
    onOpenFilters: () -> Unit,
    onQuery: (AssignmentQuery) -> Unit,
    onToggleSelect: () -> Unit,
    onSync: () -> Unit,
) {
    val colors = StudySpotTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = onOpenFilters,
                label = { Text(if (query.sort == SortOrder.Soonest) "Filter & sort" else query.sort.label) },
                leadingIcon = {
                    BadgedBox(badge = { if (query.activeFilterCount > 0) Badge { Text("${query.activeFilterCount}") } }) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                    }
                },
                colors = AssistChipDefaults.assistChipColors(labelColor = colors.secondary, leadingIconContentColor = colors.secondary),
            )
            query.summaryLabels(classes).forEach { (label, without) ->
                InputChip(
                    selected = true,
                    onClick = { onQuery(without) },
                    label = { Text(label, maxLines = 1) },
                    trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Remove filter $label", modifier = Modifier.size(InputChipDefaults.IconSize)) },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = colors.secondaryTint,
                        selectedLabelColor = colors.secondary,
                        selectedTrailingIconColor = colors.secondary,
                    ),
                )
            }
        }
        IconButton(onClick = onToggleSelect) {
            Icon(
                if (selectMode) Icons.Outlined.Close else Icons.Outlined.Checklist,
                contentDescription = if (selectMode) "Done selecting" else "Select",
                tint = colors.secondary,
            )
        }
        if (showSync) {
            IconButton(onClick = onSync, enabled = !isSyncing) {
                if (isSyncing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Sync, contentDescription = "Sync now", tint = colors.secondary)
                }
            }
        }
    }
}

@Composable
private fun BulkBar(
    count: Int,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit,
    onReschedule: () -> Unit,
    onPriority: (Priority) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = StudySpotTheme.colors
    val enabled = count > 0
    var priorityMenu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = colors.secondary,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(12.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onComplete, enabled = enabled) { Icon(Icons.Outlined.Check, "Mark complete", tint = if (enabled) colors.success else colors.mutedText) }
            IconButton(onClick = onIncomplete, enabled = enabled) { Icon(Icons.AutoMirrored.Outlined.Undo, "Mark incomplete") }
            IconButton(onClick = onReschedule, enabled = enabled) { Icon(Icons.Outlined.EditCalendar, "Reschedule") }
            Box {
                IconButton(onClick = { priorityMenu = true }, enabled = enabled) { Icon(Icons.Outlined.Flag, "Set priority") }
                DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                    Priority.entries.reversed().forEach { p ->
                        DropdownMenuItem(text = { Text("${p.label} priority") }, onClick = { priorityMenu = false; onPriority(p) })
                    }
                }
            }
            IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Outlined.Delete, "Delete", tint = if (enabled) colors.danger else colors.mutedText) }
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Done selecting") }
        }
    }
}

/** Swipe right: complete / undo. Swipe left: delete. Disabled in select mode. */
@Composable
private fun SwipeableAssignmentRow(
    assignment: Assignment,
    classColor: Color,
    selectMode: Boolean,
    selected: Boolean,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudySpotTheme.colors
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = !selectMode,
        enableDismissFromEndToStart = !selectMode,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleComplete()
                    scope.launch { state.reset() } // snap back
                }
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            val toStart = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val bg = if (toStart) colors.danger else colors.success
            Row(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(bg).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toStart) Arrangement.End else Arrangement.Start,
            ) {
                val icon = when {
                    toStart -> Icons.Outlined.Delete
                    assignment.completed -> Icons.AutoMirrored.Outlined.Undo
                    else -> Icons.Outlined.Check
                }
                val label = when {
                    toStart -> "Delete"
                    assignment.completed -> "Undo"
                    else -> "Done"
                }
                Icon(icon, contentDescription = label, tint = Color.White)
            }
        },
    ) {
        AssignmentRow(
            assignment,
            classColor,
            onToggleComplete = onToggleComplete,
            onClick = onClick,
            selectMode = selectMode,
            selected = selected,
            onLongClick = onLongClick,
        )
    }
}

private fun LazyListScope.gradedItems(
    graded: List<Assignment>,
    scale: List<GradeEntry>,
    onOpen: (String) -> Unit,
) {
    items(graded, key = { it.id }) { a ->
        val colors = StudySpotTheme.colors
        val letter = Grading.letter(a.score, a.points, scale)
        val letterColor = gradeColor(letter)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surface)
                .clickable { onOpen(a.id) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(width = 44.dp, height = 36.dp).clip(MaterialTheme.shapes.small).background(letterColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Text(letter ?: "–", color = letterColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) {
                Text(a.displayTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    "${a.className ?: "Uncategorized"} · ${a.dueDate?.let { DateText.date(it) } ?: "No due date"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${(a.score ?: 0.0).clean}/${a.points.clean}", style = MaterialTheme.typography.titleSmall)
                Text("${Grading.percent(a) ?: 0}%", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
            }
        }
    }
}
