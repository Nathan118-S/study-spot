package education.studyspot.app.push

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import education.studyspot.app.AppContainer
import education.studyspot.app.liveupdates.ClassAlarmScheduler
import education.studyspot.app.liveupdates.ClassSchedule
import education.studyspot.app.timer.TimerController
import education.studyspot.app.widget.WidgetUpdater
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps push registration, the class countdown alarms and the widgets in
 * step with the app's data (the iOS app does this from its root view):
 *
 * - push: re-configured when the user or their reminder settings change;
 * - class countdowns: re-scheduled when classes or "class_live_activities" change;
 * - widgets: the snapshot is re-written when assignments or classes change.
 *
 * Started once from StudySpotApplication.
 */
object BackgroundSync {
    @OptIn(FlowPreview::class)
    fun start(c: AppContainer) {
        val ctx = c.context
        val repo = c.repository

        // Local state first: finish/restore the timer, re-arm class alarms.
        TimerController.get(ctx).restore()
        ClassAlarmScheduler.reschedule(ctx)
        c.scope.launch { WidgetUpdater.publishPreviews(ctx) }

        // Push: only for a known user (sign-out unregisters in AuthStore).
        c.scope.launch {
            repo.user.filterNotNull()
                .map { Triple(it.id, PushManager.isEnabled(it), it.isDemo) }
                .distinctUntilChanged()
                .collect { PushManager.configure(ctx, c.api, repo.user.value) }
        }

        // Class countdown alarms, once the classes have loaded.
        c.scope.launch {
            combine(repo.user, repo.classes, repo.hasLoaded) { u, classes, loaded ->
                if (u == null || !loaded) null else Triple(u, ClassSchedule.isEnabled(u), classes)
            }
                .filterNotNull()
                .distinctUntilChanged { a, b ->
                    a.second == b.second && a.first.id == b.first.id &&
                        a.third.map { Triple(it.id, it.schedule, listOf(it.name, it.subject, it.room, it.color)) } ==
                        b.third.map { Triple(it.id, it.schedule, listOf(it.name, it.subject, it.room, it.color)) }
                }
                .debounce(500)
                .collect { (u, _, classes) -> ClassAlarmScheduler.update(ctx, u, classes) }
        }

        // Widgets: re-write the snapshot when the data changes.
        c.scope.launch {
            combine(repo.user, repo.assignments, repo.classes, repo.connections, repo.hasLoaded) { u, a, cl, co, loaded ->
                listOf(u?.id, u?.displayName, a, cl, co, loaded)
            }
                .distinctUntilChanged()
                .filter { repo.user.value == null || repo.hasLoaded.value }
                .debounce(750)
                .collect { WidgetUpdater.refresh(ctx) }
        }
    }
}

/**
 * Re-arms the class countdown alarms and the timer's phase-end alarm after a
 * reboot, an app update, a time change or exact alarms being allowed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                // Application.onCreate (BackgroundSync.start) already ran; reschedule
                // again so a time or time-zone change takes effect.
                ClassAlarmScheduler.reschedule(context)
                TimerController.get(context).checkDue()
            }
        }
    }
}
