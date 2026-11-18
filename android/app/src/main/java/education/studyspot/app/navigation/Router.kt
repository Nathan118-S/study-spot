package education.studyspot.app.navigation

import education.studyspot.app.model.IcalKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped navigation requests that can come from outside Compose (deep
 * links, notifications, widgets). AppNavHost observes [pendingRoute] and
 * navigates once the signed-in UI is showing.
 */
class Router {
    private val _selectedTab = MutableStateFlow(AppTab.Dashboard)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    private val _popToMain = MutableStateFlow(0)
    /** Incremented to ask AppNavHost to return to the tabbed shell. */
    val popToMain: StateFlow<Int> = _popToMain.asStateFlow()

    private val _calendarLinkGuide = MutableStateFlow<IcalKind?>(null)
    /** A calendar-link guide to show over the app (AppRoot), e.g. from a Blackboard error message. */
    val calendarLinkGuide: StateFlow<IcalKind?> = _calendarLinkGuide.asStateFlow()

    fun openCalendarLinkGuide(kind: IcalKind) {
        _calendarLinkGuide.value = kind
    }

    fun closeCalendarLinkGuide() {
        _calendarLinkGuide.value = null
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    /** Switch tab and close any pushed screens. */
    fun showTab(tab: AppTab) {
        _selectedTab.value = tab
        _popToMain.value++
    }

    /** Queue a route (see [Routes]) to open. */
    fun navigate(route: String) {
        _pendingRoute.value = route
    }

    fun consumePendingRoute(): String? = _pendingRoute.value.also { _pendingRoute.value = null }

    /** Maps a website path (e.g. a notification's action_url) onto native navigation. */
    fun openWebPath(path: String) {
        when {
            path == "/classes" -> showTab(AppTab.Classes)
            path == "/calendar" -> showTab(AppTab.Calendar)
            path == "/analytics" || path == "/streaks" -> showTab(AppTab.Streaks)
            path == "/insights" -> showTab(AppTab.Insights)
            path == "/settings" -> navigate(Routes.SETTINGS)
            path.startsWith("/admin") -> navigate(Routes.ADMIN)
            else -> showTab(AppTab.Dashboard)
        }
    }

    fun reset() {
        _selectedTab.value = AppTab.Dashboard
        _pendingRoute.value = null
        _calendarLinkGuide.value = null
    }
}
