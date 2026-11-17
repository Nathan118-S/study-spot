package education.studyspot.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Settings > Onboarding (OnboardingRestart in ios/StudySpot/Views/Settings/SettingsView.swift).
 * Clearing onboarding_completed makes AppRoot show the setup wizard.
 */
@Composable
fun OnboardingSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }

    SettingsColumn(modifier) {
        SettingsSection {
            SettingsText("Re-run the first-time setup wizard to revisit your theme, leaderboard, security, services, and grading choices.")
            SettingsActions {
                BusyButton(
                    "Restart onboarding",
                    icon = Icons.Outlined.RestartAlt,
                    busy = busy,
                    enabled = user?.isDemo != true,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                c.repository.updateUser(buildJsonObject { put("onboarding_completed", false) })
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                c.messages.show("Couldn't restart onboarding", e.userMessage, error = true)
                            }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}
