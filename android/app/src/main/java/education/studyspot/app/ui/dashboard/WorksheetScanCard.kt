package education.studyspot.app.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import education.studyspot.app.logic.WorksheetParser
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.ui.components.SectionCard
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * "Scan a worksheet" on the new-assignment form: take a photo of a handout (or
 * pick one), read its text on the device with ML Kit, and prefill the form
 * through [onScanned] (see logic/WorksheetParser.kt). Nothing is uploaded.
 * Mirrors ios/StudySpot/Views/Dashboard/WorksheetScanSection.swift.
 */
@Composable
fun WorksheetScanCard(classes: List<SchoolClass>, onScanned: (WorksheetParser.Result) -> Unit) {
    val context = LocalContext.current
    val colors = StudySpotTheme.colors
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    fun read(uri: Uri) {
        scope.launch {
            busy = true
            status = null
            try {
                val lines = recognizeLines(context, uri)
                if (lines.isEmpty()) {
                    status = "No text found. Try again with the whole page in view and good light."
                } else {
                    val result = WorksheetParser.parse(
                        lines,
                        classes.map { WorksheetParser.ClassRef(it.id, it.displayName) },
                        LocalDate.now(ZoneId.systemDefault()),
                    )
                    onScanned(result)
                    status = "Filled in from the worksheet. Check the details, then save."
                }
            } catch (e: Exception) {
                status = "Couldn't read that photo" + (e.message?.let { ": $it" } ?: ".")
            } finally {
                busy = false
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingUri
        if (saved && uri != null) read(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) read(uri)
    }

    SectionCard(title = "Scan a worksheet") {
        Text(
            "Point your camera at a handout to fill in the title, due date and class. Text is read on your device; nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.mutedText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasCamera) {
                Button(
                    onClick = {
                        val uri = newScanUri(context)
                        pendingUri = uri
                        try {
                            takePicture.launch(uri)
                        } catch (_: ActivityNotFoundException) {
                            status = "No camera app is available. Choose a photo instead."
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) {
                    Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Scan")
                }
            }
            OutlinedButton(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !busy,
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                Text("  Choose photo", color = colors.secondary)
            }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary) }
    }
}

/** A fresh cache file the camera app can write to, shared through the app's FileProvider. */
private fun newScanUri(context: Context): Uri {
    val dir = File(context.cacheDir, "scans").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // only ever keep the latest photo
    val file = File(dir, "worksheet-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** On-device OCR with ML Kit's bundled Latin model; lines in reading order with their heights. */
private suspend fun recognizeLines(context: Context, uri: Uri): List<WorksheetParser.Line> {
    val image = InputImage.fromFilePath(context, uri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val text: MlText = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return text.textBlocks.flatMap { block ->
            block.lines.map { line -> WorksheetParser.Line(line.text, line.boundingBox?.height()?.toDouble()) }
        }
    } finally {
        recognizer.close()
    }
}
