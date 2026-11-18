package education.studyspot.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.WebSignIn
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.IcalAddResult
import education.studyspot.app.model.IcalFeed
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch

/**
 * The "Add your Google Calendar with a link" guide, full screen: five steps
 * (open Google Calendar, the calendar's settings, make it public, copy its
 * public URL, paste it here) and a "You're all set" screen. Opened from Settings > Google,
 * the school-IT notice and onboarding.
 */
@Composable
fun IcalGuideDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            IcalGuide(onClose = onDismiss, modifier = Modifier.safeDrawingPadding())
        }
    }
}

/** The guide's content (also rendered directly by the screenshot tests). */
@Composable
fun IcalGuide(onClose: () -> Unit, modifier: Modifier = Modifier, initialStep: Int = 0) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val isDemo = user?.isDemo == true
    val pager = rememberPagerState(initialPage = initialStep) { IcalLinks.STEPS }
    var url by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var importAll by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<IcalAddResult?>(null) }
    val page = pager.currentPage
    val navy = StudySpotTheme.colors.secondary

    fun goTo(p: Int) = scope.launch { pager.animateScrollToPage(p) }

    fun add() {
        if (busy || isDemo) return
        if (url.isBlank()) {
            error = "Paste your calendar's link first."
            return
        }
        scope.launch {
            busy = true
            error = null
            try {
                done = c.repository.addIcalFeed(url, label, if (importAll) IcalFeed.MODE_ALL else IcalFeed.MODE_AUTO)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
            } finally {
                busy = false
            }
        }
    }

    BackHandler(enabled = done == null && page > 0 && !busy) { goTo(page - 1) }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Add your Google Calendar with a link",
                style = MaterialTheme.typography.titleLarge,
                color = navy,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close", tint = navy) }
        }

        val result = done
        if (result != null) {
            DonePage(
                result = result,
                fallbackLabel = label,
                onAddAnother = {
                    done = null
                    url = ""
                    label = ""
                    importAll = false
                    error = null
                    scope.launch { pager.scrollToPage(1) }
                },
                onDone = onClose,
            )
            return@Column
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Step ${page + 1} of ${IcalLinks.STEPS}", style = MaterialTheme.typography.labelMedium, color = StudySpotTheme.colors.mutedText)
            LinearProgressIndicator(
                progress = { (page + 1f) / IcalLinks.STEPS },
                modifier = Modifier.fillMaxWidth(),
                color = StudySpotTheme.colors.accent,
                trackColor = StudySpotTheme.colors.secondaryTint,
                drawStopIndicator = {},
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            userScrollEnabled = !busy,
            verticalAlignment = Alignment.Top,
        ) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (p) {
                    0 -> StepOpenCalendar()
                    1 -> StepCalendarSettings()
                    2 -> StepMakePublic()
                    3 -> StepCopyAddress()
                    else -> StepPaste(
                        url = url,
                        onUrlChange = { url = it; error = null },
                        label = label,
                        onLabelChange = { label = it },
                        importAll = importAll,
                        onImportAllChange = { importAll = it },
                        error = error,
                        isDemo = isDemo,
                        enabled = !busy,
                        onSubmit = ::add,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { goTo(page - 1) },
                enabled = page > 0 && !busy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = navy),
            ) { Text("Back") }
            if (page < IcalLinks.STEPS - 1) {
                Button(onClick = { goTo(page + 1) }, modifier = Modifier.weight(1f)) { Text("Next") }
            } else {
                Button(onClick = ::add, enabled = !busy && !isDemo, modifier = Modifier.weight(1f)) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                        Spacer(Modifier.size(8.dp))
                        Text("Importing…")
                    } else {
                        Text("Add calendar")
                    }
                }
            }
        }
    }
}

// region Steps

