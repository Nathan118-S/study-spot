package education.studyspot.app.push

import androidx.core.content.edit
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.liveupdates.ClassAlarmScheduler
import kotlinx.coroutines.launch

/** POST_NOTIFICATIONS (Android 13+) helpers. */
object NotificationPermission {
    private const val PREFS = "push"
    private const val KEY_ASKED = "notification_permission_asked"

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun wasAsked(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }
}

/**
 * Asks once for notification permission on the first signed-in launch,
 * with a short rationale first (reminders, class countdowns, timer alerts).
 * Place it inside the signed-in UI; it renders nothing otherwise.
 */
@Composable
fun NotificationPermissionPrompt() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val c = LocalAppContainer.current
    val user by c.repository.user.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // Now allowed: register for push and post any countdown that's due.
            c.scope.launch { PushManager.configure(c.context, c.api, c.repository.user.value) }
            ClassAlarmScheduler.reschedule(c.context)
        }
    }
    LaunchedEffect(user?.id) {
        val u = user ?: return@LaunchedEffect
        show = !u.isDemo && !u.mfaPending && !NotificationPermission.isGranted(context) && !NotificationPermission.wasAsked(context)
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = {
            show = false
            NotificationPermission.markAsked(context)
        },
        icon = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
        title = { Text("Stay on top of your work") },
        text = {
            Text(
                "Study Spot can remind you before assignments are due, count down to your next class " +
                    "and tell you when a study timer session ends. You can change this any time in Settings.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                show = false
                NotificationPermission.markAsked(context)
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Allow notifications") }
        },
        dismissButton = {
            TextButton(onClick = {
                show = false
                NotificationPermission.markAsked(context)
            }) { Text("Not now") }
        },
    )
}
