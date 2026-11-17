package education.studyspot.app

import android.app.Application
import education.studyspot.app.push.BackgroundSync
import education.studyspot.app.push.NotificationChannels
import education.studyspot.app.push.PushManager

class StudySpotApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.create(this)
        PushManager.initialize(this) // no-op without FCM config
        // Timer restore, class countdown alarms, push registration and widget snapshots.
        BackgroundSync.start(container)
    }
}
