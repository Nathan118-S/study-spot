package education.studyspot.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import education.studyspot.app.core.userMessage
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.settings.BusyButton
import education.studyspot.app.ui.settings.SettingsActions
import education.studyspot.app.ui.settings.SettingsColumn
import education.studyspot.app.ui.settings.SettingsFootnote
import education.studyspot.app.ui.settings.SettingsSection
import education.studyspot.app.ui.settings.SettingsToggle
import education.studyspot.app.ui.settings.SettingsValueRow
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private typealias SimSend = (title: String, function: String, payload: JsonObject) -> Unit

/**
 * Admin > account > Simulate on phone (ios/StudySpot/Views/Admin/AdminSimulateView.swift):
 * sends real pushes (notifications, class countdowns, study timers) to the
 * person's registered iPhones (APNs) and Android phones (FCM).
 */
@Composable
fun AdminSimulateScreen(nav: AdminNav, model: AdminAccountModel) {
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(SimOptions()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<SimResult>() }

    suspend fun refreshOptions() {
        runCatching { model.call("adminSimulateOptions") }.getOrNull()?.let { options = SimOptions.from(it) }
    }

    suspend fun load() {
        loading = true
        try {
            options = SimOptions.from(model.call("adminSimulateOptions"))
        } catch (_: AdminUnlock.Cancelled) {
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            results.add(0, SimResult.error("Couldn't load", e.userMessage))
        }
        loading = false
    }

    val send: SimSend = { title, function, payload ->
        scope.launch {
            busy = true
            try {
                results.add(0, SimResult.from(title, model.call(function, payload)))
            } catch (_: AdminUnlock.Cancelled) {
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                results.add(0, SimResult.error(title, e.userMessage))
            }
            while (results.size > 6) results.removeAt(results.lastIndex)
            // Dead tokens are dropped server-side, so refresh the device counts.
            refreshOptions()
            busy = false
        }
    }

    LaunchedEffect(model) { load() }

    SubScreenScaffold(
        title = "Simulate on phone",
        onBack = nav::pop,
        actions = { IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh") } },
    ) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            StatusSection(options, loading)
            if (results.isNotEmpty()) ResultsSection(results)
            NotificationSection(options, busy, send)
            ClassSection(options, busy, send)
            TimerSection(options, busy, send)
        }
    }
}

private fun phones(n: Int, noun: String) = if (n == 0) "None" else "$n $noun${if (n == 1) "" else "s"}"

@Composable
private fun StatusSection(options: SimOptions, loading: Boolean) {
    val ok = StudySpotTheme.colors.success
    val warn = StudySpotTheme.colors.dueToday
    SettingsSection(
        header = "Status",
        footer = {
            SettingsFootnote(
                if (!loading && !options.anyPush) {
                    "Add the Apple Push key and/or the Firebase service account in Admin → Integration Secrets to send pushes."
                } else {
                    "A phone registers when the app is opened signed in to this account. iPhone Live Activities need iOS 17.2 or later."
                },
            )
        },
    ) {
        SettingsValueRow("Apple Push", "", trailing = {
            Text(if (loading) "…" else if (options.apns) "Set up" else "Not set up", color = if (options.apns) ok else warn)
        })
        SettingsValueRow("Firebase (Android)", "", trailing = {
            Text(if (loading) "…" else if (options.fcm) "Set up" else "Not set up", color = if (options.fcm) ok else warn)
        })
        HorizontalDivider(color = StudySpotTheme.colors.border)
        SettingsValueRow("iPhone notifications", phones(options.notificationDevices, "iPhone"))
        SettingsValueRow("iPhone class Live Activities", phones(options.classDevices, "iPhone"))
        SettingsValueRow("iPhone study timer Live Activities", phones(options.timerDevices, "iPhone"))
        SettingsValueRow("Android phones", phones(options.androidDevices, "phone"))
    }
}

