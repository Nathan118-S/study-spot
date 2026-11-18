package education.studyspot.app.liveupdates

import kotlinx.serialization.Serializable

/** One assignment in a class countdown's "due today" list. */
@Serializable
data class DueItem(val title: String = "", val className: String = "")

/**
 * "Class starts soon" (ios/Shared/ClassActivity.swift ClassStartAttributes +
 * ContentState). Built from an FCM `type=class_start` message
 * (server/src/jobs/classActivities.js androidClassData) or locally by
 * [ClassAlarmScheduler].
 */
data class ClassCountdown(
    val classId: String,
    val className: String,
    val subject: String = "",
    val room: String = "",
    val colorHex: String = "#f19d37",
    /** Unix seconds. */
    val startsAt: Long,
    val endsAfterMinutes: Int = DEFAULT_END_AFTER_MINUTES,
    val dueCount: Int = 0,
    /** Up to 3. */
    val dueToday: List<DueItem> = emptyList(),
    val simulated: Boolean = false,
) {
    val notificationId: Int get() = education.studyspot.app.push.NotificationIds.classMeeting(classId, startsAt)
    val startsAtMillis: Long get() = startsAt * 1000
    /** When the notification goes away on its own. */
    val endsAtMillis: Long get() = startsAtMillis + endsAfterMinutes * 60_000L

    /** "{class} starts in N min" / "{class} is starting" / "{class} started". */
    fun title(nowMillis: Long): String {
        val mins = Math.ceil((startsAtMillis - nowMillis) / 60_000.0).toLong()
        return when {
            mins >= 1 -> "$className starts in $mins min"
            mins > -1 -> "$className is starting"
            else -> "$className started"
        }
    }

    /** "Subject · Room 101" (empty when neither is set). */
    val detailLine: String
        get() = listOfNotNull(subject.takeIf { it.isNotBlank() }, room.takeIf { it.isNotBlank() }?.let { "Room $it" })
            .joinToString(" · ")

    /** "3 due today: Essay, Lab report, Quiz" (empty when nothing is due). */
    val dueLine: String
        get() {
            if (dueCount <= 0) return ""
            val titles = dueToday.take(3).map { it.title }.filter { it.isNotBlank() }
            val more = dueCount - titles.size
            val list = titles.joinToString(", ") + if (more > 0 && titles.isNotEmpty()) " +$more more" else ""
            return if (titles.isEmpty()) "$dueCount due today" else "$dueCount due today: $list"
        }

    companion object {
        const val LEAD_MINUTES = 10
        const val DEFAULT_END_AFTER_MINUTES = 10
    }
}

/**
 * A study timer shown as a live notification, either the app's own timer
 * or one pushed by the server (FCM `type=study_timer`, display only).
 */
data class StudyTimerLive(
    val phase: String,
    val phaseLabel: String,
    /** Wall clock (ms) the phase ends; null while paused. */
    val endsAtMillis: Long?,
    /** Phase length in seconds. */
    val totalSeconds: Long,
    /** Seconds left (used while paused). */
    val remainingSeconds: Long,
    val assignmentTitle: String = "",
    val className: String = "",
    val simulated: Boolean = false,
) {
    val running: Boolean get() = endsAtMillis != null

    fun progress(nowMillis: Long): Int {
        if (totalSeconds <= 0) return 0
        val left = endsAtMillis?.let { ((it - nowMillis) / 1000).coerceAtLeast(0) } ?: remainingSeconds
        return (((totalSeconds - left).toDouble() / totalSeconds) * 100).toInt().coerceIn(0, 100)
    }
}
