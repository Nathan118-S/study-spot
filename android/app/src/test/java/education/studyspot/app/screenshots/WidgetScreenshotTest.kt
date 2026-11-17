package education.studyspot.app.screenshots

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import education.studyspot.app.widget.StudySpotWidget
import education.studyspot.app.widget.WidgetSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * The Up Next widget at its three sizes, rendered through Glance's
 * RemoteViews translation and applied like a launcher would, on a
 * wallpaper-like backdrop.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PIXEL_8, application = ScreenshotApp::class)
class WidgetScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}-dark={0}")
        fun modes() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        if (dark) RuntimeEnvironment.setQualifiers("+night")
    }

    @Test
    fun upNext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        val now = System.currentTimeMillis()
        val snapshot = WidgetSnapshot.preview(now)
        val widget = StudySpotWidget()
        val sizes = listOf(StudySpotWidget.SMALL, StudySpotWidget.MEDIUM, StudySpotWidget.LARGE)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (dark) Color.rgb(28, 32, 44) else Color.rgb(196, 214, 236))
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }
        sizes.forEach { size: DpSize ->
            val rv = runBlocking {
                GlanceRemoteViews().compose(context, size) { widget.Content(context, snapshot, now, "Tuesday, Nov 17") }.remoteViews
            }
            val host = FrameLayout(context)
            host.addView(rv.apply(context, host))
            column.addView(
                host,
                LinearLayout.LayoutParams((size.width.value * density).toInt(), (size.height.value * density).toInt()).apply {
                    bottomMargin = (16 * density).toInt()
                },
            )
        }
        val w = View.MeasureSpec.makeMeasureSpec((300 * density).toInt(), View.MeasureSpec.EXACTLY)
        val h = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        column.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        column.measure(w, h)
        column.layout(0, 0, column.measuredWidth, column.measuredHeight)
        val bitmap = android.graphics.Bitmap.createBitmap(column.measuredWidth, column.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        column.draw(android.graphics.Canvas(bitmap))
        bitmap.captureRoboImage("build/outputs/roborazzi/18_widget_up_next_${if (dark) "dark" else "light"}.png")
    }
}
