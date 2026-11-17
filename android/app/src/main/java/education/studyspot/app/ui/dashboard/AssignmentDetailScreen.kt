package education.studyspot.app.ui.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent
import education.studyspot.app.ui.components.SubScreenScaffold

/**
 * Assignment (ios/StudySpot/Views/Dashboard/AssignmentDetailView.swift).
 *
 * TODO(dashboard): STUB. Show repository.assignment(assignmentId) (observe repository.assignments): details, progress, subtasks, score/points with letter grade, notes, Classroom attachments (functions/getClassroomAttachments -> model.ClassroomAttachments), edit (navigator.editAssignment) and delete. The id may not be loaded yet when opened from a deep link: show a loader until it appears.
 */
@Composable
fun AssignmentDetailScreen(assignmentId: String, navigator: AppNavigator) {
    SubScreenScaffold(title = "Assignment", onBack = navigator::back) { padding ->
        StubContent("Assignment", "Coming soon.", Modifier.padding(padding))
    }
}
