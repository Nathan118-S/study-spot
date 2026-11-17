package education.studyspot.app.ui.timer

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent
import education.studyspot.app.ui.components.SubScreenScaffold

/**
 * Study timer (ios/StudySpot/Views/Dashboard/StudyTimerView.swift).
 *
 * TODO(timer): STUB. Focus/break timer tied to an assignment; save finished sessions with repository.createStudySession (StudySession fields: assignment_id, assignment_title, class_id, class_name, duration_minutes, session_type, started_at, completed_at). Ongoing notification via liveupdates.LiveUpdates. Deep link: studyspot://timer.
 */
@Composable
fun StudyTimerScreen(navigator: AppNavigator) {
    SubScreenScaffold(title = "Study timer", onBack = navigator::back) { padding ->
        StubContent("Study timer", "Coming soon.", Modifier.padding(padding))
    }
}
