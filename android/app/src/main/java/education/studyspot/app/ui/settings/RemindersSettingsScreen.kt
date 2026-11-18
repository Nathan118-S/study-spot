package education.studyspot.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.arrayValue
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.User
import education.studyspot.app.push.PushManager
import education.studyspot.app.ui.components.clean
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Reminder lead times (hours before the due date), as stored in user.data.reminder_lead_hours. */
object ReminderLeads {
    val presets = listOf(1.0, 6.0, 12.0, 24.0, 48.0, 72.0)

    /** "6h before", "2d before", "36h before". */
    fun label(h: Double): String {
        if (h < 24) return "${h.clean}h before"
        val d = h / 24
        return if (d == Math.rint(d)) "${d.toInt()}d before" else "${h.clean}h before"
    }

    /** Positive, unique, sorted; accepts a single number (older data) as well as a list. Defaults to [24]. */
    fun from(user: User?): List<Double> {
        val raw = user?.data?.get("reminder_lead_hours")
        raw.arrayValue?.let { arr -> return arr.mapNotNull { it.doubleValue }.filter { it > 0 }.distinct().sorted() }
        raw.doubleValue?.takeIf { it > 0 }?.let { return listOf(it) }
        return listOf(24.0)
    }
}

/**
 * Settings > Reminders (ReminderSettings in ios/StudySpot/Views/Settings/SettingsView.swift):
 * assignment reminders, phone notifications, lead times, do-not-disturb and the
 * class countdown before each class. Changes save right away to user.data.
 */
@Composable
fun RemindersSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val data = user?.data ?: JsonObject(emptyMap())
    val isDemo = user?.isDemo == true

    // Local copies so the controls respond instantly; saved on every change.
    var enabled by remember { mutableStateOf(data["reminders_enabled"].boolValue != false) }
    var leads by remember { mutableStateOf(ReminderLeads.from(user)) }
    var dndEnabled by remember { mutableStateOf(data["dnd_enabled"].boolValue == true) }
    var dndStart by remember { mutableStateOf(HourMinute.parse(data["dnd_start"].stringValue, LocalTime.of(22, 0))) }
    var dndEnd by remember { mutableStateOf(HourMinute.parse(data["dnd_end"].stringValue, LocalTime.of(7, 0))) }
    var custom by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end"
    val pushOn = data["push_reminders"].boolValue != false
    val countdownOn = data["class_live_activities"].boolValue != false

    fun save(patch: JsonObject, reconfigurePush: Boolean = false) {
        if (isDemo) return
        scope.launch {
            try {
                val updated = c.repository.updateUser(patch)
                if (reconfigurePush) PushManager.configure(context, c.api, updated)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                c.messages.show("Couldn't save", e.userMessage, error = true)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            c.messages.show("Notifications are off", "Reminders still show in the app. Turn on notifications for Study Spot in Android settings to get them on this phone.")
        }
    }

    fun setPush(v: Boolean) {
        if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        save(buildJsonObject { put("push_reminders", v) }, reconfigurePush = true)
    }

    fun saveLeads(list: List<Double>) {
        leads = list
        save(buildJsonObject { put("reminder_lead_hours", buildJsonArray { list.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }) })
    }

    fun addLead(h: Double) {
        if (h <= 0 || h in leads) return
        saveLeads((leads + h).sorted())
    }

    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    SettingsColumn(modifier) {
        SettingsSection(
            footer = {
                SettingsFootnote(
                    "Control when you get notified about upcoming assignments. If notifications are turned off for Study Spot " +
                        "in Android settings, reminders still show in the app.",
                )
            },
        ) {
            SettingsToggle(
                title = "Assignment reminders",
                description = "Get a notification before assignments are due.",
                icon = Icons.Outlined.Notifications,
                checked = enabled,
                enabled = !isDemo,
                onCheckedChange = { v -> enabled = v; save(buildJsonObject { put("reminders_enabled", v) }, reconfigurePush = true) },
            )
            HorizontalDivider(color = StudySpotTheme.colors.border)
            SettingsToggle(
                title = "Phone notifications",
                description = "Send reminders to this phone as notifications. They also appear in the in-app notification center.",
                icon = Icons.Outlined.PhoneAndroid,
                checked = pushOn,
                enabled = enabled && !isDemo,
                onCheckedChange = ::setPush,
            )
        }

        SettingsSection(header = "Remind me", footer = { SettingsFootnote("Add the exact times you want a reminder before each due date.") }) {
            if (leads.isEmpty()) SettingsText("No reminder times. Add one below.")
            leads.forEach { h ->
                ListItem(
                    headlineContent = { Text(ReminderLeads.label(h)) },
                    leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = StudySpotTheme.colors.secondary) },
                    trailingContent = {
                        IconButton(onClick = { saveLeads(leads - h) }, enabled = enabled && !isDemo) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove ${ReminderLeads.label(h)}")
                        }
                    },
                    colors = settingsRowColors(),
                )
            }
            // Wraps rather than scrolls sideways, so no preset is hidden off the card's edge.
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReminderLeads.presets.filter { it !in leads }.forEach { p ->
                    AssistChip(onClick = { addLead(p) }, enabled = enabled && !isDemo, label = { Text("+ ${ReminderLeads.label(p)}") })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val customHours = custom.replace(',', '.').toDoubleOrNull() ?: 0.0
                OutlinedTextField(
                    value = custom,
                    onValueChange = { v -> custom = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                    label = { Text("Custom hours before") },
                    singleLine = true,
                    enabled = enabled && !isDemo,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { addLead(customHours); custom = "" }, enabled = enabled && customHours > 0 && !isDemo) { Text("Add") }
            }
        }

        SettingsSection {
            SettingsToggle(
                title = "Do not disturb",
                description = "Pause reminders during a daily quiet window (your local time).",
                icon = Icons.Outlined.Bedtime,
                checked = dndEnabled,
                enabled = enabled && !isDemo,
                onCheckedChange = { v -> dndEnabled = v; save(buildJsonObject { put("dnd_enabled", v) }) },
            )
            HorizontalDivider(color = StudySpotTheme.colors.border)
            TimeRow("Start", dndStart.format(timeFormat), enabled && !isDemo) { picking = "start" }
            TimeRow("End", dndEnd.format(timeFormat), enabled && !isDemo) { picking = "end" }
        }

        SettingsSection {
            SettingsToggle(
                title = "Class countdown before class",
                description = "10 minutes before each class, show a countdown notification with anything due that day. " +
                    "Add meeting times to your classes to use it.",
                icon = Icons.Outlined.Alarm,
                checked = countdownOn,
                enabled = !isDemo,
                onCheckedChange = { v -> save(buildJsonObject { put("class_live_activities", v) }, reconfigurePush = true) },
            )
        }
    }

    when (picking) {
        "start" -> TimePickerAlert("Quiet from", dndStart, onConfirm = { t ->
            dndStart = t
            save(buildJsonObject { put("dnd_start", HourMinute.format(t)) })
        }, onDismiss = { picking = null })
        "end" -> TimePickerAlert("Quiet until", dndEnd, onConfirm = { t ->
            dndEnd = t
            save(buildJsonObject { put("dnd_end", HourMinute.format(t)) })
        }, onDismiss = { picking = null })
    }
}

@Composable
private fun TimeRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(value, color = if (enabled) StudySpotTheme.colors.accent else StudySpotTheme.colors.mutedText) },
        colors = settingsRowColors(),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}
