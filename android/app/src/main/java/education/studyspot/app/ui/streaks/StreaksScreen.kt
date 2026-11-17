package education.studyspot.app.ui.streaks

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.userMessage
import education.studyspot.app.data.DemoData
import education.studyspot.app.logic.ClassStreak
import education.studyspot.app.logic.Streaks
import education.studyspot.app.model.Leaderboard
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.navigation.AppTab
import education.studyspot.app.ui.components.EmptyState
import education.studyspot.app.ui.components.IconStatTile
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.components.ProgressBar
import education.studyspot.app.ui.components.color
import education.studyspot.app.ui.components.flameColor
import education.studyspot.app.ui.components.parseHexColor
import education.studyspot.app.ui.settings.SettingsPage
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch

private const val PREFS = "streaks"
private const val UNVERIFIED_DISMISSED = "cf-streaks-unverified-dismissed"

/**
 * Streaks tab (ios/StudySpot/Views/Streaks/StreaksView.swift): per-class
 * streaks (logic.Streaks), verified / unverified, active / at-risk / broken,
 * best streaks, summary tiles and the opt-in leaderboard. Demo accounts see
 * DemoData's precomputed streaks and leaderboard.
 */
@Composable
fun StreaksScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    val c = LocalAppContainer.current
    val repo = c.repository
    val user by repo.user.collectAsStateWithLifecycle()
    val classes by repo.classes.collectAsStateWithLifecycle()
    val assignments by repo.assignments.collectAsStateWithLifecycle()
    val connections by repo.connections.collectAsStateWithLifecycle()
    val isLoading by repo.isLoading.collectAsStateWithLifecycle()
    val hasLoaded by repo.hasLoaded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    val isDemo = user?.isDemo == true
    var refreshing by remember { mutableStateOf(false) }
    var leaderboardReload by remember { mutableIntStateOf(0) }

    val streaks = remember(isDemo, classes, assignments, connections) {
        if (isDemo) DemoData.streaks else Streaks.build(classes, assignments, connections)
    }
    val summary = if (isDemo) DemoData.streakSummary else Streaks.summary(streaks)

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                repo.reloadData()
                repo.refreshConnections()
                leaderboardReload++
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    "Complete every assignment in a class to keep its streak alive. One overdue assignment breaks it. " +
                        "Streaks from synced Google Classroom and Blackboard classes are verified; manual classes are unverified.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
            }
            item(key = "status") {
                VerificationBox(
                    classroom = connections.classroom,
                    blackboard = connections.blackboard,
                    isDemo = isDemo,
                    onConnect = { navigator.openSettings(SettingsPage.Google) },
                )
            }
            item(key = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconStatTile("Active streaks", "${summary.active}", Icons.Filled.LocalFireDepartment, colors.accent, Modifier.weight(1f))
                        IconStatTile("At risk", "${summary.atRisk}", Icons.Filled.GppMaybe, colors.dueToday, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconStatTile("Broken", "${summary.broken}", Icons.Filled.Warning, colors.danger, Modifier.weight(1f))
                        IconStatTile("Best streak", "${summary.best}", Icons.Filled.EmojiEvents, androidx.compose.ui.graphics.Color(0xFFE6B400), Modifier.weight(1f))
                    }
                }
            }
            when {
                isLoading && !hasLoaded && !isDemo -> item(key = "loading") { LoadingBox() }
                streaks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        Icons.Outlined.School,
                        "No classes yet",
                        message = "Add a class to start a streak.",
                        action = {
                            Button(
                                onClick = { navigator.selectTab(AppTab.Classes) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            ) { Text("Add a class") }
                        },
                    )
                }
                else -> items(streaks, key = { it.id }) { StreakCard(it) }
            }
            item(key = "leaderboard") {
                if (user?.leaderboardEnabled == true || isDemo) {
                    LeaderboardCard(isDemo = isDemo, reloadKey = leaderboardReload)
                } else {
                    LeaderboardInvite(onOpen = { navigator.openSettings(SettingsPage.Leaderboard) })
                }
            }
        }
    }
}

@Composable
private fun VerificationBox(classroom: Boolean, blackboard: Boolean, isDemo: Boolean, onConnect: () -> Unit) {
    val colors = StudySpotTheme.colors
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(UNVERIFIED_DISMISSED, false)) }

    if (classroom || blackboard || isDemo) {
        val sources = listOfNotNull(
            if (classroom || isDemo) "Google Classroom" else null,
            if (blackboard || isDemo) "Blackboard" else null,
        )
        StatusCard(
            icon = { Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = colors.success) },
            title = "Verified",
            titleColor = colors.success,
            text = "${sources.joinToString(" and ")} ${if (sources.size > 1) "are" else "is"} connected. Streaks from synced classes are verified.",
        )
    } else if (!dismissed) {
        StatusCard(
            icon = { Icon(Icons.Outlined.GppMaybe, contentDescription = null, tint = colors.dueToday) },
            title = "Unverified",
            titleColor = colors.dueToday,
            text = "Neither Google Classroom nor Blackboard is connected. You can continue with streaks based on your manual classes, but your streaks can not be verified.",
            action = { TextButton(onClick = onConnect, contentPadding = PaddingValues(0.dp)) { Text("Connect", fontWeight = FontWeight.SemiBold) } },
            onDismiss = {
                dismissed = true
                prefs.edit { putBoolean(UNVERIFIED_DISMISSED, true) }
            },
        )
    }
}

