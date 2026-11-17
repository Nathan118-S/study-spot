package education.studyspot.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.Source
import education.studyspot.app.ui.components.ColorDot
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.theme.StudySpotTheme

/**
 * One assignment: class color bar, completion checkbox, title with source /
 * overdue pills, then class · type · due date. Reused by other lists (calendar,
 * class detail).
 *
 * In [selectMode] a selection circle leads the row and taps toggle selection
 * (the caller decides what [onClick] does). [onLongClick] enters select mode on
 * the dashboard.
 */
@Composable
fun AssignmentRow(
    assignment: Assignment,
    classColor: Color,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val a = assignment
    val colors = StudySpotTheme.colors
    val overdue = a.isOverdue
    val base = when {
        selected -> colors.accent.copy(alpha = 0.14f).compositeOver(colors.surface)
        overdue -> colors.danger.copy(alpha = 0.08f).compositeOver(colors.surface)
        else -> colors.surface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(base)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) "Select" else null,
            )
            .height(IntrinsicSize.Min)
            .padding(end = 12.dp)
            .semantics {
                if (overdue) stateDescription = "Overdue"
                if (selectMode) this.selected = selected
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectMode) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.mutedText,
                modifier = Modifier.padding(start = 12.dp).size(22.dp),
            )
        }
        Box(
            Modifier
                .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(classColor),
        )
        Checkbox(
            checked = a.completed,
            onCheckedChange = if (selectMode) null else { _ -> onToggleComplete() },
            modifier = Modifier.semantics { contentDescription = if (a.completed) "Mark incomplete" else "Mark complete" },
            colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.mutedText),
        )
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Pills wrap under the title instead of squeezing it onto two lines.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    a.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (a.completed) colors.mutedText else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (a.completed) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (a.source != Source.Manual) Pill(a.source.label)
                if (overdue) Pill("Overdue", color = colors.danger, filled = true, icon = Icons.Filled.Warning)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val meta = MaterialTheme.typography.bodySmall
                // Class · type truncate only when the date needs the room.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ColorDot(classColor, size = 7.dp)
                    Text(
                        "${a.className?.takeIf { it.isNotBlank() } ?: "Uncategorized"} · ${a.type.label}",
                        style = meta,
                        color = colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("·", style = meta, color = colors.mutedText)
                    Text(DateText.short(a.dueDate), style = meta, color = if (overdue) colors.danger else colors.mutedText, maxLines = 1)
                }
                if (a.progress > 0) {
                    Text(
                        if (a.progress >= 100) "✓" else "${a.progress.toInt()}%",
                        style = meta,
                        fontWeight = FontWeight.Medium,
                        color = colors.mutedText,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
