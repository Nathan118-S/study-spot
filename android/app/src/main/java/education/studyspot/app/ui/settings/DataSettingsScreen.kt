package education.studyspot.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import education.studyspot.app.data.ExcelExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings > Data (DataExportSettings in ios/StudySpot/Views/Settings/SettingsView.swift):
 * builds an Excel workbook of all assignments and classes ([ExcelExport]) and
 * shares it through the app's FileProvider.
 */
@Composable
fun DataSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<Uri?>(null) }
    var counts by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun build() {
        scope.launch {
            busy = true
            try {
                val (assignments, classes) = if (c.repository.user.value?.isDemo == true) {
                    c.repository.assignments.value to c.repository.classes.value
                } else {
                    c.api.assignments(limit = 2000) to c.api.classes()
                }
                file = withContext(Dispatchers.IO) {
                    ExcelExport.write(context, ExcelExport.build(assignments, classes))
                }
                counts = assignments.size to classes.size
                file?.let { share(context, it) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                c.messages.show("Export failed", e.userMessage, error = true)
            }
            busy = false
        }
    }

    SettingsColumn(modifier) {
        SettingsSection {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Export your data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Create an Excel spreadsheet of all your assignments and classes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                counts?.let { (a, cl) ->
                    Text(
                        "Last export: $a assignments, $cl classes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingsActions {
                file?.let { uri ->
                    BusyButton("Share again", onClick = { share(context, uri) }, prominent = false, icon = Icons.Outlined.Share)
                }
                BusyButton("Export to Excel", onClick = ::build, busy = busy, icon = Icons.Outlined.FileDownload)
            }
        }
    }
}

private fun share(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = ExcelExport.MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Study Spot export")
        clipData = ClipData.newRawUri(ExcelExport.FILE_NAME, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Share or save ${ExcelExport.FILE_NAME}")
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // No app can receive it; the button stays available.
    }
}
