package education.studyspot.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.Grading
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.StatTile
import education.studyspot.app.ui.components.clean
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import java.time.Instant

enum class DashboardTab(val label: String) { Unfinished("Unfinished"), Completed("Completed"), Graded("Graded") }

/**
 * Dashboard (ios/StudySpot/Views/Dashboard/DashboardView.swift): stats,
 * Unfinished / Completed / Graded tabs, assignment rows (swipe right to
 * complete, left to delete), pull to refresh (syncs connected services).
 *
 * The pattern for feature screens: read StateFlows from
 * `LocalAppContainer.current.repository`, mutate through the repository in
 * `rememberCoroutineScope()`, navigate through [AppNavigator].
 *
 * TODO(dashboard): filters (class / due / priority / type), sort, select mode
 * with bulk edits (repository.bulkUpdate), sync button, welcome greeting.
 */
@Composable
fun DashboardScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    val repo = LocalAppContainer.current.repository
    val assignments by repo.assignments.collectAsStateWithLifecycle()
    val classes by repo.classes.collectAsStateWithLifecycle()
    val user by repo.user.collectAsStateWithLifecycle()
    val isLoading by repo.isLoading.collectAsStateWithLifecycle()
    val hasLoaded by repo.hasLoaded.collectAsStateWithLifecycle()
    val isRefreshing by repo.isRefreshing.collectAsStateWithLifecycle()
    val syncMessage by repo.syncMessage.collectAsStateWithLifecycle()
    val lastError by repo.lastError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(DashboardTab.Unfinished) }
    val colors = StudySpotTheme.colors

    val classColors = remember(classes) { classes.associate { it.id to parseHexColor(it.color) } }
    val uncategorized = parseHexColor(education.studyspot.app.model.SchoolClass.UNCATEGORIZED_COLOR)
    fun colorFor(a: Assignment) = a.classIdOrNull?.let(classColors::get) ?: uncategorized

    val now = Instant.now()
    val visible = remember(assignments, tab) {
        when (tab) {
            DashboardTab.Unfinished -> assignments.filter { !it.completed && !it.isGraded }
            DashboardTab.Completed -> assignments.filter { it.completed && !it.isGraded }
            DashboardTab.Graded -> assignments.filter { it.isGraded }
        }.let { list ->
            if (tab == DashboardTab.Graded) {
                list.sortedByDescending { (it.score ?: 0.0) / it.points }
            } else {
                list.sortedBy { it.dueDate ?: Instant.MAX }
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { repo.refresh() } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
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
                lastError?.let { err ->
                    item(key = "error") { Text(err, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
                }
                item(key = "tabs") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        DashboardTab.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = tab == t,
                                onClick = { tab = t },
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
                when {
                    isLoading && !hasLoaded -> item(key = "loading") { LoadingBox() }
                    visible.isEmpty() -> item(key = "empty") {
                        if (tab == DashboardTab.Graded) {
                            EmptyState(
                                Icons.Outlined.WorkspacePremium,
                                "No graded assignments yet",
                                message = "Open an assignment with points and enter a score to see its letter grade here.",
                            )
                        } else {
                            EmptyState(Icons.Outlined.Inbox, "No assignments here.")
                        }
                    }
                    tab == DashboardTab.Graded -> gradedItems(visible, user?.gradingScale.orEmpty(), navigator::openAssignment)
                    else -> items(visible, key = { it.id }) { a ->
                        SwipeableAssignmentRow(
                            assignment = a,
                            classColor = colorFor(a),
                            onToggleComplete = { scope.launch { repo.toggleComplete(a) } },
                            onDelete = { scope.launch { repo.deleteAssignment(a) } },
                            onClick = { navigator.openAssignment(a.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = navigator::newAssignment,
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add assignment") }
    }
}

/** Swipe right: complete / undo. Swipe left: delete. */
@Composable
private fun SwipeableAssignmentRow(
    assignment: Assignment,
    classColor: Color,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudySpotTheme.colors
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
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
        AssignmentRow(assignment, classColor, onToggleComplete = onToggleComplete, onClick = onClick)
    }
}

private fun LazyListScope.gradedItems(
    graded: List<Assignment>,
    scale: List<education.studyspot.app.model.GradeEntry>,
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

@Composable
private fun gradeColor(letter: String?): Color {
    val colors = StudySpotTheme.colors
    return when (letter?.firstOrNull()?.uppercaseChar()) {
        'A' -> colors.success
        'B' -> Color(0xFF0A84FF)
        'C' -> colors.dueToday
        'D', 'F' -> colors.danger
        else -> colors.mutedText
    }
}
