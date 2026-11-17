package education.studyspot.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import education.studyspot.app.ui.classes.ClassFormSheet
import education.studyspot.app.ui.components.LogoMark
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private enum class Step(val title: String) { Welcome("Welcome"), Services("Services"), Classes("Classes"), Done("All set") }

/**
 * First-run setup wizard, shown by AppRoot while `auth.needsOnboarding`
 * (ios/StudySpot/Views/Onboarding/OnboardingView.swift): welcome, services
 * (Google / Blackboard), classes, done. "Skip setup" finishes at any step;
 * finishing PATCHes `{ onboarding_completed: true }`. Everything can be changed
 * later in Settings.
 */
@Composable
fun OnboardingScreen() {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    val animations = StudySpotTheme.animations
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    var finishing by remember { mutableStateOf(false) }

    fun go(delta: Int) {
        Step.entries.getOrNull(step.ordinal + delta)?.let { step = it }
    }
    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            try {
                c.repository.updateUser(buildJsonObject { put("onboarding_completed", true) })
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                finishing = false
                c.messages.show("Couldn't finish setup", e.userMessage, error = true)
            }
        }
    }

    BackHandler(enabled = step != Step.Welcome) { go(-1) }

    SubScreenScaffold(
        title = step.title,
        onBack = null,
        actions = { TextButton(onClick = ::finish, enabled = !finishing) { Text("Skip setup", color = colors.secondary) } },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { go(-1) }, enabled = step != Step.Welcome && !finishing) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Text("  Back")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (step == Step.Done) finish() else go(1) },
                    enabled = !finishing,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) {
                    when {
                        finishing -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onAccent)
                        step == Step.Welcome -> Text("Get started")
                        step == Step.Done -> {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Finish")
                        }
                        else -> {
                            Text("Next  ")
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Progress capsules.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Step.entries.forEach { s ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(if (s.ordinal <= step.ordinal) colors.accent else colors.secondary.copy(alpha = 0.15f)),
                    )
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (!animations) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "step",
                modifier = Modifier.weight(1f),
            ) { s ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        when (s) {
                            Step.Welcome -> WelcomeStep()
                            Step.Services -> OnboardingServicesStep()
                            Step.Classes -> ClassesStep()
                            Step.Done -> DoneStep()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val colors = StudySpotTheme.colors
    val animate = StudySpotTheme.animations
    val transition = rememberInfiniteTransition(label = "logo")
    val t by if (animate) {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "bounce")
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LogoMark(
            96.dp,
            Modifier.graphicsLayer {
                translationY = -8.dp.toPx() * t
                rotationZ = 6f - 12f * t
            },
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.secondary)) { append("Welcome to ") }
                withStyle(SpanStyle(color = colors.accent)) { append("Study Spot") }
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Let's set up your account in a few quick steps. You can change any of these later in Settings.",
            textAlign = TextAlign.Center,
            color = colors.mutedText,
        )
    }
}

@Composable
private fun ClassesStep() {
    val c = LocalAppContainer.current
    val classes by c.repository.classes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Add the classes you take. Each gets a color, and you can add meeting times for a countdown before class. " +
                "Connected Google Classroom and Blackboard classes are added for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mutedText,
        )
        if (classes.isEmpty()) {
            Text("No classes yet.", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
        }
        classes.forEach { cls ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(parseHexColor(cls.color)))
                    Text(cls.displayName, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (cls.schedule.isNotEmpty()) {
                        Text("${cls.schedule.size}× / week", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                    }
                }
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AddCircle, contentDescription = null, tint = colors.accent)
            Text("  Add a class", color = colors.secondary)
        }
    }

    if (adding) {
        ClassFormSheet(
            schoolClass = null,
            onDismiss = { adding = false },
            onSave = { fields ->
                scope.launch {
                    if (c.repository.createClass(JsonObject(fields + ("source" to JsonPrimitive("manual")))) != null) adding = false
                }
            },
        )
    }
}

@Composable
private fun DoneStep() {
    val colors = StudySpotTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(colors.success.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(48.dp))
        }
        Text(
            "You're all set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.secondary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Add assignments from the dashboard with +, track streaks per class, and see your progress in Insights. " +
                "Appearance, reminders, the leaderboard, grading and security are in Settings.",
            textAlign = TextAlign.Center,
            color = colors.mutedText,
        )
    }
}
