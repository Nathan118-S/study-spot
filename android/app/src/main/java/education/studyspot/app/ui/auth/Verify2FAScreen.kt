package education.studyspot.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.SubScreenScaffold

/**
 * Second-factor check after a website sign-in whose session is `mfa_pending`
 * (ios/StudySpot/Views/Auth/Verify2FAView.swift).
 *
 * TODO(2fa): STUB. Offer the user's methods (user.enabledTwoFactorMethods):
 * - TOTP: functions/verify2fa { code } ->
 * - Passkey: functions/passkeyLoginStart / passkeyLoginFinish via androidx.credentials
 * then call `c.auth.completeTwoFactor(response)` with the function's JSON result
 * (it carries the full `access_token`). Keep "Sign out" available.
 */
@Composable
fun Verify2FAScreen() {
    val c = LocalAppContainer.current
    val user by c.repository.user.collectAsStateWithLifecycle()
    SubScreenScaffold(title = "Two-factor authentication", onBack = null) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                icon = Icons.Outlined.Lock,
                title = "Confirm it's you",
                message = "Methods: " + user?.enabledTwoFactorMethods.orEmpty().joinToString { it.label } +
                    "\n(Verification screen coming soon.)",
            )
            TextButton(onClick = { c.auth.signOut() }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
        }
    }
}
