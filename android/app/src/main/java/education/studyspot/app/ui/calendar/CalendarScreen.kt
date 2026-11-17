package education.studyspot.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.logic.CalendarMath
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.components.currentLocale
import education.studyspot.app.ui.components.DayPickerDialog
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.components.priorityColor
import education.studyspot.app.ui.components.time
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * Calendar tab (ios/StudySpot/Views/Calendar/CalendarScreen.swift): a month
 * grid of assignments on their due dates, today highlighted; tap a day to list
 * its items. Long-press an assignment (in the grid or the day list) and drag it
 * onto another day to reschedule it, keeping its time of day. Long-press and
 * release without dragging opens a menu with "Move to…" (a date picker).
 */
@Composable
fun CalendarScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    val repo = LocalAppContainer.current.repository
    val assignments by repo.assignments.collectAsStateWithLifecycle()
    val classes by repo.classes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by rememberSaveable { mutableStateOf(today) }
    var refreshing by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Assignment?>(null) }
    val drag = remember { CalendarDragState() }

    val byDay = remember(assignments) { CalendarMath.byDay(assignments, zone) }
    val grid = remember(month) { CalendarMath.monthGrid(month) }
    val classColors = remember(classes) { classes.associate { it.id to parseHexColor(it.color) } }
    val uncategorized = parseHexColor(SchoolClass.UNCATEGORIZED_COLOR)
    fun colorFor(a: Assignment) = a.classIdOrNull?.let(classColors::get) ?: uncategorized

    fun reschedule(a: Assignment, day: LocalDate) {
        selectedDay = day
        if (YearMonth.from(day) != month) month = YearMonth.from(day)
        scope.launch { repo.reschedule(a.id, day) }
    }

    val actions = ItemActions(
        open = { navigator.openAssignment(it.id) },
        toggle = { a -> scope.launch { repo.toggleComplete(a) } },
        moveTo = { moving = it },
        drop = ::reschedule,
    )

    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .onGloballyPositioned { drag.containerOrigin = it.positionInRoot() },
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; repo.reloadData(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MonthHeader(
                    month = month,
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onToday = { month = YearMonth.from(today); selectedDay = today },
                    showToday = month != YearMonth.from(today) || selectedDay != today,
                )
                MonthGrid(
                    grid = grid,
                    month = month,
                    today = today,
                    selected = selectedDay,
                    byDay = byDay,
                    drag = drag,
                    colorFor = ::colorFor,
                    actions = actions,
                    onSelect = { selectedDay = it },
                )
                Text(
                    "Tip: long-press an assignment and drag it to another day to reschedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                )
                DayList(
                    day = selectedDay,
                    items = byDay[selectedDay].orEmpty(),
                    drag = drag,
                    colorFor = ::colorFor,
                    actions = actions,
                )
            }
        }
        DragOverlay(drag, colorFor = ::colorFor)
    }

    moving?.let { a ->
        DayPickerDialog(
            initial = a.dueDate?.atZone(zone)?.toLocalDate() ?: selectedDay,
            title = "Move “${a.displayTitle}” to…",
            confirmLabel = "Move",
            onDismiss = { moving = null },
            onPick = { day -> reschedule(a, day) },
        )
    }
}

/** What an assignment in the grid or day list can do. */
private class ItemActions(
    val open: (Assignment) -> Unit,
    val toggle: (Assignment) -> Unit,
    val moveTo: (Assignment) -> Unit,
    val drop: (Assignment, LocalDate) -> Unit,
)

/** Shared state for long-press drag and drop between days. */
@Stable
private class CalendarDragState {
    var dragging by mutableStateOf<Assignment?>(null)
        private set
    /** Pointer position in root coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var hover by mutableStateOf<LocalDate?>(null)
        private set
    var containerOrigin = Offset.Zero
    private var travelled = 0f
    /** Day cells' bounds in root coordinates (kept fresh while scrolling). */
    val cells = mutableMapOf<LocalDate, Rect>()

    fun start(a: Assignment, at: Offset) {
        dragging = a; pointer = at; travelled = 0f; hover = dayAt(at)
    }

    fun move(by: Offset) {
        pointer += by
        travelled += by.getDistance()
        hover = dayAt(pointer)
    }

    /** Ends the drag: the day it was dropped on, and whether the pointer moved at all. */
    fun end(): Pair<LocalDate?, Boolean> {
        val result = hover to (travelled > 24f)
        cancel()
        return result
    }

