package education.studyspot.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.AuthStore
import education.studyspot.app.ui.auth.SplashScreen
import education.studyspot.app.ui.auth.UnreachableScreen
import education.studyspot.app.ui.auth.Verify2FAScreen
import education.studyspot.app.ui.auth.WelcomeScreen
import education.studyspot.app.ui.components.LocalSnackbarHostState
import education.studyspot.app.ui.onboarding.OnboardingScreen

/**
 * Top of the UI (ios/StudySpot/App/RootView.swift): picks splash / welcome /
 * 2FA / onboarding / the main app from the auth state, and shows app messages
 * as snackbars.
 */
@Composable
fun AppRoot() {
    val c = LocalAppContainer.current
    val phase by c.auth.phase.collectAsStateWithLifecycle()
    val needsTwoFactor by c.auth.needsTwoFactor.collectAsStateWithLifecycle()
    val needsOnboarding by c.auth.needsOnboarding.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { c.auth.check() }
    LaunchedEffect(Unit) {
        c.messages.messages.collect { m ->
            val text = if (m.detail.isNullOrBlank()) m.title else "${m.title}: ${m.detail}"
            snackbar.showSnackbar(text, withDismissAction = m.isError, duration = if (m.isError) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }
    LaunchedEffect(phase) {
        if (phase == AuthStore.Phase.SignedOut) c.router.reset()
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Surface(color = MaterialTheme.colorScheme.background) {
            when (val p = phase) {
                AuthStore.Phase.Loading -> SplashScreen()
                is AuthStore.Phase.Unreachable -> UnreachableScreen(p.message)
                AuthStore.Phase.SignedOut -> WelcomeScreen()
                AuthStore.Phase.SignedIn -> when {
                    needsTwoFactor -> Verify2FAScreen()
                    needsOnboarding -> OnboardingScreen()
                    else -> AppNavHost()
                }
            }
        }
    }
}
