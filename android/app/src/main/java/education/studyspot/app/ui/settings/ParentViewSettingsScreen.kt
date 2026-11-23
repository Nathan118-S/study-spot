package education.studyspot.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.core.FlexBoolSerializer
import education.studyspot.app.core.FlexInstantSerializer
import education.studyspot.app.core.FlexIntSerializer
import education.studyspot.app.core.FlexTextSerializer
import education.studyspot.app.core.userMessage
import education.studyspot.app.ui.theme.StudySpotTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Settings > Parent view (GuardianLinksSettings in
// ios/StudySpot/Views/Settings/GuardianLinks.swift): private, read-only links
// to a weekly summary for a parent or guardian. The parent page itself is on
// the website (/parent/<token>); the full link is only shown once, when made.

@Serializable
data class GuardianLink(
    @Serializable(with = FlexTextSerializer::class) val id: String = "",
    @Serializable(with = FlexTextSerializer::class) val label: String = "",
    @Serializable(with = FlexBoolSerializer::class) @SerialName("show_grades") val showGrades: Boolean = false,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("created_at") val createdAt: Instant? = null,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("last_viewed_at") val lastViewedAt: Instant? = null,
)

@Serializable
data class GuardianLinkList(
    val links: List<GuardianLink> = emptyList(),
    @Serializable(with = FlexIntSerializer::class) val max: Int = 5,
)

@Serializable
data class CreatedGuardianLink(val link: GuardianLink = GuardianLink(), @Serializable(with = FlexTextSerializer::class) val url: String = "")

@Serializable
private data class GuardianLinkUpdate(val link: GuardianLink = GuardianLink())

private data class NewLink(val id: String, val label: String, val url: String)

@Composable
fun ParentViewSettingsScreen(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val isDemo = user?.isDemo == true

    var links by remember { mutableStateOf<List<GuardianLink>>(emptyList()) }
    var maxLinks by remember { mutableIntStateOf(5) }
    var loaded by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var showGrades by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf<NewLink?>(null) }
    var revoking by remember { mutableStateOf<GuardianLink?>(null) }

    suspend fun load() {
        if (isDemo) { loaded = true; return }
        try {
            val res = c.api.invokeAs<GuardianLinkList>("guardianListLinks")
            links = res.links
            maxLinks = res.max
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            c.messages.show("Couldn't load parent links", e.userMessage, error = true)
        }
        loaded = true
    }

    LaunchedEffect(isDemo) { load() }

    fun create() {
        val name = label.trim()
        if (name.isEmpty()) return
        scope.launch {
            creating = true
            try {
                val res = c.api.invokeAs<CreatedGuardianLink>("guardianCreateLink", buildJsonObject {
                    put("label", name)
                    put("show_grades", showGrades)
                })
                created = NewLink(res.link.id, res.link.label, res.url)
                label = ""
                showGrades = false
                load()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                c.messages.show("Couldn't create the link", e.userMessage, error = true)
            } finally {
                creating = false
            }
        }
    }

    fun setGrades(link: GuardianLink, on: Boolean) {
        scope.launch {
            busyId = link.id
            links = links.map { if (it.id == link.id) it.copy(showGrades = on) else it }
            try {
                val res = c.api.invokeAs<GuardianLinkUpdate>("guardianUpdateLink", buildJsonObject {
                    put("id", link.id)
                    put("show_grades", on)
                })
                links = links.map { if (it.id == link.id) res.link else it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                c.messages.show("Couldn't update the link", e.userMessage, error = true)
                load()
            } finally {
                busyId = null
            }
        }
    }

    fun revoke(link: GuardianLink) {
        scope.launch {
            busyId = link.id
            try {
                c.api.invoke("guardianRevokeLink", buildJsonObject { put("id", link.id) })
                if (created?.id == link.id) created = null
                load()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                c.messages.show("Couldn't turn the link off", e.userMessage, error = true)
            } finally {
                busyId = null
            }
        }
    }

    SettingsColumn(modifier) {
        SettingsSection(footer = if (isDemo) ({ SettingsFootnote("Parent links aren't available on the demo account.") }) else null) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.People, contentDescription = null, tint = StudySpotTheme.colors.secondary, modifier = Modifier.padding(top = 2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Give a parent or guardian a private, read-only link to your week: what's due and overdue, what you " +
                            "finished in the last 7 days, your streak and your study time. Grades are only included if you turn them on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "They don't need an account, and you can turn a link off at any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        created?.let { link ->
            SettingsSection(
                header = "Link for ${link.label} is ready",
                footer = { SettingsFootnote("Copy or share it now: for privacy, Study Spot can't show it again.") },
            ) {
                Text(
                    link.url,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                SettingsActions {
                    BusyButton(
                        "Copy link",
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Study Spot parent link", link.url)))
                                c.messages.show("Link copied")
                            }
                        },
                        prominent = false,
                        icon = Icons.Outlined.ContentCopy,
                    )
                    BusyButton("Share", onClick = { shareLink(context, link.url) }, icon = Icons.Outlined.Share)
                }
            }
        }

        if (links.isNotEmpty()) {
            SettingsSection(header = "Your parent links") {
                links.forEachIndexed { i, link ->
                    if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(link.label, fontWeight = FontWeight.Medium)
                                Text(
                                    viewedLine(link),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (busyId == link.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            TextButton(onClick = { revoking = link }) {
                                Text("Turn off", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Show grades", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = link.showGrades, onCheckedChange = { setGrades(link, it) }, enabled = busyId != link.id)
                        }
                    }
                }
            }
        }

        if (loaded && links.size < maxLinks) {
            SettingsSection(header = "New parent link") {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    placeholder = { Text("Who is it for? e.g. Mom") },
                    singleLine = true,
                    enabled = !isDemo,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { create() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SettingsToggle(
                    title = "Show grades",
                    description = "Your grade in each class, from scored assignments.",
                    icon = Icons.Outlined.School,
                    checked = showGrades,
                    onCheckedChange = { showGrades = it },
                    enabled = !isDemo,
                )
                SettingsActions {
                    BusyButton(
                        "Create link",
                        onClick = ::create,
                        busy = creating,
                        enabled = !isDemo && label.isNotBlank(),
                        icon = Icons.Outlined.Add,
                    )
                }
            }
        } else if (loaded) {
            SettingsFootnote(
                "You have $maxLinks parent links, the most you can have. Turn one off to add another.",
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    revoking?.let { link ->
        ConfirmDialog(
            title = "Turn off ${link.label}'s link?",
            message = "The link stops working right away. You can make a new one later.",
            confirmLabel = "Turn off",
            onConfirm = { revoke(link) },
            onDismiss = { revoking = null },
        )
    }
}

private fun viewedLine(link: GuardianLink): String {
    val at = link.lastViewedAt ?: return "Not opened yet"
    val mins = Duration.between(at, Instant.now()).toMinutes().coerceAtLeast(0)
    val ago = when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 1440 -> "${(mins + 30) / 60} h ago"
        else -> "${(mins + 720) / 1440} d ago"
    }
    return "Last opened $ago"
}

private fun shareLink(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "My Study Spot weekly summary")
        putExtra(Intent.EXTRA_TEXT, "Here's my weekly Study Spot summary: $url")
    }
    val chooser = Intent.createChooser(send, "Share parent link")
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // No app can receive it; the link can still be copied.
    }
}
