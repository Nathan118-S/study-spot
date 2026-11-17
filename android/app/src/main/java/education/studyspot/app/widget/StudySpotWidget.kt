package education.studyspot.app.widget

import androidx.core.content.edit
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import education.studyspot.app.appContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Up Next" home-screen widget (ios/StudySpotWidgets/UpNextWidget.swift):
 * the next assignments with what's due today and overdue. Small shows the
 * next one, medium two, large six plus weekly stats. Tapping an
 * assignment opens it (studyspot://assignment/<id>).
 *
 * (Kept as StudySpotWidget / StudySpotWidgetReceiver, the names the manifest uses.)
 */
class StudySpotWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))
    override val previewSizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.load(context)
        val today = todayLabel()
        provideContent { Content(context, snapshot, System.currentTimeMillis(), today) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val today = todayLabel()
        provideContent { Content(context, WidgetSnapshot.preview(), System.currentTimeMillis(), today) }
    }

    @Composable
    @VisibleForTesting
    internal fun Content(context: Context, snap: WidgetSnapshot, now: Long, today: String) {
        GlanceTheme(colors = WidgetColors.providers) {
            val size = LocalSize.current
            val root = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(20.dp).padding(14.dp)
            when {
                !snap.signedIn -> Column(root.clickableLink(context, WidgetLinks.DASHBOARD)) { SignedOut() }
                size.height >= LARGE.height -> Large(context, snap, now, today, root)
                size.width >= MEDIUM.width -> Medium(context, snap, now, root)
                else -> Small(context, snap, now, root)
            }
        }
    }

    @Composable
    private fun Small(context: Context, snap: WidgetSnapshot, now: Long, modifier: GlanceModifier) {
        val next = snap.upcoming.firstOrNull()
        Column(modifier.clickableLink(context, next?.let { WidgetLinks.assignment(it.id) } ?: WidgetLinks.DASHBOARD)) {
            WidgetHeader("Up Next")
            Spacer(GlanceModifier.defaultWeight())
            if (next == null) {
                AllCaughtUp()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(colorOf(next.classColor))
                    Spacer(GlanceModifier.width(5.dp))
                    Text(next.className, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
                }
                // One line: at the 110 dp minimum height a second line pushes the counts out of view.
                Text(next.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(
                    dueText(next, now),
                    style = TextStyle(
                        color = if (next.isOverdue(now)) WidgetColors.danger else WidgetColors.accent,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "${snap.dueToday(now).size} due · ${snap.overdue(now).size} overdue",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun Medium(context: Context, snap: WidgetSnapshot, now: Long, modifier: GlanceModifier) {
        Row(modifier) {
            Column(GlanceModifier.width(86.dp).clickableLink(context, WidgetLinks.DASHBOARD)) {
                WidgetHeader("Up Next")
                Spacer(GlanceModifier.defaultWeight())
                Stat("${snap.dueToday(now).size}", "Due today", WidgetColors.accent)
                // A compact line: a second big stat doesn't fit the 82 dp of content height.
                Text(
                    "${snap.overdue(now).size} overdue",
                    style = TextStyle(color = WidgetColors.danger, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                if (snap.upcoming.isEmpty()) {
                    AllCaughtUp()
                } else {
                    // Two rows fill the medium height; a third would be cut in half.
                    snap.upcoming.take(2).forEach { a ->
                        AssignmentLine(context, a, now)
                        Spacer(GlanceModifier.height(8.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun Large(context: Context, snap: WidgetSnapshot, now: Long, today: String, modifier: GlanceModifier) {
        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().clickableLink(context, WidgetLinks.DASHBOARD)) {
                WidgetHeader("Up Next")
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    today,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                StatBox("${snap.dueToday(now).size}", "Due today", WidgetColors.accent)
                Spacer(GlanceModifier.width(6.dp))
                StatBox("${snap.overdue(now).size}", "Overdue", WidgetColors.danger)
                Spacer(GlanceModifier.width(6.dp))
                StatBox("${snap.dueThisWeek(now).size}", "This week", WidgetColors.dueToday)
                Spacer(GlanceModifier.width(6.dp))
                StatBox("${snap.completedThisWeek}", "Done", WidgetColors.success)
            }
            Spacer(GlanceModifier.height(10.dp))
            if (snap.upcoming.isEmpty()) {
                AllCaughtUp()
            } else {
                snap.upcoming.take(6).forEach { a ->
                    AssignmentLine(context, a, now)
                    Spacer(GlanceModifier.height(7.dp))
                }
            }
        }
    }

    @Composable
    private fun Stat(value: String, label: String, color: ColorProvider) {
        Column {
            Text(value, style = TextStyle(color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.StatBox(value: String, label: String, color: ColorProvider) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.defaultWeight().background(GlanceTheme.colors.surfaceVariant).cornerRadius(10.dp).padding(vertical = 6.dp),
        ) {
            Text(value, style = TextStyle(color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
        }
    }

    /** "Tuesday, Nov 17" (formatted outside composition). */
    private fun todayLabel(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

class StudySpotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudySpotWidget()
}

/** Keeps the widgets' snapshot current and redraws placed widgets. */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"

    /**
     * Writes the snapshot from the app's current data and redraws the
     * widgets. Called after loads, on data changes and on sign-out. While
     * signed in but not loaded yet, the last snapshot is kept.
     */
    suspend fun refresh(context: Context) {
        val c = context.appContainer
        val user = c.repository.user.value
        val snapshot = when {
            user == null && c.api.token == null -> WidgetSnapshot.signedOut
            user == null || !c.repository.hasLoaded.value -> null
            else -> WidgetSnapshot.build(
                user, c.repository.assignments.value, c.repository.classes.value, c.repository.connections.value,
            )
        }
        val changed = snapshot != null && WidgetSnapshotStore.save(context, snapshot)
        if (changed) redraw(context)
    }

    /** Redraws every placed widget from the saved snapshot. */
    suspend fun redraw(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            if (manager.getGlanceIds(StudySpotWidget::class.java).isNotEmpty()) StudySpotWidget().updateAll(context)
            if (manager.getGlanceIds(StreakWidget::class.java).isNotEmpty()) StreakWidget().updateAll(context)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Widget update failed", e)
        }
    }

    /** Generated previews in the widget picker (Android 15+). Rate limited by the system, so once per app version. */
    suspend fun publishPreviews(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val version = education.studyspot.app.BuildConfig.VERSION_CODE
        if (prefs.getInt("previews_version", -1) == version) return
        try {
            val manager = GlanceAppWidgetManager(context)
            val a = manager.setWidgetPreviews(StudySpotWidgetReceiver::class)
            val b = manager.setWidgetPreviews(StreakWidgetReceiver::class)
            if (a == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS && b == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                prefs.edit { putInt("previews_version", version) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Couldn't publish widget previews", e)
        }
    }
}
