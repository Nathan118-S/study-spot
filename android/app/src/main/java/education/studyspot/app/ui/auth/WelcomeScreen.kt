package education.studyspot.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.WebSignIn
import education.studyspot.app.ui.components.LogoMark
import education.studyspot.app.ui.theme.StudySpotTheme

/**
 * Logo, "Study Spot", Log in and Sign up — nothing else (like iOS LandingView).
 * Long-press the logo for Server settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen() {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val disabledReason by c.auth.disabledReason.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf<WebSignIn.Page?>(null) }
    var error by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }
    val toolbar = MaterialTheme.colorScheme.background.toArgb()

    // Returning from the Custom Tab without a token: stop the spinner.
    LifecycleResumeEffect(Unit) {
        busy = null
        onPauseOrDispose { }
    }

    fun signIn(page: WebSignIn.Page) {
        error = ""
        busy = page
        if (!WebSignIn.open(context, c.config, page, toolbar)) {
            busy = null
            error = "No browser is available to open the sign-in page."
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 500.dp).fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            LogoMark(
                size = 112.dp,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .combinedClickable(onClick = {}, onLongClick = { showServer = true }, onLongClickLabel = "Server settings")
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction("Server settings") { showServer = true; true })
                    },
            )
            Spacer(Modifier.height(16.dp))
            Text("Study Spot", style = MaterialTheme.typography.headlineLarge, color = StudySpotTheme.colors.secondary)
            Spacer(Modifier.weight(1f))

            disabledReason?.let { reason ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Column {
                        Text("Your account has been disabled.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        if (reason.isNotEmpty()) Text(reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (error.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            Button(
                onClick = { signIn(WebSignIn.Page.Login) },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy == WebSignIn.Page.Login) ButtonSpinner()
                Text("Log in", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { signIn(WebSignIn.Page.Register) },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudySpotTheme.colors.secondary),
                // The default outline nearly disappears on the base color, especially in dark mode.
                border = BorderStroke(1.dp, StudySpotTheme.colors.secondary.copy(alpha = 0.35f)),
            ) {
                if (busy == WebSignIn.Page.Register) ButtonSpinner()
                Text("Sign up", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showServer) ServerSettingsDialog(onDismiss = { showServer = false })
}

@Composable
private fun ButtonSpinner() {
    CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
}