@Composable
private fun StepTitle(number: Int, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(StudySpotTheme.colors.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.surface)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Body(markup: String, modifier: Modifier = Modifier) {
    Text(IcalLinks.bold(markup), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = modifier)
}

@Composable
private fun ColumnScope.StepOpenCalendar() {
    val context = LocalContext.current
    val c = LocalAppContainer.current
    Text(
        "If your school won't let Study Spot connect to your Google account, you can paste your calendar's public link instead. " +
            "Study Spot reads your calendar and imports your assignments. It takes about a minute.",
        style = MaterialTheme.typography.bodyMedium,
        color = StudySpotTheme.colors.mutedText,
    )
    StepTitle(1, "Open Google Calendar on a computer")
    Body(
        "Go to calendar.google.com in a web browser and sign in with your school account. " +
            "These settings aren't in the Google Calendar phone app. " +
            "On a phone, open the page in your browser and choose \"Desktop site\".",
    )
    Button(
        onClick = {
            if (!openExternal(context, IcalLinks.GOOGLE_CALENDAR_SETTINGS_URL)) {
                c.messages.show("Couldn't open Google Calendar", "No browser is available.", error = true)
            }
        },
        modifier = Modifier.align(Alignment.Start),
    ) {
        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Open Google Calendar")
    }
}

@Composable
private fun StepCalendarSettings() {
    StepTitle(2, "Open the calendar's settings")
    Body(
        "In the list on the left under \"My calendars\", point to the calendar that has your assignments, " +
            "click the three dots (⋮), then choose **Settings and sharing**.",
    )
    CalendarListIllustration()
    Callout(
        icon = Icons.Outlined.Lightbulb,
        tint = StudySpotTheme.colors.secondary,
        background = StudySpotTheme.colors.secondaryTint,
    ) {
        Text(
            "Each Google Classroom class has its own calendar named after the class. Your main calendar has your name. " +
                "Add each one you want, one at a time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun StepMakePublic() {
    var emailing by remember { mutableStateOf(false) }
    StepTitle(3, "Make the calendar public")
    Body(
        "Under **Access permissions for events**, turn on **Make available to public** and choose **See all event details**. " +
            "If Google shows a warning, click **OK**.",
    )
    PublicAccessIllustration()
    Callout(
        icon = Icons.Outlined.Lock,
        tint = StudySpotTheme.colors.secondary,
        background = StudySpotTheme.colors.secondaryTint,
    ) {
        Text(
            IcalLinks.bold(
                "Anyone with the link can see this calendar's events. Prefer to keep it private? " +
                    "Copy the **Secret address in iCal format** (under Integrate calendar) instead. Study Spot accepts that too.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    Callout(
        icon = Icons.Outlined.WarningAmber,
        tint = StudySpotTheme.colors.dueToday,
        background = StudySpotTheme.colors.dueToday.copy(alpha = 0.12f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Don't see this option, or it's greyed out? Your school has turned off public calendars. " +
                    "Email your school's IT team or add assignments yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(
                onClick = { emailing = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Outlined.Email, contentDescription = null, tint = StudySpotTheme.colors.secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    "Email my school's IT team",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StudySpotTheme.colors.secondary,
                )
            }
        }
    }
    if (emailing) SchoolITEmailConfirmDialog(SchoolITEmail.Service.Google, onDismiss = { emailing = false })
}

@Composable
private fun StepCopyAddress() {
    StepTitle(4, "Copy the public URL")
    Body("Scroll down to **Integrate calendar** and copy **Public URL to this calendar**.")
    Body("It starts with https://calendar.google.com/calendar/embed?src=")
    PublicUrlIllustration()
    PrivacyNote()
}

@Composable
private fun StepPaste(
    url: String,
    onUrlChange: (String) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    importAll: Boolean,
    onImportAllChange: (Boolean) -> Unit,
    error: String?,
    isDemo: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    StepTitle(5, "Paste it here")
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("Calendar link") },
        placeholder = { Text(IcalLinks.URL_PLACEHOLDER, maxLines = 1) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = {
            TextButton(onClick = { clipboardText(context)?.let(onUrlChange) }, enabled = enabled) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudySpotTheme.colors.secondary)
                Spacer(Modifier.size(4.dp))
                Text("Paste", color = StudySpotTheme.colors.secondary)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = label,
        onValueChange = onLabelChange,
        label = { Text("Name (optional)") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Import", style = MaterialTheme.typography.labelLarge, color = StudySpotTheme.colors.secondary)
        ModeOption(
            title = "Only events that look like assignments",
            description = "Homework, quizzes, tests, projects and everything from Google Classroom.",
            selected = !importAll,
            enabled = enabled,
            onClick = { onImportAllChange(false) },
        )
        ModeOption(
            title = "Every event in this calendar",
            description = null,
            selected = importAll,
            enabled = enabled,
            onClick = { onImportAllChange(true) },
        )
    }
    if (isDemo) SettingsFootnote("The demo account can't add calendars.")
    PrivacyNote()
}

@Composable
private fun ModeOption(title: String, description: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = StudySpotTheme.colors.mutedText)
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = StudySpotTheme.colors.mutedText, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Text(
            "Study Spot only reads this calendar. If you turn off public access later, Study Spot stops getting updates from it.",
            style = MaterialTheme.typography.bodySmall,
            color = StudySpotTheme.colors.mutedText,
        )
    }
}

@Composable
private fun Callout(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(background, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun DonePage(result: IcalAddResult, fallbackLabel: String, onAddAnother: () -> Unit, onDone: () -> Unit) {
    val name = result.feed?.displayName ?: fallbackLabel.trim().ifEmpty { "your calendar" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = StudySpotTheme.colors.success, modifier = Modifier.size(56.dp))
        Text("You're all set", style = MaterialTheme.typography.headlineSmall, color = StudySpotTheme.colors.secondary, modifier = Modifier.semantics { heading() })
        Text(IcalLinks.importedMessage(result.imported, name), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(
            "Study Spot checks this calendar for changes every couple of hours and whenever you sync. Add each class calendar the same way.",
            style = MaterialTheme.typography.bodyMedium,
            color = StudySpotTheme.colors.mutedText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            OutlinedButton(
                onClick = onAddAnother,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudySpotTheme.colors.secondary),
            ) { Text("Add another calendar") }
        }
    }
}

// endregion

// region Illustrations (drawn, no Google artwork)

@Composable
private fun MockWindow(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, StudySpotTheme.colors.border), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private val CalendarBlue = Color(0xFF3B82F6)
private val CalendarGreen = Color(0xFF10B981)
private val CalendarPurple = Color(0xFF8B5CF6)

/** The "My calendars" sidebar with "AP Biology ⋮" and its menu open on "Settings and sharing". */
@Composable
private fun CalendarListIllustration() {
    val muted = StudySpotTheme.colors.mutedText
    val accent = StudySpotTheme.colors.accent
    MockWindow {
        Text("My calendars", style = MaterialTheme.typography.labelLarge, color = muted)
        CalendarRow("Your name", CalendarBlue)
        CalendarRow("AP Biology", CalendarGreen, highlighted = true)
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(0.25f))
            Column(
                Modifier
                    .weight(0.75f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, StudySpotTheme.colors.border, RoundedCornerShape(10.dp))
                    .padding(vertical = 6.dp),
            ) {
                MenuItem("Display this only")
                MenuItem("Hide from list")
                MenuItem("Settings and sharing", highlighted = true)
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(CalendarBlue, CalendarGreen, CalendarPurple, accent, muted).forEach {
                        Box(Modifier.size(12.dp).background(it, CircleShape))
                    }
                }
            }
        }
        CalendarRow("World History", CalendarPurple)
    }
}

