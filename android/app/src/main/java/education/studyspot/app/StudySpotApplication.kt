package education.studyspot.app

import android.app.Application
import education.studyspot.app.push.BackgroundSync
import education.studyspot.app.push.NotificationChannels
import education.studyspot.app.push.PushManager

open class StudySpotApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        NotificationChannels.create(this)
        PushManager.initialize(this) // no-op without FCM config
        // Timer restore, class countdown alarms, push registration and widget snapshots.
        BackgroundSync.start(container)
    }

    /** Overridden by the screenshot tests' application to run without a network. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
