package education.studyspot.app.ui.insights

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent

/**
 * Insights tab (ios/StudySpot/Views/Insights/InsightsView.swift).
 *
 * TODO(insights): STUB. Charts (Vico is on the classpath: com.patrykandpatrick.vico.compose) of completion, grades and study time (repository.loadStudySessions / studySessions).
 * Read data from `LocalAppContainer.current.repository`; apply [contentPadding]
 * (the shell's top bar and bottom navigation).
 */
@Composable
fun InsightsScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    StubContent("Insights", "Coming soon.", Modifier.padding(contentPadding))
}
