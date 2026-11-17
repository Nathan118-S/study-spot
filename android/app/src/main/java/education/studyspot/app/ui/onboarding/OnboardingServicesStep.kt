package education.studyspot.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import education.studyspot.app.ui.theme.StudySpotTheme

/** Shown wherever the app offers Google / Blackboard connections. */
const val SCHOOL_IT_DISCLAIMER =
    "Some schools block connecting third-party apps like Study Spot to school Google or Blackboard accounts. " +
        "If the connection is blocked or fails, ask your school's IT team, or add your assignments manually."

/**
 * The onboarding "Services" step body. Placeholder until the settings screens
 * provide embeddable Google / Blackboard connection sections: replace the cards
 * below with those (like iOS, which hosts GoogleSettings() and BlackboardSettings()).
 */
@Composable
fun OnboardingServicesStep(modifier: Modifier = Modifier) {
    val colors = StudySpotTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Import your classes and assignments automatically by connecting the services your school uses.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mutedText,
        )
        ServiceCard(Icons.Outlined.Power, "Google Calendar and Classroom", "Connect in Settings → Google.")
        ServiceCard(Icons.Outlined.AccountBalance, "Blackboard", "Connect in Settings → Blackboard.")
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.secondaryTint),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                Text(SCHOOL_IT_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            }
        }
    }
}

@Composable
private fun ServiceCard(icon: ImageVector, title: String, detail: String) {
    val colors = StudySpotTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = colors.secondary)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.secondary)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
            }
        }
    }
}