    fun cancel() { dragging = null; hover = null; travelled = 0f }

    private fun dayAt(p: Offset) = cells.entries.firstOrNull { it.value.contains(p) }?.key
}

/**
 * Wraps an assignment so a tap runs [onTap] and a long-press starts dragging it;
 * dropping it on another day reschedules it, and releasing without moving opens
 * [onMenu]. Tap handling lives here (not in a clickable child) so a long-press
 * never also counts as a tap: the drag detector consumes the release, and the
 * outer tap detector then ignores it.
 */
@Composable
private fun DragSource(
    a: Assignment,
    drag: CalendarDragState,
    actions: ItemActions,
    onTap: () -> Unit,
    tapLabel: String,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(a)
    val currentMenu by rememberUpdatedState(onMenu)
    val currentTap by rememberUpdatedState(onTap)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val zone = ZoneId.systemDefault()
    Box(
        modifier
            .onGloballyPositioned { coords[0] = it }
            .semantics {
                onClick(label = tapLabel) { currentTap(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Move to another day") { actions.moveTo(current); true },
                    CustomAccessibilityAction(if (current.completed) "Mark incomplete" else "Mark complete") {
                        actions.toggle(current); true
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { currentTap() }) }
            .pointerInput(a.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val root = coords[0]?.takeIf { it.isAttached }?.localToRoot(offset) ?: offset
                        drag.start(current, root)
                    },
                    onDrag = { change, amount -> change.consume(); drag.move(amount) },
                    onDragEnd = {
                        val (day, moved) = drag.end()
                        val from = current.dueDate?.atZone(zone)?.toLocalDate()
                        when {
                            day != null && day != from -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                actions.drop(current, day)
                            }
                            !moved -> currentMenu()
                        }
                    },
                    onDragCancel = { drag.cancel() },
                )
            },
    ) { content() }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, showToday: Boolean) {
    val colors = StudySpotTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = colors.secondary) }
        Text(
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", currentLocale())),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.secondary,
            modifier = Modifier.weight(1f).semantics { heading() },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = colors.secondary) }
        TextButton(onClick = onToday, enabled = showToday) { Text("Today") }
    }
}

@Composable
private fun MonthGrid(
    grid: List<LocalDate>,
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<Assignment>>,
    drag: CalendarDragState,
    colorFor: (Assignment) -> Color,
    actions: ItemActions,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = StudySpotTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            // Sunday-first, like iOS.
            (0..6).forEach { i ->
                val dow = java.time.DayOfWeek.SUNDAY.plus(i.toLong())
                Text(
                    dow.getDisplayName(TextStyle.SHORT, currentLocale()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.mutedText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        grid.chunked(7).forEach { week ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        inMonth = YearMonth.from(day) == month,
                        isToday = day == today,
                        isSelected = day == selected,
                        isDropTarget = drag.dragging != null && drag.hover == day,
                        items = byDay[day].orEmpty(),
                        drag = drag,
                        colorFor = colorFor,
                        actions = actions,
                        onSelect = { onSelect(day) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isDropTarget: Boolean,
    items: List<Assignment>,
    drag: CalendarDragState,
    colorFor: (Assignment) -> Color,
    actions: ItemActions,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudySpotTheme.colors
    DisposableEffect(day) { onDispose { drag.cells.remove(day) } }
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        isDropTarget -> colors.accent
        isSelected -> colors.accent
        isToday -> colors.accent.copy(alpha = 0.6f)
        else -> colors.border
    }
    val label = day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", currentLocale())) +
        if (items.isEmpty()) ", nothing due" else ", ${items.size} due"
    Column(
        modifier
            .heightIn(min = 76.dp)
            .onGloballyPositioned { drag.cells[day] = it.boundsInRoot() }
            .clip(shape)
            .background(
                when {
                    isDropTarget -> colors.accent.copy(alpha = 0.18f)
                    isSelected -> colors.accent.copy(alpha = 0.08f)
                    else -> colors.surface
                },
            )
            .border(if (isSelected || isDropTarget) 2.dp else if (isToday) 1.dp else 0.5.dp, borderColor, shape)
            .clickable(onClick = onSelect)
            .semantics(mergeDescendants = false) {
                contentDescription = label
                selected = isSelected
            }
            .padding(3.dp)
            .alpha(if (inMonth) 1f else 0.55f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isToday) colors.accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                fontSize = 11.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> colors.onAccent
                    !inMonth -> colors.mutedText.copy(alpha = 0.5f)
                    else -> colors.text
                },
            )
        }
        items.take(3).forEach { a ->
            var menu by remember { mutableStateOf(false) }
            DragSource(a, drag, actions, onTap = onSelect, tapLabel = "Show day", onMenu = { menu = true }) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(colorFor(a).copy(alpha = if (inMonth) 0.2f else 0.1f))
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(priorityColor(a.priority)))
                    Text(
                        a.displayTitle,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        color = if (a.completed) colors.mutedText else colors.text,
                        textDecoration = if (a.completed) TextDecoration.LineThrough else null,
                    )
                }
                ItemMenu(a, expanded = menu, onDismiss = { menu = false }, actions = actions)
            }
        }
        if (items.size > 3) {
            Text("+${items.size - 3} more", fontSize = 9.sp, color = colors.mutedText, maxLines = 1)
        }
    }
}

