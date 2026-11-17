package education.studyspot.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.GradeEntry
import education.studyspot.app.model.Grading
import education.studyspot.app.ui.components.clean
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One editable row: text fields keep what the user typed until saved. */
class GradeRow(letter: String, min: String) {
    var letter by mutableStateOf(letter)
    var min by mutableStateOf(min)

    companion object {
        fun from(e: GradeEntry) = GradeRow(e.letter, e.min.clean)
    }
}

/** Valid rows (non-empty letter, numeric minimum), highest minimum first. */
fun gradeScaleFrom(rows: List<GradeRow>): List<GradeEntry> = rows
    .mapNotNull { r ->
        val letter = r.letter.trim().take(3)
        val min = r.min.trim().replace(',', '.').toDoubleOrNull()
        if (letter.isEmpty() || min == null) null else GradeEntry(letter, min)
    }
    .sortedByDescending { it.min }

fun gradeScaleJson(scale: List<GradeEntry>): JsonArray = buildJsonArray {
    scale.forEach { e -> add(buildJsonObject { put("letter", e.letter); put("min", e.min) }) }
}

/** Settings > Grading (GradingScaleEditor in ios/StudySpot/Views/Settings/SettingsView.swift). */
@Composable
fun GradingSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val rows = remember {
        mutableStateListOf<GradeRow>().apply {
            addAll((c.repository.user.value?.gradingScale ?: Grading.defaultScale).map(GradeRow::from))
        }
    }
    var saving by remember { mutableStateOf(false) }

    SettingsColumn(modifier) {
        SettingsSection(
            header = "Letter grades",
            footer = { SettingsFootnote("Set the minimum percentage for each letter grade. This is used to compute letter grades on graded assignments.") },
        ) {
            GradeScaleEditor(rows)
        }
        SettingsSection {
            SettingsActions {
                TextButton(onClick = {
                    rows.clear()
                    rows.addAll(Grading.defaultScale.map(GradeRow::from))
                }) { Text("Reset to default", color = StudySpotTheme.colors.secondary) }
                BusyButton(
                    "Save scale",
                    busy = saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            val sorted = gradeScaleFrom(rows)
                            try {
                                if (c.repository.user.value?.isDemo != true) {
                                    c.repository.updateUser(buildJsonObject { put("grading_scale", gradeScaleJson(sorted)) })
                                }
                                rows.clear()
                                rows.addAll(sorted.map(GradeRow::from))
                                c.messages.show("Grading scale saved")
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                c.messages.show("Couldn't save", e.userMessage, error = true)
                            }
                            saving = false
                        }
                    },
                )
            }
        }
    }
}

/** Letter ≥ minimum % rows with delete buttons and "Add row" (also used by the admin account manager). */
@Composable
fun GradeScaleEditor(rows: SnapshotStateList<GradeRow>) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { i, row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.letter,
                    onValueChange = { row.letter = it.take(3) },
                    placeholder = { Text("Letter") },
                    singleLine = true,
                    modifier = Modifier.width(96.dp),
                )
                Text("≥", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = row.min,
                    onValueChange = { v -> row.min = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { rows.removeAt(i) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove ${row.letter.ifBlank { "row" }}")
                }
            }
        }
        TextButton(onClick = { rows.add(GradeRow("", "0")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("Add row", modifier = Modifier.padding(start = 6.dp))
        }
    }
}
