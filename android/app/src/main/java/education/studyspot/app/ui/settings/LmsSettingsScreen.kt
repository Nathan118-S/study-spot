package education.studyspot.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.model.IcalKind
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch

/**
 * Settings > Canvas, Schoology & Brightspace (shared spec: lms-spec.md): a row per
 * system that opens its calendar-link guide ([IcalGuideDialog] with
 * [IcalKind.Canvas] / [IcalKind.Schoology] / [IcalKind.Brightspace]), then the
 * links of those kinds, each labelled with its system. With [embedded] (the
 * onboarding services step) it's a compact card with three buttons instead of rows.
 */
@Composable
fun LmsSettingsScreen(modifier: Modifier = Modifier, embedded: Boolean = false) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val allFeeds by c.repository.icalFeeds.collectAsStateWithLifecycle()
    val feeds = IcalLinks.lmsFeeds(allFeeds)
    val isDemo = user?.isDemo == true
    var guide by remember { mutableStateOf<IcalKind?>(null) }

    LifecycleResumeEffect(Unit) {
        val job = scope.launch { c.repository.refreshIcalFeeds() }
        onPauseOrDispose { job.cancel() }
    }

    SettingsColumn(modifier, embedded = embedded) {
        SettingsSection(header = if (embedded) SettingsPage.Lms.title else null) {
            SettingsText(LMS_INTRO)
            if (embedded) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IcalKind.LMS.forEach { kind ->
                        BusyButton(kind.label, onClick = { guide = kind }, enabled = !isDemo, prominent = false, icon = Icons.Outlined.Add)
                    }
                }
            } else {
                IcalKind.LMS.forEach { kind ->
                    HorizontalDivider(color = StudySpotTheme.colors.border)
                    LmsRow(kind, enabled = !isDemo) { guide = kind }
                }
            }
            IcalFeedRows(feeds, isDemo = isDemo, showKind = true)
        }
    }

    guide?.let { kind -> IcalGuideDialog(onDismiss = { guide = null }, kind = kind) }
}

/** The intro text of Settings > Canvas, Schoology & Brightspace and its onboarding card. */
const val LMS_INTRO =
    "If your school uses Canvas, Schoology or Brightspace, paste your calendar link from it and Study Spot will import your due dates. " +
        "It works without your school approving anything."

@Composable
private fun LmsRow(kind: IcalKind, enabled: Boolean, onClick: () -> Unit) {
    val navy = StudySpotTheme.colors.secondary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = IcalLinks.guideTitle(kind), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EditCalendar, contentDescription = null, tint = if (enabled) navy else StudySpotTheme.colors.mutedText)
        Text(
            kind.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else StudySpotTheme.colors.mutedText,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = StudySpotTheme.colors.mutedText)
    }
}
