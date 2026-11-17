package education.studyspot.app.ui.admin

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent
import education.studyspot.app.ui.components.SubScreenScaffold

/**
 * Admin (ios/StudySpot/Views/Admin).
 *
 * TODO(admin): STUB. Admin tools (admins only): people (functions/adminListUsers -> model.AdminUser), per-account management, send notification, secrets (adminListSecrets -> model.SecretStatus), deploy (adminDeployStatus -> model.DeployStatus), simulate. Per-account calls need the 2FA step-up: on ApiException.elevationRequired run functions/adminElevateStart + adminElevate and store the returned token in `api.adminElevation` (memory only; sent as X-Admin-Elevation).
 */
@Composable
fun AdminScreen(navigator: AppNavigator) {
    SubScreenScaffold(title = "Admin", onBack = navigator::back) { padding ->
        StubContent("Admin", "Coming soon.", Modifier.padding(padding))
    }
}
