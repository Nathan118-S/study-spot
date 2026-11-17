package education.studyspot.app.screenshots

import education.studyspot.app.AppContainer
import education.studyspot.app.StudySpotApplication

/**
 * The app as the screenshot tests run it: the real [AppContainer], but its
 * HTTP client answers from [FakeBackend] instead of the network.
 */
class ScreenshotApp : StudySpotApplication() {
    override fun createContainer(): AppContainer = AppContainer(this, FakeBackend.client)
}