@Composable
private fun CalendarRow(name: String, color: Color, highlighted: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) StudySpotTheme.colors.secondaryTint else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(16.dp).background(color, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (highlighted) {
            Box(Modifier.size(26.dp).border(2.dp, StudySpotTheme.colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = StudySpotTheme.colors.secondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, highlighted: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
        color = if (highlighted) StudySpotTheme.colors.secondary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) StudySpotTheme.colors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** "Access permissions for events": "Make available to public" checked, "See all event details". */
@Composable
private fun PublicAccessIllustration() {
    val accent = StudySpotTheme.colors.accent
    MockWindow {
        Text("Access permissions for events", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .border(2.dp, accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(18.dp).background(StudySpotTheme.colors.secondary, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface)
            }
            Text("Make available to public", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier
                .padding(start = 28.dp)
                .border(1.dp, StudySpotTheme.colors.border, RoundedCornerShape(6.dp))
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("See all event details", style = MaterialTheme.typography.bodySmall)
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = StudySpotTheme.colors.mutedText, modifier = Modifier.size(18.dp))
        }
    }
}

/** "Integrate calendar" > "Public URL to this calendar" with the copy action ringed. */
@Composable
private fun PublicUrlIllustration() {
    val accent = StudySpotTheme.colors.accent
    MockWindow {
        Text("Integrate calendar", style = MaterialTheme.typography.titleSmall)
        Text("Public URL to this calendar", style = MaterialTheme.typography.labelLarge, color = StudySpotTheme.colors.mutedText)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                IcalLinks.URL_PLACEHOLDER,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                    .border(1.dp, StudySpotTheme.colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape)
                    .border(2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = StudySpotTheme.colors.secondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// endregion

/** Opens [url] in the browser (not a Custom Tab, so "Desktop site" is available). */
fun openExternal(context: Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        WebSignIn.openInCustomTab(context, url.toUri())
    }
}

/** The clipboard's text, trimmed, or null when it holds none. */
private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
