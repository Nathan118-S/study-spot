package education.studyspot.app.ui.timer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.liveupdates.ClassAlarmScheduler
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.push.NotificationPermission
import education.studyspot.app.push.Notifier
import education.studyspot.app.timer.TimerController
import education.studyspot.app.timer.TimerPhase
import education.studyspot.app.timer.TimerState
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Study timer (ios/StudySpot/Views/Dashboard/StudyTimerView.swift): a
 * Pomodoro with focus / short / long phases, custom lengths, an optional
 * assignment, and start / pause / resume / reset / skip.
 *
 * The timer itself lives in [TimerController] (and a foreground service), so
 * it keeps running when this screen or the app is closed; finished focus
 * sessions are logged as StudySession records. Deep link: studyspot://timer.
 */
@Composable
fun StudyTimerScreen(navigator: AppNavigator) {
    val context = LocalContext.current
    val c = LocalAppContainer.current
    val controller = remember { TimerController.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val logging by controller.logging.collectAsStateWithLifecycle()
    val assignments by c.repository.assignments.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running, state.endsAtMillis) {
        now = System.currentTimeMillis()
        while (state.running) {
            delay(200)
            now = System.currentTimeMillis()
            if (state.isDue(now)) controller.checkDue()
        }
    }

    // Re-check permissions when coming back from system settings.
    var canNotify by remember { mutableStateOf(Notifier.canPost(context)) }
    var exactAlarms by remember { mutableStateOf(ClassAlarmScheduler.canUseExact(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Notifier.canPost(context)
        exactAlarms = ClassAlarmScheduler.canUseExact(context)
        controller.checkDue()
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted && Notifier.canPost(context)
    }
    fun start() {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationPermission.isGranted(context) && !NotificationPermission.wasAsked(context)) {
            NotificationPermission.markAsked(context)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        controller.start()
    }

    var customFor by rememberSaveable { mutableStateOf<TimerPhase?>(null) }

    SubScreenScaffold(title = "Study timer", onBack = navigator::back) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, StudySpotTheme.colors.border),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(state.phase.label, color = phaseColor(state.phase), icon = phaseIcon(state.phase))
                        Spacer(Modifier.weight(1f))
                        val done = state.cyclesToday(LocalDate.now())
                        Text(
                            "$done session${if (done == 1) "" else "s"} today",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TimerRing(state, now, Modifier.align(Alignment.CenterHorizontally))

                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        TimerPhase.entries.forEachIndexed { i, p ->
                            SegmentedButton(
                                selected = state.phase == p,
                                onClick = { controller.switchPhase(p) },
                                enabled = !state.running,
                                shape = SegmentedButtonDefaults.itemShape(i, TimerPhase.entries.size),
                            ) { Text(p.shortLabel) }
                        }
                    }

                    LengthChips(
                        state = state,
                        enabled = !state.running,
                        onPick = { controller.setMinutes(state.phase, it) },
                        onOther = { customFor = state.phase },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { if (state.running) controller.pause() else start() },
                            enabled = !logging,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    state.running -> "Pause"
                                    state.paused -> "Resume"
                                    else -> "Start"
                                },
                            )
                        }
                        OutlinedButton(onClick = controller::reset, enabled = state.paused && !logging) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Reset")
                        }
                        TextButton(onClick = controller::skip, enabled = !logging) {
                            Icon(Icons.Outlined.SkipNext, contentDescription = null)
                            Spacer(Modifier.width(2.dp))
                            Text("Skip")
                        }
                    }
                }
            }

            AssignmentPicker(
                selectedTitle = state.assignmentTitle,
                options = assignments.filter { !it.completed }.take(100),
                onPick = controller::selectAssignment,
            )

            Text(
                "Focus sessions are saved to your study history when they finish. After every " +
                    "${TimerState.LONG_BREAK_EVERY}th focus session you get a long break. The timer keeps " +
                    "running when you leave the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!canNotify) {
                Hint(
                    icon = Icons.Outlined.NotificationsOff,
                    text = "Notifications are off, so the timer won't show outside the app or alert you when a phase ends.",
                    action = "Allow",
                ) {
                    if (Build.VERSION.SDK_INT >= 33 && !NotificationPermission.wasAsked(context)) {
                        NotificationPermission.markAsked(context)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
            } else if (!exactAlarms && Build.VERSION.SDK_INT >= 31) {
                Hint(
                    icon = Icons.Outlined.Alarm,
                    text = "To end phases and start class countdowns right on time, allow Study Spot to set alarms.",
                    action = "Allow",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                        )
                    }
                }
            }
        }
    }

    customFor?.let { phase ->
        CustomLengthDialog(
            phase = phase,
            initial = state.minutesFor(phase),
            onDismiss = { customFor = null },
            onSet = {
                controller.setMinutes(phase, it)
                customFor = null
            },
        )
    }
}

private fun phaseColor(p: TimerPhase): Color = when (p) {
    TimerPhase.Focus -> Color(0xFFF19D37)
    TimerPhase.Short -> Color(0xFF34C759)
    TimerPhase.Long -> Color(0xFF32ADE6)
}

private fun phaseIcon(p: TimerPhase): ImageVector = if (p == TimerPhase.Focus) Icons.Outlined.Psychology else Icons.Outlined.Coffee

@Composable
private fun TimerRing(state: TimerState, now: Long, modifier: Modifier = Modifier) {
    val tint = phaseColor(state.phase)
    val track = StudySpotTheme.colors.secondary.copy(alpha = 0.12f)
    val progress = state.progress(now)
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(tint, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(phaseIcon(state.phase), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                state.display(now),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                when {
                    state.running -> "${state.minutesFor(state.phase)} min ${state.phase.label.lowercase()}"
                    state.paused -> "Paused"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LengthChips(state: TimerState, enabled: Boolean, onPick: (Int) -> Unit, onOther: () -> Unit) {
    val presets = TimerState.presets(state.phase)
    val current = state.minutesFor(state.phase)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { m ->
            FilterChip(selected = current == m, onClick = { onPick(m) }, enabled = enabled, label = { Text("$m min") })
        }
        FilterChip(
            selected = current !in presets,
            onClick = onOther,
            enabled = enabled,
            label = { Text(if (current in presets) "Other" else "Custom: $current min") },
        )
    }
}

@Composable
private fun AssignmentPicker(
    selectedTitle: String?,
    options: List<education.studyspot.app.model.Assignment>,
    onPick: (education.studyspot.app.model.Assignment?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedTitle ?: "General study (no assignment)",
            onValueChange = {},
            readOnly = true,
            label = { Text("Studying for") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("General study (no assignment)") },
                onClick = { onPick(null); expanded = false },
            )
            options.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(a.displayTitle, maxLines = 1)
                            a.className?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = { onPick(a); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun Hint(icon: ImageVector, text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = StudySpotTheme.colors.secondaryTint)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = StudySpotTheme.colors.secondary)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction, colors = ButtonDefaults.textButtonColors()) { Text(action) }
        }
    }
}

@Composable
private fun CustomLengthDialog(phase: TimerPhase, initial: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in TimerState.MIN_MINUTES..TimerState.MAX_MINUTES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${phase.label} length") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { t -> text = t.filter(Char::isDigit).take(3) },
                label = { Text("Minutes (${TimerState.MIN_MINUTES}–${TimerState.MAX_MINUTES})") },
                isError = text.isNotEmpty() && !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onSet) }, enabled = valid) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
