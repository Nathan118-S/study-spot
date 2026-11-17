package education.studyspot.app.screenshots

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import education.studyspot.app.AppContainer
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.StudySpotApplication
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.navigation.LocalAppNavigator
import education.studyspot.app.ui.theme.StudySpotTheme
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Shadows
import android.content.ComponentName
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import java.util.TimeZone

/**
 * Pixel 8: 1080 × 2400 px at 420 dpi = 411 × 914 dp. Status bar 52 dp
 * (camera cutout), gesture navigation 24 dp.
 */
const val PIXEL_8 = "w411dp-h914dp-normal-long-notround-any-420dpi-keyshidden-nonav"
private const val STATUS_BAR_PX = 137
private const val NAV_BAR_PX = 63

/**
 * Base for the screenshot tests: a fresh [ScreenshotApp] per test, light or
 * dark, edge-to-edge with fake system-bar insets (drawn on top, like the real
 * status bar and gesture handle), and a NavHost so screens get a real
 * [AppNavigator]. PNGs go to build/outputs/roborazzi/<name>_<light|dark>.png.
 */
abstract class ScreenshotHarness(private val dark: Boolean) {
    // ui-test-manifest isn't merged into a unit-test manifest, so register the
    // host activity with Robolectric before the compose rule launches it.
    protected val compose = @Suppress("DEPRECATION") createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(TestWatcherRule {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        })
        .around(compose)

    protected val container: AppContainer
        get() = ApplicationProvider.getApplicationContext<Application>().let { (it as StudySpotApplication).container }

    @Before
    fun baseSetUp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        if (dark) RuntimeEnvironment.setQualifiers("+night")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    protected fun snap(name: String, interact: () -> Unit = {}, content: @Composable (AppNavigator) -> Unit) {
        val activity = compose.activity
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(activity.window, false) }
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                StudySpotTheme(darkTheme = dark) {
                    val nav = rememberNavController()
                    val navigator = remember(nav) { AppNavigator(nav, container.router) }
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalAppNavigator provides navigator) {
                            NavHost(nav, startDestination = "screen") {
                                composable("screen") {
                                    Surface(color = MaterialTheme.colorScheme.background) { content(navigator) }
                                }
                            }
                        }
                        SystemBars(dark)
                    }
                }
            }
        }
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(0, STATUS_BAR_PX, 0, NAV_BAR_PX))
                .build()
            ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        interact()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/${name}_${if (dark) "dark" else "light"}.png")
    }
}

/** Draws a status bar (clock, icons) and the gesture handle over the content, transparent like edge-to-edge. */
@Composable
private fun SystemBars(dark: Boolean) {
    val ink = if (dark) Color.White else Color(0xFF1C1B1F)
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("9:41", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 14.dp, height = 10.dp).background(ink.copy(alpha = 0.9f), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(width = 20.dp, height = 10.dp).background(ink.copy(alpha = 0.9f), RoundedCornerShape(2.dp)))
        }
        // Camera punch-hole.
        Box(Modifier.align(Alignment.TopCenter).windowInsetsTopHeight(WindowInsets.statusBars), contentAlignment = Alignment.Center) {
            Box(Modifier.size(14.dp).background(Color.Black, RoundedCornerShape(7.dp)))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 108.dp, height = 4.dp).background(ink.copy(alpha = 0.85f), RoundedCornerShape(2.dp)))
        }
    }
}

private class TestWatcherRule(private val before: () -> Unit) : ExternalResource() {
    override fun before() = before.invoke()
}