@Composable
private fun ResultsSection(results: List<SimResult>) {
    SettingsSection(header = "Results") {
        results.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (r.ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = if (r.ok) "Succeeded" else "Problem",
                    tint = if (r.ok) StudySpotTheme.colors.success else StudySpotTheme.colors.dueToday,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${r.title}: ${r.summary}", style = MaterialTheme.typography.bodyMedium)
                    r.failures.forEach { f ->
                        Text(f, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(AdminParse.time(r.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        androidx.compose.material3.OutlinedIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text("$value", modifier = Modifier.padding(horizontal = 12.dp))
        androidx.compose.material3.OutlinedIconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
private fun NotificationSection(options: SimOptions, busy: Boolean, send: SimSend) {
    var kind by remember { mutableStateOf("reminder") }
    var assignmentId by remember { mutableStateOf("") }
    var classId by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(7) }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var inApp by remember { mutableStateOf(true) }
    val canSend = (options.notificationPushReady || inApp) && (kind != "custom" || title.isNotBlank())
    val field = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)

    SettingsSection(
        header = "Notification",
        footer = {
            SettingsFootnote(
                when {
                    !options.anyPush -> "Push isn't set up, so only the notification bell can be tested."
                    !options.notificationPushReady -> "No phone has turned on notifications for this account, so only the notification bell can be tested."
                    else -> "Arrives like a real reminder. Tapping it opens the app."
                },
            )
        },
    ) {
        ChoiceField("Type", listOf("reminder" to "Assignment reminder", "streak" to "Streak record", "custom" to "Custom"), kind, { kind = it })
        when (kind) {
            "reminder" -> ChoiceField(
                "Assignment",
                listOf("" to "Sample assignment") + options.assignments.map { it.id to it.name },
                assignmentId, { assignmentId = it },
            )
            "streak" -> {
                ChoiceField("Class", listOf("" to "Sample class") + options.classes.map { it.id to it.name }, classId, { classId = it })
                Stepper("Streak", streak, 2..999) { streak = it }
            }
            else -> {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = field)
                OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtitle (optional)") }, singleLine = true, modifier = field)
                OutlinedTextField(message, { message = it }, label = { Text("Message") }, minLines = 2, maxLines = 5, modifier = field)
                OutlinedTextField(url, { url = it }, label = { Text("Link (optional)") }, placeholder = { Text("studyspot://tab/streaks") }, singleLine = true, modifier = field)
            }
        }
        SettingsToggle("Also add to their notification bell", checked = inApp, onCheckedChange = { inApp = it })
        SettingsActions {
            BusyButton("Send notification", icon = Icons.AutoMirrored.Outlined.Send, busy = busy, enabled = canSend, onClick = {
                send("Notification", "adminSimulateNotification", buildJsonObject {
                    put("kind", kind)
                    put("inApp", inApp)
                    when (kind) {
                        "reminder" -> if (assignmentId.isNotEmpty()) put("assignmentId", assignmentId)
                        "streak" -> {
                            if (classId.isNotEmpty()) put("classId", classId)
                            put("streak", streak)
                        }
                        else -> {
                            put("title", title)
                            put("subtitle", subtitle)
                            put("body", message)
                            if (url.isNotBlank()) put("url", url.trim())
                        }
                    }
                })
            })
        }
    }
}

@Composable
private fun ClassSection(options: SimOptions, busy: Boolean, send: SimSend) {
    var classId by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(10) }

    SettingsSection(
        header = "Class countdown",
        footer = {
            SettingsFootnote(
                when {
                    !options.anyPush -> "Needs Apple Push or Firebase to be set up."
                    !options.classReady -> "No phone has registered for class countdowns yet (iPhone: iOS 17.2+, app opened signed in, class countdowns on; Android: app opened signed in with push set up)."
                    else -> "Shows the class countdown (Lock Screen and Dynamic Island on iPhone, an ongoing notification on Android). End only reaches iPhone activities that were reported back, so the sample class ends on its own."
                },
            )
        },
    ) {
        ChoiceField("Class", listOf("" to "Sample class") + options.classes.map { it.id to it.name }, classId, { classId = it })
        Stepper("Starts in (min)", minutes, 1..60) { minutes = it }
        SettingsActions {
            BusyButton("End", icon = Icons.Outlined.StopCircle, busy = busy, prominent = false, enabled = options.anyPush, onClick = {
                send("End class countdowns", "adminSimulateLiveActivity", buildJsonObject { put("kind", "class_start"); put("action", "end") })
            })
            BusyButton("Start", icon = Icons.Outlined.PlayCircle, busy = busy, enabled = options.classReady, onClick = {
                send("Class countdown", "adminSimulateLiveActivity", buildJsonObject {
                    put("kind", "class_start")
                    put("action", "start")
                    put("minutes", minutes)
                    if (classId.isNotEmpty()) put("classId", classId)
                })
            })
        }
    }
}

@Composable
private fun TimerSection(options: SimOptions, busy: Boolean, send: SimSend) {
    var phase by remember { mutableStateOf("focus") }
    var minutes by remember { mutableIntStateOf(25) }
    var assignmentId by remember { mutableStateOf("") }

    SettingsSection(
        header = "Study timer",
        footer = {
            SettingsFootnote(
                when {
                    !options.anyPush -> "Needs Apple Push or Firebase to be set up."
                    !options.timerReady -> "No phone has registered for study timers yet (iPhone: iOS 17.2+ and the latest app opened signed in)."
                    else -> "Shows a running timer on their phone. It isn't connected to the app's own timer."
                },
            )
        },
    ) {
        ChoiceField("Phase", listOf("focus" to "Focus", "short" to "Short break", "long" to "Long break"), phase, { p ->
            phase = p
            minutes = when (p) { "focus" -> 25; "short" -> 5; else -> 15 }
        })
        Stepper("Minutes", minutes, 1..120) { minutes = it }
        ChoiceField("Assignment", listOf("" to "None") + options.assignments.map { it.id to it.name }, assignmentId, { assignmentId = it })
        SettingsActions {
            BusyButton("Start timer", icon = Icons.Outlined.Timer, busy = busy, enabled = options.timerReady, onClick = {
                send("Study timer", "adminSimulateLiveActivity", buildJsonObject {
                    put("kind", "study_timer")
                    put("action", "start")
                    put("phase", phase)
                    put("minutes", minutes)
                    if (assignmentId.isNotEmpty()) put("assignmentId", assignmentId)
                })
            })
        }
    }
}
