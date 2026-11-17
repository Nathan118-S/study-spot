package education.studyspot.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import education.studyspot.app.ui.theme.StudySpotTheme
import java.time.LocalTime

// Building blocks shared by the settings sub-pages and the admin screens:
// grouped cards (the iOS Form sections), toggles, busy buttons and dialogs.

/** The scrolling body of a settings page. */
@Composable
fun SettingsColumn(modifier: Modifier = Modifier, embedded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    // Embedded (e.g. inside onboarding's own scrolling column): no scroll or
    // fill of its own, which would conflict with the parent.
    Column(
        if (embedded) modifier.fillMaxWidth()
        else modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** A grouped card with an optional header above and footer below (an iOS Form section). */
@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = StudySpotTheme.colors.secondary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, StudySpotTheme.colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
        if (footer != null) {
            Box(Modifier.padding(horizontal = 4.dp)) { footer() }
        }
    }
}

/** Small explanatory text under a section. */
@Composable
fun SettingsFootnote(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Plain padded text inside a section. */
@Composable
fun SettingsText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
fun settingsRowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)

/** A titled switch row with a description and optional icon. */
@Composable
fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null, tint = StudySpotTheme.colors.secondary) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        colors = settingsRowColors(),
        modifier = modifier,
    )
}

/** A label/value row (iOS LabeledContent). */
@Composable
fun SettingsValueRow(label: String, value: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = if (value.isEmpty()) null else ({ Text(value) }),
        trailingContent = trailing,
        colors = settingsRowColors(),
        modifier = modifier,
    )
}

/** A full-width button with a spinner while [busy]. Orange (primary) when [prominent]. */
@Composable
fun BusyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
    prominent: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    val content: @Composable () -> Unit = {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text)
            }
        }
    }
    if (prominent || destructive) {
        Button(
            onClick = onClick,
            enabled = enabled && !busy,
            modifier = modifier,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled && !busy,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StudySpotTheme.colors.secondary),
        ) { content() }
    }
}

/** Buttons inside a section, right-aligned with padding. */
@Composable
fun SettingsActions(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A confirmation dialog; [destructive] colors the confirm button red. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissLabel: String = "Cancel",
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = if (message == null && content == null) null else ({
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Text(message)
                content?.invoke()
            }
        }),
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = StudySpotTheme.colors.secondary) }
        },
    )
}

/** A 6-digit one-time code field (authenticator app). */
@Composable
fun CodeField(code: String, onCodeChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    OutlinedTextField(
        value = code,
        onValueChange = { v -> onCodeChange(v.filter(Char::isDigit).take(6)) },
        placeholder = { Text("123456", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 6.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (code.length == 6) onDone?.invoke() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** "HH:mm" <-> LocalTime, as stored in user.data (dnd_start / dnd_end). */
object HourMinute {
    fun parse(s: String?, fallback: LocalTime): LocalTime {
        val parts = s?.split(":")?.mapNotNull { it.trim().toIntOrNull() } ?: return fallback
        if (parts.isEmpty() || parts[0] !in 0..23) return fallback
        val m = parts.getOrNull(1)?.takeIf { it in 0..59 } ?: 0
        return LocalTime.of(parts[0], m)
    }

    fun format(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
}

/** A Material time picker in a dialog. */
@Composable
fun TimePickerAlert(title: String, initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = StudySpotTheme.colors.secondary) } },
    )
}
