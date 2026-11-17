package education.studyspot.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import education.studyspot.app.ui.settings.BlackboardSettingsScreen
import education.studyspot.app.ui.settings.GoogleSettingsScreen
import education.studyspot.app.ui.theme.StudySpotTheme

/**
 * The onboarding "Services" step: the same Google and Blackboard connection
 * sections as Settings (each with the school-IT notice and the "Email my
 * school's IT team" draft), like iOS.
 */
@Composable
fun OnboardingServicesStep(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Import your classes and assignments automatically by connecting the services your school uses. " +
                "You can also do this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = StudySpotTheme.colors.mutedText,
        )
        GoogleSettingsScreen(embedded = true)
        BlackboardSettingsScreen(embedded = true)
    }
}
