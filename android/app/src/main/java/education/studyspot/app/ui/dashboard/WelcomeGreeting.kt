package education.studyspot.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import education.studyspot.app.model.User
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.delay

/**
 * The "Hello, name!" greeting shown once per app launch after sign-in
 * (iOS RootView.WelcomeOverlay), styled by user.data.welcome_style:
 * "large" (full-screen), "small" (banner) or "off".
 */
object WelcomeGreeting {
    const val DURATION_MS = 2800L

    /** Once per launch (AuthStore.hasGreetedThisLaunch), after onboarding, unless turned off. */
    fun shouldShow(user: User?, alreadyGreeted: Boolean): Boolean =
        user != null && !alreadyGreeted && (user.onboardingCompleted || user.isDemo) && user.welcomeStyle != "off"
}

@Composable
fun WelcomeGreetingHost(user: User, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(WelcomeGreeting.DURATION_MS)
        onDone()
    }
    val name = user.displayName
    if (user.welcomeStyle == "small") SmallGreeting(name, onDone) else LargeGreeting(name, onDone)
}

@Composable
private fun rememberAppear(): MutableTransitionState<Boolean> {
    val animate = StudySpotTheme.animations
    return remember { MutableTransitionState(!animate).apply { targetState = true } }
}

@Composable
private fun LargeGreeting(name: String, onDone: () -> Unit) {
    val colors = StudySpotTheme.colors
    val appear = rememberAppear()
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.base.copy(alpha = 0.92f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn() + scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = 0.6f)) +
                    slideInVertically { it / 6 },
            ) {
                Column(
                    Modifier.padding(32.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("👋", fontSize = 56.sp)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.accent)) { append("Hello") }
                            withStyle(SpanStyle(color = colors.secondary)) { append(if (name.isEmpty()) "!" else ", $name!") }
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text("Welcome back to Study Spot", style = MaterialTheme.typography.bodyLarge, color = colors.mutedText)
                }
            }
        }
    }
}

@Composable
private fun SmallGreeting(name: String, onDone: () -> Unit) {
    val colors = StudySpotTheme.colors
    val appear = rememberAppear()
    AnimatedVisibility(
        visibleState = appear,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Card(
            onClick = onDone,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("👋", fontSize = 28.sp)
                Column {
                    Text(
                        "Hello" + if (name.isEmpty()) "!" else ", $name!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondary,
                    )
                    Text("Welcome back to Study Spot", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
                }
            }
        }
    }
}
