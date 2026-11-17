package education.studyspot.app.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Material date picker for a local day. (The M3 picker works in UTC-midnight
 * millis; this converts so callers only see [LocalDate].)
 */
@Composable
fun DayPickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    confirmLabel: String = "OK",
    title: String? = null,
    extraAction: (@Composable () -> Unit)? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(confirmLabel) }
        },
        dismissButton = extraAction ?: { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state, title = title?.let { t -> { Text(t, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) } })
    }
}

/** Material time picker in a dialog, honoring the device's 12/24-hour setting. */
@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit, title: String = "Select time") {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "3:00 PM" / "15:00" in the user's locale. */
fun LocalTime.short(): String = format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** A read-only outlined field that opens a menu of [options]. */
@Composable
fun <T> DropdownField(
    label: String,
    selected: String,
    options: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}
