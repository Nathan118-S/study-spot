package education.studyspot.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.ui.theme.ColorTheme
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Settings > Appearance > Theme: a swatch per [ColorTheme]. Applies at once,
 * is cached on-device (data.ThemeStore) and saved to user.data.theme so the
 * website and iOS follow.
 */
@Composable
fun ColorThemePicker(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settings by c.theme.settings.collectAsStateWithLifecycle()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val current = ColorTheme.fromId(settings.colorTheme)
    val dark = StudySpotTheme.colors.isDark

    fun choose(t: ColorTheme) {
        if (t == current) return
        c.theme.setColorTheme(t.id)
        if (user == null || user?.isDemo == true) return
        scope.launch {
            runCatching { c.repository.updateUser(buildJsonObject { put("theme", t.id) }) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorTheme.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    ThemeSwatch(t, dark, selected = t == current, onClick = { choose(t) }, modifier = Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tiny screen in the theme's colours: base, a card with an ink title, an accent button. */
@Composable
private fun ThemeSwatch(theme: ColorTheme, dark: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = theme.accent(dark)
    val ink = theme.ink(dark)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                if (selected) BorderStroke(1.5.dp, accent) else BorderStroke(0.5.dp, StudySpotTheme.colors.border),
                shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = "${theme.label} theme" }
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.base(dark))
                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.width(26.dp).height(5.dp).clip(CircleShape).background(ink))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(theme.surface(dark))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(18.dp).height(4.dp).clip(CircleShape).background(ink.copy(alpha = 0.5f)))
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(20.dp).height(11.dp).clip(CircleShape).background(accent))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Text(
                theme.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Settings > Appearance > App icon: switches the enabled launcher alias
 * ([AppIcons]). Per device, so not synced.
 */
@Composable
fun AppIconPicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcons.Icon.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { icon ->
                    val selected = icon == current
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton) {
                                if (!selected) {
                                    AppIcons.set(context, icon)
                                    current = icon
                                }
                            }
                            .semantics { this.selected = selected; contentDescription = "${icon.label} icon" }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .border(2.5.dp, if (selected) StudySpotTheme.colors.accent else Color.Transparent, CircleShape)
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AdaptiveIconPreview(icon, Modifier.size(54.dp))
                        }
                        Text(
                            icon.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else StudySpotTheme.colors.mutedText,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Draws an adaptive icon like a circular launcher mask: background, then the 108dp foreground layer cropped to the middle. */
@Composable
private fun AdaptiveIconPreview(icon: AppIcons.Icon, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(colorResource(icon.background))
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // The visible part of an adaptive icon is roughly the middle 72dp of 108dp.
        Image(painterResource(icon.foreground), contentDescription = null, modifier = Modifier.requiredSize(54.dp * 1.5f))
    }
}
