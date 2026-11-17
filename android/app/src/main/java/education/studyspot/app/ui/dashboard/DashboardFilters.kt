package education.studyspot.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import education.studyspot.app.logic.AssignmentQuery
import education.studyspot.app.logic.DueFilter
import education.studyspot.app.logic.SortOrder
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Priority
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.ui.components.ColorDot
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.theme.StudySpotTheme

/** The dashboard's "Filter & sort" sheet (the iOS filter menu): class, due, priority, type, sort. */
@Composable
fun FilterSortSheet(
    query: AssignmentQuery,
    classes: List<SchoolClass>,
    onChange: (AssignmentQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filter & sort",
                    style = MaterialTheme.typography.titleLarge,
                    color = StudySpotTheme.colors.secondary,
                    modifier = Modifier.weight(1f),
                )
                if (query.filtersActive) {
                    TextButton(onClick = { onChange(query.clearedFilters()) }) { Text("Clear filters") }
                }
            }
            ChipGroup("Class") {
                Choice("All classes", query.classId == null) { onChange(query.copy(classId = null)) }
                classes.forEach { c ->
                    Choice(c.displayName, query.classId == c.id, dot = c.color) { onChange(query.copy(classId = c.id)) }
                }
            }
            ChipGroup("Due") {
                DueFilter.entries.forEach { d -> Choice(d.label, query.due == d) { onChange(query.copy(due = d)) } }
            }
            ChipGroup("Priority") {
                Choice("All priorities", query.priority == null) { onChange(query.copy(priority = null)) }
                Priority.entries.reversed().forEach { p -> Choice(p.label, query.priority == p) { onChange(query.copy(priority = p)) } }
            }
            ChipGroup("Type") {
                Choice("All types", query.type == null) { onChange(query.copy(type = null)) }
                AssignmentType.entries.forEach { t -> Choice(t.label, query.type == t) { onChange(query.copy(type = t)) } }
            }
            ChipGroup("Sort by") {
                SortOrder.entries.forEach { s -> Choice(s.label, query.sort == s) { onChange(query.copy(sort = s)) } }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ChipGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = StudySpotTheme.colors.secondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, dot: String? = null, onClick: () -> Unit) {
    val colors = StudySpotTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = when {
            selected -> { { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } }
            dot != null -> { { ColorDot(parseHexColor(dot), size = 10.dp) } }
            else -> null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.accent.copy(alpha = 0.16f),
            selectedLabelColor = colors.secondary,
            selectedLeadingIconColor = colors.accent,
            labelColor = colors.secondary,
        ),
    )
}

/** Short description of the active filters for the dashboard's chip row. */
fun AssignmentQuery.summaryLabels(classes: List<SchoolClass>): List<Pair<String, AssignmentQuery>> = buildList {
    classId?.let { id -> add((classes.firstOrNull { it.id == id }?.displayName ?: "Class") to copy(classId = null)) }
    if (due != DueFilter.All) add(due.label to copy(due = DueFilter.All))
    priority?.let { add("${it.label} priority" to copy(priority = null)) }
    type?.let { add(it.label to copy(type = null)) }
}
