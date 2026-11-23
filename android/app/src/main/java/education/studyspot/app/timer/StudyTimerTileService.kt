package education.studyspot.app.timer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import education.studyspot.app.MainActivity
import education.studyspot.app.R
import education.studyspot.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * "Study timer" Quick Settings tile: tap to start focusing (or resume), tap
 * again to pause. Shows whether the timer is running and the time left.
 * Drives the same [TimerController] as the timer screen, so the foreground
 * service and its notification follow. Signed out, a tap opens the app.
 */
class StudyTimerTileService : TileService() {
    private val controller by lazy { TimerController.get(this) }
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch {
            controller.state.collectLatest { st ->
                // While running, tick the subtitle once a second while the panel is open.
                while (true) {
                    val now = System.currentTimeMillis()
                    if (st.isDue(now)) {
                        controller.checkDue()
                        return@collectLatest
                    }
                    render(st, now)
                    if (!st.running) return@collectLatest
                    delay((st.remaining(now) % 1000).coerceAtLeast(200L))
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (appContainer.api.token == null) {
            if (isLocked) unlockAndRun { openApp() } else openApp()
            return
        }
        controller.quickToggle()
        render(controller.state.value, System.currentTimeMillis())
    }

    private fun render(s: TimerState, now: Long) {
        val tile = qsTile ?: return
        tile.state = if (s.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_timer_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_timer)
        val detail = when {
            s.running -> "${s.phase.label} · ${s.display(now)}"
            s.paused -> "Paused · ${s.display(now)}"
            else -> "Start ${s.focusMinutes} min focus"
        }
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = detail
        tile.contentDescription = "${getString(R.string.tile_timer_label)}, $detail"
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(Intent.ACTION_VIEW, "studyspot://timer".toUri(), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /**
         * Asks the system to add the tile (Android 13+). [onResult] gets true
         * when it's added or was already there.
         */
        fun requestAdd(context: Context, onResult: (Boolean) -> Unit) {
            if (Build.VERSION.SDK_INT < 33) return onResult(false)
            val sbm = context.getSystemService(StatusBarManager::class.java) ?: return onResult(false)
            sbm.requestAddTileService(
                ComponentName(context, StudyTimerTileService::class.java),
                context.getString(R.string.tile_timer_label),
                Icon.createWithResource(context, R.drawable.ic_tile_timer),
                ContextCompat.getMainExecutor(context),
            ) { result ->
                onResult(
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                )
            }
        }
    }
}
