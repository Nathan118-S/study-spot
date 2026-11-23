package education.studyspot.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.navigation.AppRoot
import education.studyspot.app.core.stringValue
import education.studyspot.app.ui.theme.ColorTheme
import education.studyspot.app.ui.theme.StudySpotTheme
import education.studyspot.app.ui.theme.isDark
import kotlinx.coroutines.launch

/** The single activity. Receives studyspot:// links (sign-in callback, widgets, notifications). */
class MainActivity : ComponentActivity() {
    private val container get() = appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by container.theme.settings.collectAsStateWithLifecycle()
            val dark = settings.isDark()
            // The colour theme follows user.data.theme (set on any device), cached on-device.
            val user by container.repository.user.collectAsStateWithLifecycle()
            val savedTheme = user?.data?.get("theme").stringValue
            LaunchedEffect(savedTheme) {
                if (savedTheme != null && ColorTheme.entries.any { it.id == savedTheme }) container.theme.setColorTheme(savedTheme)
            }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                StudySpotTheme(darkTheme = dark, animations = settings.animations, colorTheme = ColorTheme.fromId(settings.colorTheme)) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        // Don't re-handle (e.g. re-use a sign-in token) if the activity is recreated.
        intent.data = null
        container.scope.launch { container.deepLinks.handle(uri) }
    }
}
