package education.studyspot.app.ui.streaks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent

/**
 * Streaks tab (ios/StudySpot/Views/Streaks/StreaksView.swift).
 *
 * TODO(streaks): STUB. Per-class streaks (port ios/StudySpot/Logic/Grading.swift Streaks.compute), summary, best streaks (Class.best_streak), leaderboard via api.invokeAs<Leaderboard>("getLeaderboard") when user.leaderboardEnabled.
 * Read data from `LocalAppContainer.current.repository`; apply [contentPadding]
 * (the shell's top bar and bottom navigation).
 */
@Composable
fun StreaksScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    StubContent("Streaks", "Coming soon.", Modifier.padding(contentPadding))
}
