package education.studyspot.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.ui.components.EmptyState
import kotlinx.coroutines.launch

/** The stored session couldn't be checked because the server is unreachable. */
@Composable
fun UnreachableScreen(message: String) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var showServer by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(icon = Icons.Outlined.WifiOff, title = "Can't reach Study Spot", message = message)
        Button(onClick = { scope.launch { c.auth.check() } }) { Text("Try again") }
        TextButton(onClick = { showServer = true }) { Text("Change server") }
        TextButton(onClick = { c.auth.signOut() }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
    }
    if (showServer) ServerSettingsDialog(onDismiss = { showServer = false })
}
