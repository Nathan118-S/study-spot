package education.studyspot.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Home-screen widget (the iOS app's WidgetKit widgets).
 * TODO(widget): show upcoming assignments / streaks. Data can come from the
 * app's DataRepository via a small snapshot written on change (WidgetUpdater),
 * with deep links studyspot://assignment/<id> and studyspot://tab/<name>.
 */
class StudySpotWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).padding(12.dp),
                ) {
                    Text("Study Spot", style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onBackground))
                    Text("Open the app to see your assignments.", style = TextStyle(color = GlanceTheme.colors.onBackground))
                }
            }
        }
    }
}

class StudySpotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudySpotWidget()
}

/** Call after data changes or sign-out to redraw placed widgets. */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        if (manager.getGlanceIds(StudySpotWidget::class.java).isNotEmpty()) {
            StudySpotWidget().updateAll(context)
        }
    }
}
