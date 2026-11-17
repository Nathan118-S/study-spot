package education.studyspot.app.push

import education.studyspot.app.core.AppJson
import education.studyspot.app.liveupdates.ClassCountdown
import education.studyspot.app.liveupdates.DueItem
import education.studyspot.app.liveupdates.StudyTimerLive
import kotlinx.serialization.builtins.ListSerializer

/**
 * The server's FCM data messages (server/src/lib/notify.js,
 * jobs/classActivities.js, functions/adminSimulate.js). Every value arrives
 * as a string; JSON values are JSON strings.
 */
sealed interface PushPayload {
    /** `type=notification`: a reminder / streak / general notification the app builds itself. */
    data class Notification(
        val title: String,
        val subtitle: String?,
        val body: String,
        val channel: String,
        /** studyspot:// link to open, if any. */
        val url: String?,
        val assignmentId: String?,
        val simulated: Boolean,
    ) : PushPayload {
        /** The link a tap opens: [url], else the assignment, else null (just open the app). */
        val link: String?
            get() = url?.takeIf { it.startsWith("studyspot://") }
                ?: assignmentId?.takeIf { it.isNotBlank() }?.let { "studyspot://assignment/$it" }
    }

    data class ClassStart(val countdown: ClassCountdown) : PushPayload
    data object ClassEnd : PushPayload
    data class StudyTimer(val timer: StudyTimerLive) : PushPayload
    data class Unknown(val type: String?) : PushPayload

    companion object {
        private val dueList = ListSerializer(DueItem.serializer())

        fun parse(data: Map<String, String>, nowMillis: Long = System.currentTimeMillis()): PushPayload {
            fun str(k: String) = data[k]?.takeIf { it.isNotEmpty() }
            fun bool(k: String) = data[k]?.lowercase() == "true" || data[k] == "1"
            fun long(k: String) = data[k]?.trim()?.toDoubleOrNull()?.toLong()

            return when (val type = data["type"]) {
                "notification" -> Notification(
                    title = data["title"].orEmpty(),
                    subtitle = str("subtitle"),
                    body = data["body"].orEmpty(),
                    channel = data["channel"]?.takeIf { it in NotificationChannels.pushChannels } ?: NotificationChannels.GENERAL,
                    url = str("url"),
                    assignmentId = str("assignmentId"),
                    simulated = bool("simulated"),
                )
                "class_start" -> {
                    val classId = str("classId") ?: return Unknown(type)
                    val startsAt = long("startsAt") ?: return Unknown(type)
                    val due = str("dueToday")?.let { runCatching { AppJson.decodeFromString(dueList, it) }.getOrNull() }.orEmpty()
                    ClassStart(
                        ClassCountdown(
                            classId = classId,
                            className = str("className") ?: "Class",
                            subject = data["subject"].orEmpty(),
                            room = data["room"].orEmpty(),
                            colorHex = str("color") ?: "#f19d37",
                            startsAt = startsAt,
                            endsAfterMinutes = long("endsAfterMinutes")?.toInt()?.takeIf { it > 0 }
                                ?: ClassCountdown.DEFAULT_END_AFTER_MINUTES,
                            dueCount = long("dueCount")?.toInt() ?: due.size,
                            dueToday = due.take(3),
                            simulated = bool("simulated"),
                        ),
                    )
                }
                "class_end" -> ClassEnd
                "study_timer" -> {
                    val endsAt = long("endsAt") ?: return Unknown(type)
                    val total = long("total") ?: return Unknown(type)
                    val phase = str("phase") ?: "focus"
                    StudyTimer(
                        StudyTimerLive(
                            phase = phase,
                            phaseLabel = str("phaseLabel") ?: education.studyspot.app.timer.TimerPhase.from(phase).label,
                            endsAtMillis = endsAt * 1000,
                            totalSeconds = total,
                            remainingSeconds = ((endsAt * 1000 - nowMillis) / 1000).coerceIn(0, total),
                            assignmentTitle = data["assignmentTitle"].orEmpty(),
                            className = data["className"].orEmpty(),
                            simulated = bool("simulated"),
                        ),
                    )
                }
                else -> Unknown(type)
            }
        }
    }
}
