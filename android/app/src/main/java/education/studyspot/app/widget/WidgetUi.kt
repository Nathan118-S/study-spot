package education.studyspot.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import education.studyspot.app.MainActivity
import education.studyspot.app.R
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.theme.BaseDark
import education.studyspot.app.ui.theme.BaseLight
import education.studyspot.app.ui.theme.DueTodayDark
import education.studyspot.app.ui.theme.DueTodayLight
import education.studyspot.app.ui.theme.GreenDark
import education.studyspot.app.ui.theme.GreenLight
import education.studyspot.app.ui.theme.InkDark
import education.studyspot.app.ui.theme.InkLight
import education.studyspot.app.ui.theme.MutedInkDark
import education.studyspot.app.ui.theme.MutedInkLight
import education.studyspot.app.ui.theme.NavyDark
import education.studyspot.app.ui.theme.NavyLight
import education.studyspot.app.ui.theme.NavyTintDark
import education.studyspot.app.ui.theme.NavyTintLight
import education.studyspot.app.ui.theme.Orange
import education.studyspot.app.ui.theme.RedDark
import education.studyspot.app.ui.theme.RedLight
import education.studyspot.app.ui.theme.SurfaceDark
import education.studyspot.app.ui.theme.SurfaceLight
import java.time.Instant

/** Study Spot palette for the widgets, light and dark (follows the system theme). */
internal object WidgetColors {
    val providers = ColorProviders(
        light = lightColorScheme(
            primary = Orange, onPrimary = Color.White,
            secondary = NavyLight, onSecondary = Color.White,
            background = BaseLight, onBackground = InkLight,
            surface = SurfaceLight, onSurface = InkLight,
            surfaceVariant = NavyTintLight, onSurfaceVariant = MutedInkLight,
            error = RedLight,
        ),
        dark = darkColorScheme(
            primary = Orange, onPrimary = Color.White,
            secondary = NavyDark, onSecondary = Color.Black,
            background = BaseDark, onBackground = InkDark,
            surface = SurfaceDark, onSurface = InkDark,
            surfaceVariant = NavyTintDark, onSurfaceVariant = MutedInkDark,
            error = RedDark,
        ),
    )
    val accent: ColorProvider = ColorProvider(Orange, Orange)
    val danger: ColorProvider = ColorProvider(RedLight, RedDark)
    val dueToday: ColorProvider = ColorProvider(DueTodayLight, DueTodayDark)
    val success: ColorProvider = ColorProvider(GreenLight, GreenDark)
    val streak: ColorProvider = ColorProvider(DueTodayLight, DueTodayDark)
}

/** Opens the app at a studyspot:// link. */
internal fun openLink(context: Context, link: String): Action =
    actionStartActivity(
        Intent(Intent.ACTION_VIEW, link.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

internal object WidgetLinks {
    fun assignment(id: String) = "studyspot://assignment/$id"
    const val DASHBOARD = "studyspot://tab/dashboard"
    const val STREAKS = "studyspot://tab/streaks"
}

internal fun colorOf(hex: String?): ColorProvider {
    val c = Color(education.studyspot.app.liveupdates.LiveUpdates.parseColor(hex, 0xFF94A3B8.toInt()))
    return ColorProvider(c, c)
}

/** "Overdue", "Today, 3:00 PM", "Tomorrow, …", "Mar 4, …". */
internal fun dueText(a: WidgetAssignment, nowMillis: Long): String = when {
    a.dueMillis == null -> "No due date"
    a.isOverdue(nowMillis) -> "Overdue · " + DateText.short(Instant.ofEpochMilli(a.dueMillis))
    else -> DateText.short(Instant.ofEpochMilli(a.dueMillis))
}

@Composable
internal fun Dot(color: ColorProvider, size: Dp = 7.dp) {
    Box(GlanceModifier.size(size).cornerRadius(size / 2).background(color)) {}
}

@Composable
internal fun WidgetHeader(title: String, icon: Int = R.drawable.ic_stat_notification, tint: ColorProvider = WidgetColors.accent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(16.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(title, style = TextStyle(color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/** One assignment row: class-colored dot, title, class and due date. */
@Composable
internal fun AssignmentLine(context: Context, a: WidgetAssignment, nowMillis: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().clickableLink(context, WidgetLinks.assignment(a.id)),
    ) {
        Dot(colorOf(a.classColor), 8.dp)
        Spacer(GlanceModifier.width(6.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                a.title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                "${dueText(a, nowMillis)} · ${a.className}",
                style = TextStyle(
                    color = if (a.isOverdue(nowMillis)) WidgetColors.danger else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun AllCaughtUp() {
    Column {
        Image(ImageProvider(R.drawable.ic_widget_check), contentDescription = null, modifier = GlanceModifier.size(24.dp))
        Spacer(GlanceModifier.height(4.dp))
        Text("All caught up", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        Text("Nothing left to do.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
    }
}

@Composable
internal fun SignedOut() {
    Column {
        WidgetHeader("Study Spot")
        Spacer(GlanceModifier.height(6.dp))
        Text("Sign in to see your assignments.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
    }
}

internal fun GlanceModifier.clickableLink(context: Context, link: String): GlanceModifier =
    clickable(openLink(context, link))
