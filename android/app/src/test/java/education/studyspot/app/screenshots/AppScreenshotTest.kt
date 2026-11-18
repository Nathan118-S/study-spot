package education.studyspot.app.screenshots

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import education.studyspot.app.data.DemoData
import education.studyspot.app.navigation.AppTab
import education.studyspot.app.navigation.MainScaffold
import education.studyspot.app.ui.admin.AdminScreen
import education.studyspot.app.ui.auth.WelcomeScreen
import education.studyspot.app.ui.classes.ClassFormSheet
import education.studyspot.app.ui.dashboard.AssignmentDetailScreen
import education.studyspot.app.ui.dashboard.AssignmentFormScreen
import education.studyspot.app.ui.notifications.NotificationsScreen
import education.studyspot.app.ui.onboarding.OnboardingScreen
import education.studyspot.app.ui.settings.IcalGuide
import education.studyspot.app.ui.settings.SettingsPage
import education.studyspot.app.ui.settings.SettingsPageScreen
import education.studyspot.app.ui.settings.SettingsScreen
import education.studyspot.app.ui.timer.StudyTimerScreen
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with demo data, light and dark. Images are only
 * written by `./gradlew :app:recordRoborazziDebug` (see android/README.md);
 * a plain `testDebugUnitTest` just checks that every screen composes.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PIXEL_8, application = ScreenshotApp::class)
class AppScreenshotTest(dark: Boolean) : ScreenshotHarness(dark) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}-dark={0}")
        fun modes() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    private fun signedIn() = ScreenshotFixtures.signIn(container)

    private fun tab(tab: AppTab, name: String) {
        signedIn()
        container.router.selectTab(tab)
        snap(name) { MainScaffold(it) }
    }

    @Test fun welcome() = snap("01_welcome") { WelcomeScreen() }

    @Test fun dashboard() = tab(AppTab.Dashboard, "02_dashboard")

    @Test fun classes() = tab(AppTab.Classes, "03_classes")

    @Test fun calendar() = tab(AppTab.Calendar, "04_calendar")

    @Test fun streaks() = tab(AppTab.Streaks, "05_streaks")

    @Test fun insights() = tab(AppTab.Insights, "06_insights")

    @Test fun assignmentDetail() {
        signedIn()
        snap("07_assignment_detail") { AssignmentDetailScreen("demo-a2", it) }
    }

    @Test fun assignmentForm() {
        signedIn()
        snap("08_assignment_form") { AssignmentFormScreen("demo-a3", it) }
    }

    @Test fun classFormSheet() {
        signedIn()
        container.router.selectTab(AppTab.Classes)
        snap("09_class_form_sheet") {
            MainScaffold(it)
            ClassFormSheet(DemoData.classes.first(), onDismiss = {}, onSave = {})
        }
    }

    @Test fun notifications() {
        signedIn()
        snap("10_notifications") { NotificationsScreen(it) }
    }

    @Test fun settings() {
        signedIn()
        snap("11_settings") { SettingsScreen(it) }
    }

    @Test fun settingsReminders() {
        signedIn()
        snap("12_settings_reminders") { SettingsPageScreen(SettingsPage.Reminders, it) }
    }

    @Test fun settingsSecurity() {
        signedIn()
        snap("13_settings_security") { SettingsPageScreen(SettingsPage.Security, it) }
    }

    @Test fun settingsGoogle() {
        signedIn()
        snap("14_settings_google") { SettingsPageScreen(SettingsPage.Google, it) }
    }

    @Test fun icalGuidePublic() {
        signedIn()
        snap("14b_ical_guide_step3") { IcalGuide(onClose = {}, initialStep = 2, modifier = Modifier.safeDrawingPadding()) }
    }

    @Test fun studyTimer() {
        signedIn()
        snap("15_study_timer") { StudyTimerScreen(it) }
    }

    @Test fun onboardingServices() {
        val user = ScreenshotFixtures.user(admin = false).let { u ->
            u.copy(data = buildJsonObject { u.data.forEach { (k, v) -> put(k, v) }; put("onboarding_completed", false) })
        }
        ScreenshotFixtures.signIn(container, user)
        snap("16_onboarding_services", interact = { compose.onNodeWithText("Get started").performClick() }) { OnboardingScreen() }
    }

    @Test fun dashboardEmpty() {
        ScreenshotFixtures.signIn(container)
        container.repository.seed(assignments = emptyList(), classes = emptyList(), notifications = emptyList())
        snap("02b_dashboard_empty") { MainScaffold(it) }
    }

    @Test fun classesEmpty() {
        ScreenshotFixtures.signIn(container)
        container.repository.seed(assignments = emptyList(), classes = emptyList())
        container.router.selectTab(AppTab.Classes)
        snap("03b_classes_empty") { MainScaffold(it) }
    }

    @Test fun adminPeople() {
        signedIn()
        snap("17_admin_people", interact = { compose.onNodeWithText("People").performClick() }) { AdminScreen(it) }
    }
}