@Composable
private fun StatusCard(
    icon: @Composable () -> Unit,
    title: String,
    titleColor: androidx.compose.ui.graphics.Color,
    text: String,
    action: (@Composable () -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = titleColor.copy(alpha = 0.08f)),
        border = BorderStroke(0.5.dp, titleColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 2.dp)) { icon() }
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = StudySpotTheme.colors.mutedText)
                action?.invoke()
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = StudySpotTheme.colors.mutedText) }
            }
        }
    }
}

@Composable
private fun StreakCard(s: ClassStreak) {
    val colors = StudySpotTheme.colors
    // A small bounce whenever the streak grows (iOS .symbolEffect(.bounce)).
    var lastStreak by remember { mutableIntStateOf(s.streak) }
    var bump by remember { mutableStateOf(false) }
    LaunchedEffect(s.streak) {
        if (s.streak > lastStreak) bump = true
        lastStreak = s.streak
        kotlinx.coroutines.delay(250)
        bump = false
    }
    val scale by animateFloatAsState(
        if (bump) 1.25f else 1f,
        animationSpec = if (StudySpotTheme.animations) spring(dampingRatio = Spring.DampingRatioMediumBouncy) else snap(),
        label = "flame",
    )
    val best = maxOf(s.schoolClass.bestStreak, s.streak)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Name and verified pill take the room left by the status pill; the name truncates last.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(s.schoolClass.color)))
                    Text(
                        s.schoolClass.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (s.verified) {
                        Pill("Verified", color = colors.success, icon = Icons.Outlined.Verified)
                    } else {
                        Pill("Unverified", color = colors.dueToday, icon = Icons.Outlined.GppMaybe)
                    }
                }
                Pill(s.status.label, color = s.status.color())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = s.status.flameColor(),
                    modifier = Modifier.size(38.dp).scale(scale),
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Text("${s.streak}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.secondary)
                    Text("streak", style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
                }
                Box(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${s.completed}/${s.total} done", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
                    if (best > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(14.dp))
                            Text(" Best $best", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                        }
                    }
                }
            }
            if (s.total > 0) ProgressBar(s.completed * 100.0 / s.total)
        }
    }
}

@Composable
private fun LeaderboardCard(isDemo: Boolean, reloadKey: Int) {
    val c = LocalAppContainer.current
    val colors = StudySpotTheme.colors
    var board by remember { mutableStateOf<Leaderboard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val medals = listOf("🥇", "🥈", "🥉")

    LaunchedEffect(isDemo, reloadKey) {
        loading = board == null
        if (isDemo) {
            board = DemoData.leaderboard
        } else {
            try {
                board = c.api.invokeAs<Leaderboard>("getLeaderboard")
                error = null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
            }
        }
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
            Text(
                " Leaderboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.secondary,
                modifier = Modifier.semantics { heading() },
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val b = board
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                b == null || error != null && b.entries.isEmpty() -> Text(
                    error ?: "Couldn't load the leaderboard.",
                    color = colors.mutedText,
                    modifier = Modifier.padding(14.dp),
                )
                b.entries.isEmpty() -> Text(
                    "No streaks yet. Complete assignments to climb the board!",
                    color = colors.mutedText,
                    modifier = Modifier.padding(14.dp),
                )
                else -> Column {
                    b.entries.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = colors.border)
                        val isMe = e.userId == b.currentUserId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isMe) colors.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (i < 3) medals[i] else "${i + 1}",
                                modifier = Modifier.width(32.dp),
                                color = colors.mutedText,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    e.name.ifBlank { "Student" },
                                    fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (isMe) Text(" (you)", style = MaterialTheme.typography.labelMedium, color = colors.accent, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Text(" ${e.bestStreak}", color = colors.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardInvite(onOpen: () -> Unit) {
    val colors = StudySpotTheme.colors
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = colors.secondaryTint),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = colors.secondary)
            Column(Modifier.weight(1f)) {
                Text("Leaderboard", style = MaterialTheme.typography.titleSmall, color = colors.secondary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Compete with other students by showing the top study streaks. Turn it on in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                )
            }
        }
    }
}
