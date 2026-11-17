package education.studyspot.app.ui.templates

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent
import education.studyspot.app.ui.components.SubScreenScaffold

/**
 * Templates (ios/StudySpot/Views/Templates/TemplateViews.swift).
 *
 * TODO(templates): STUB. Assignment templates (repository.templates / createTemplate / updateTemplate / deleteTemplate), and "use template" from the assignment form.
 */
@Composable
fun TemplatesScreen(navigator: AppNavigator) {
    SubScreenScaffold(title = "Templates", onBack = navigator::back) { padding ->
        StubContent("Templates", "Coming soon.", Modifier.padding(padding))
    }
}
