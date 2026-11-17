package education.studyspot.app.ui.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent

/**
 * Calendar tab (ios/StudySpot/Views/Calendar/CalendarScreen.swift).
 *
 * TODO(calendar): STUB. Month grid with assignments by due date (repository.assignments), day list using ui.dashboard.AssignmentRow, drag/move with repository.reschedule.
 * Read data from `LocalAppContainer.current.repository`; apply [contentPadding]
 * (the shell's top bar and bottom navigation).
 */
@Composable
fun CalendarScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    StubContent("Calendar", "Coming soon.", Modifier.padding(contentPadding))
}