@Composable
private fun DayList(
    day: LocalDate,
    items: List<Assignment>,
    drag: CalendarDragState,
    colorFor: (Assignment) -> Color,
    actions: ItemActions,
) {
    val colors = StudySpotTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", currentLocale())),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.secondary,
            modifier = Modifier.semantics { heading() },
        )
        if (items.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(18.dp))
                Text("Nothing due.", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
            }
        }
        items.forEach { a ->
            var menu by remember(a.id) { mutableStateOf(false) }
            DragSource(a, drag, actions, onTap = { actions.open(a) }, tapLabel = "Open", onMenu = { menu = true }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorFor(a).copy(alpha = 0.08f).compositeOverSurface()),
                    border = BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.height(IntrinsicSize.Min).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(colorFor(a)))
                        Box(Modifier.padding(start = 10.dp).size(8.dp).clip(CircleShape).background(priorityColor(a.priority)))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                a.displayTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                                color = if (a.completed) colors.mutedText else colors.text,
                                textDecoration = if (a.completed) TextDecoration.LineThrough else null,
                            )
                            Text(
                                "${a.className?.takeIf { it.isNotBlank() } ?: "Uncategorized"} · ${DateText.time(a.dueDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (a.isOverdue) colors.danger else colors.mutedText,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = { actions.moveTo(a) }) {
                            Icon(Icons.Outlined.EditCalendar, contentDescription = "Move ${a.displayTitle} to another day", tint = colors.secondary)
                        }
                    }
                }
                ItemMenu(a, expanded = menu, onDismiss = { menu = false }, actions = actions)
            }
        }
    }
}

@Composable
private fun Color.compositeOverSurface(): Color = androidx.compose.ui.graphics.lerp(StudySpotTheme.colors.surface, copy(alpha = 1f), alpha)

@Composable
private fun ItemMenu(a: Assignment, expanded: Boolean, onDismiss: () -> Unit, actions: ItemActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Open") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
            onClick = { onDismiss(); actions.open(a) },
        )
        DropdownMenuItem(
            text = { Text("Move to…") },
            leadingIcon = { Icon(Icons.Outlined.EditCalendar, null) },
            onClick = { onDismiss(); actions.moveTo(a) },
        )
        DropdownMenuItem(
            text = { Text(if (a.completed) "Mark incomplete" else "Mark complete") },
            leadingIcon = { Icon(if (a.completed) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.Check, null) },
            onClick = { onDismiss(); actions.toggle(a) },
        )
    }
}

/** The chip that follows the finger while dragging. */
@Composable
private fun DragOverlay(drag: CalendarDragState, colorFor: (Assignment) -> Color) {
    val a = drag.dragging ?: return
    val colors = StudySpotTheme.colors
    val density = LocalDensity.current
    val lift = with(density) { 36.dp.toPx() }
    val half = with(density) { 70.dp.toPx() }
    val p = drag.pointer - drag.containerOrigin
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colorFor(a)),
        modifier = Modifier
            .offset { IntOffset((p.x - half).roundToInt(), (p.y - lift * 1.6f).roundToInt()) }
            .widthIn(max = 140.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(a.displayTitle, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                drag.hover?.format(DateTimeFormatter.ofPattern("EEE, MMM d", currentLocale())) ?: "Drop on a day",
                style = MaterialTheme.typography.labelSmall,
                color = if (drag.hover != null) colors.accent else colors.mutedText,
            )
        }
    }
}
