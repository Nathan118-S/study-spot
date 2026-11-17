package education.studyspot.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import education.studyspot.app.R

/**
 * "Streak" widget (ios/StudySpotWidgets/StreakWidget.swift): the best class
 * streak and how many are active or at risk. Opens the Streaks tab.
 */
class StreakWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SIZE))
    override val previewSizeMode = SizeMode.Responsive(setOf(SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.load(context)
        provideContent { Content(context, snapshot) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { Content(context, WidgetSnapshot.preview()) }
    }

    @Composable
    private fun Content(context: Context, snap: WidgetSnapshot) {
        GlanceTheme(colors = WidgetColors.providers) {
            val root = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(20.dp).padding(14.dp)
            if (!snap.signedIn) {
                Column(root.clickableLink(context, WidgetLinks.DASHBOARD)) { SignedOut() }
                return@GlanceTheme
            }
            Column(root.clickableLink(context, WidgetLinks.STREAKS)) {
                WidgetHeader("Streak", R.drawable.ic_widget_flame, WidgetColors.streak)
                Spacer(GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_widget_flame), contentDescription = null, modifier = GlanceModifier.size(30.dp))
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        "${snap.bestStreak}",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 38.sp, fontWeight = FontWeight.Bold),
                    )
                }
                val name = snap.bestStreakClass
                if (name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(colorOf(snap.bestStreakColor))
                        Spacer(GlanceModifier.width(5.dp))
                        Text(name, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                    }
                } else {
                    Text(
                        "Finish assignments to start a streak",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 2,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "${snap.activeStreaks} active · ${snap.atRiskStreaks} at risk",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }

    companion object {
        val SIZE = DpSize(110.dp, 110.dp)
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
