package education.studyspot.app.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.IcalFeed
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * "Import a calendar file" (Settings > Google): a one-time import of Google
 * Calendar's export .zip or any .ics file with functions/icalImportFile, for
 * when calendar links are turned off too. The file is read through the
 * ContentResolver (10 MB max), base64-encoded and sent with mode 'auto', or
 * 'all' when "Import every event" is on.
 */
@Composable
fun IcalImportFileDialog(onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val isDemo = user?.isDemo == true
    var importAll by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val navy = StudySpotTheme.colors.secondary

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || isDemo) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            result = null
            try {
                val file = withContext(Dispatchers.IO) { readCalendarFile(context, uri) }
                if (file == null) {
                    error = "That file is too large (10 MB max)"
                } else {
                    val res = c.repository.importIcalFile(file.first, file.second, if (importAll) IcalFeed.MODE_ALL else IcalFeed.MODE_AUTO)
                    result = IcalLinks.importFileMessage(res)
                }
            } catch (e: IOException) {
                error = "That file couldn’t be read"
            } catch (e: SecurityException) {
                error = "That file couldn’t be read"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Import a calendar file") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "For when calendar links are turned off too. It's a one-time import: new assignments won't appear automatically, " +
                        "so import again later to pick them up.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                NumberedStep(1, "On a computer, open calendar.google.com → ⚙ **Settings** → **Import & export** → **Export**. Google downloads a .zip file.")
                OutlinedButton(
                    onClick = {
                        if (!openExternal(context, IcalLinks.GOOGLE_CALENDAR_EXPORT_URL)) {
                            c.messages.show("Couldn't open Google Calendar", "No browser is available.", error = true)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = navy),
                    modifier = Modifier.padding(start = 26.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Open Google Calendar settings")
                }
                NumberedStep(2, "Choose that .zip file here (or any .ics calendar file).")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Import every event", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = importAll, onCheckedChange = { importAll = it }, enabled = !busy)
                }
                Button(
                    onClick = { picker.launch(IcalLinks.IMPORT_MIME_TYPES) },
                    enabled = !busy && !isDemo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                        Spacer(Modifier.size(8.dp))
                        Text("Importing…")
                    } else {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Choose file…")
                    }
                }
                if (isDemo) SettingsFootnote("The demo account can't import calendars.")
                error?.let { SettingsFootnote(it, error = true) }
                result?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = StudySpotTheme.colors.success, modifier = Modifier.size(18.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(if (result != null) "Done" else "Close", color = navy) }
        },
    )
}

@Composable
private fun NumberedStep(number: Int, markup: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, color = StudySpotTheme.colors.secondary, modifier = Modifier.size(width = 18.dp, height = 20.dp))
        Text(IcalLinks.bold(markup), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The file's name and bytes, or null when it is over 10 MB. Throws IOException when it can't be read. */
private fun readCalendarFile(context: Context, uri: Uri): Pair<String, ByteArray>? {
    val resolver = context.contentResolver
    var name: String? = null
    var size: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    if ((size ?: 0L) > IcalLinks.MAX_IMPORT_BYTES) return null
    val input = resolver.openInputStream(uri) ?: throw IOException("No data")
    val bytes = input.use { IcalLinks.readCapped(it) } ?: return null
    return (name?.takeIf { it.isNotBlank() } ?: "calendar.ics") to bytes
}
