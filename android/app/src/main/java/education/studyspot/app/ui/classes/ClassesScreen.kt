package education.studyspot.app.ui.classes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent

/**
 * Classes tab (ios/StudySpot/Views/Classes/ClassesView.swift).
 *
 * TODO(classes): STUB. List classes (repository.classes) with color, teacher, room and weekly schedule; add/edit/delete via repository.createClass/updateClass/deleteClass (ClassFormView: name, color, teacher, subject, room, schedule meetings).
 * Read data from `LocalAppContainer.current.repository`; apply [contentPadding]
 * (the shell's top bar and bottom navigation).
 */
@Composable
fun ClassesScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    StubContent("Classes", "Coming soon.", Modifier.padding(contentPadding))
}
