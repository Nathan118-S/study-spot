package education.studyspot.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.SubScreenScaffold
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * First-run setup wizard, shown by AppRoot while `auth.needsOnboarding`
 * (user.data.onboarding_completed is not true) — ios/StudySpot/Views/Onboarding/OnboardingView.swift.
 *
 * TODO(onboarding): STUB. Steps: welcome, add classes, connect Google/Blackboard,
 * reminders, leaderboard, welcome style. Finishing must PATCH
 * `{ onboarding_completed: true }` through repository.updateUser (as below).
 */
@Composable
fun OnboardingScreen() {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    SubScreenScaffold(title = "Welcome", onBack = null) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(Icons.Outlined.School, "Welcome to Study Spot", message = "Setup wizard coming soon.")
            Button(onClick = {
                scope.launch {
                    runCatching { c.repository.updateUser(buildJsonObject { put("onboarding_completed", true) }) }
                }
            }) { Text("Get started") }
        }
    }
}
