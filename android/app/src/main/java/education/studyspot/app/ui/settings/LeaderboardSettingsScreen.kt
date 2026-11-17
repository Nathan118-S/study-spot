package education.studyspot.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Settings > Leaderboard (LeaderboardSetting in ios/StudySpot/Views/Settings/SettingsView.swift). */
@Composable
fun LeaderboardSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()

    SettingsColumn(modifier) {
        SettingsSection {
            SettingsToggle(
                title = "Study-streak leaderboard",
                description = "Display the top study streaks on the Streaks page so you can compete with other students. " +
                    "Off by default. Your streak is never shared unless you turn this on.",
                icon = Icons.Outlined.EmojiEvents,
                checked = user?.leaderboardEnabled ?: false,
                enabled = user?.isDemo != true,
                onCheckedChange = { v ->
                    scope.launch {
                        try {
                            c.repository.updateUser(buildJsonObject { put("leaderboard_enabled", v) })
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            c.messages.show("Couldn't save", e.userMessage, error = true)
                        }
                    }
                },
            )
        }
    }
}
