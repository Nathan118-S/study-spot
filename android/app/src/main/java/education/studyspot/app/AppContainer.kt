package education.studyspot.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import education.studyspot.app.auth.AuthStore
import education.studyspot.app.core.ApiClient
import education.studyspot.app.core.AppConfig
import education.studyspot.app.core.TokenStore
import education.studyspot.app.data.DataRepository
import education.studyspot.app.data.MessageCenter
import education.studyspot.app.data.ThemeStore
import education.studyspot.app.navigation.DeepLinkHandler
import education.studyspot.app.navigation.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App-wide singletons (manual dependency injection). Created once in
 * [StudySpotApplication]; reach it from Compose with `LocalAppContainer.current`
 * and elsewhere with `context.appContainer`.
 */
class AppContainer(context: Context) {
    val context: Context = context.applicationContext

    /** Lives as long as the process; use for work that must outlive a screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val config = AppConfig(this.context)
    val tokens = TokenStore(this.context)
    val api = ApiClient(config, tokens)
    val messages = MessageCenter()
    val repository = DataRepository(api, messages, scope)
    val auth = AuthStore(this.context, api, repository, scope)
    val theme = ThemeStore(this.context, scope)
    val router = Router()
    val deepLinks = DeepLinkHandler(this)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer not provided (see MainActivity)")
}

val Context.appContainer: AppContainer
    get() = (applicationContext as StudySpotApplication).container
