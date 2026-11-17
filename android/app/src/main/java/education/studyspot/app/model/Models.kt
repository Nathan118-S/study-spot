package education.studyspot.app.model

import education.studyspot.app.core.FlexBoolSerializer
import education.studyspot.app.core.FlexDoubleOrNullSerializer
import education.studyspot.app.core.FlexDoubleSerializer
import education.studyspot.app.core.FlexInstantSerializer
import education.studyspot.app.core.FlexIntSerializer
import education.studyspot.app.core.FlexStringSerializer
import education.studyspot.app.core.FlexTextSerializer
import education.studyspot.app.core.LenientEnumSerializer
import education.studyspot.app.core.LenientListSerializer
import education.studyspot.app.core.LenientObjectSerializer
import education.studyspot.app.core.arrayValue
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

// Server JSON models (snake_case on the wire). Mirrors ios/StudySpot/Models/Models.swift
// and server/schema.sql. Entity rows also carry created_date / updated_date.

// region Enums

@Serializable(with = Priority.Serializer::class)
enum class Priority(val wire: String, val label: String, val rank: Int) {
    Low("low", "Low", 1), Medium("medium", "Medium", 2), High("high", "High", 3);

    object Serializer : LenientEnumSerializer<Priority>("Priority", entries.toTypedArray(), Medium, { it.wire })
    companion object { fun from(raw: String?) = entries.firstOrNull { it.wire == raw } ?: Medium }
}

@Serializable(with = AssignmentType.Serializer::class)
enum class AssignmentType(val wire: String, val label: String) {
    Homework("homework", "Homework"), Project("project", "Project"), Quiz("quiz", "Quiz"),
    Test("test", "Test"), Reading("reading", "Reading"), Other("other", "Other");

    object Serializer : LenientEnumSerializer<AssignmentType>("AssignmentType", entries.toTypedArray(), Homework, { it.wire })
    companion object { fun from(raw: String?) = entries.firstOrNull { it.wire == raw } ?: Homework }
}

@Serializable(with = Source.Serializer::class)
enum class Source(val wire: String, val label: String) {
    Manual("manual", "Manual"),
    GoogleCalendar("google_calendar", "Calendar"),
    GoogleClassroom("google_classroom", "Classroom"),
    Blackboard("blackboard", "Blackboard");

    object Serializer : LenientEnumSerializer<Source>("Source", entries.toTypedArray(), Manual, { it.wire })
}

enum class TwoFactorMethod(val wire: String, val label: String, val detail: String) {
    Totp("totp", "Authenticator app", "Google Authenticator, Authy, 2FAS"),
    Passkey("passkey", "Passkey", "Fingerprint, face or screen lock; also signs you in without a password");

    companion object { fun from(raw: String) = entries.firstOrNull { it.wire == raw } }
}

// endregion

// region User

@Serializable
data class GradeEntry(
    @Serializable(with = FlexTextSerializer::class) val letter: String = "",
    @Serializable(with = FlexDoubleSerializer::class) val min: Double = 0.0,
)

/** GET/PATCH /api/auth/me. Free-form settings live in [data]. */
@Serializable
data class User(
    val id: String,
    /** Sign-in name (accounts have usernames, not emails). */
    @Serializable(with = FlexTextSerializer::class) val username: String = "",
    @Serializable(with = FlexStringSerializer::class) val name: String? = null,
    @Serializable(with = FlexStringSerializer::class) @SerialName("full_name") val fullName: String? = null,
    @Serializable(with = FlexTextSerializer::class) val role: String = "user",
    @Serializable(with = FlexStringSerializer::class) @SerialName("disabled_reason") val disabledReason: String? = null,
    @Serializable(with = FlexBoolSerializer::class) @SerialName("is_verified") val isVerified: Boolean = false,
    @Serializable(with = FlexBoolSerializer::class) @SerialName("twofa_enabled") val twofaEnabled: Boolean = false,
    @Serializable(with = FlexStringSerializer::class) @SerialName("twofa_method") val twofaMethod: String? = null,
    @Serializable(with = StringList::class) @SerialName("twofa_methods") val twofaMethods: List<String> = emptyList(),
    /** True while this session still needs its second factor verified. */
    @Serializable(with = FlexBoolSerializer::class) @SerialName("mfa_pending") val mfaPending: Boolean = false,
    @Serializable(with = FlexBoolSerializer::class) @SerialName("has_passkey") val hasPasskey: Boolean = false,
    @Serializable(with = LenientObjectSerializer::class) val data: JsonObject = JsonObject(emptyMap()),
    @Serializable(with = FlexInstantSerializer::class) @SerialName("created_date") val createdDate: Instant? = null,
) {
    val isAdmin get() = role == "admin"
    val isDemo get() = role == "demo"
    val isDisabled get() = role == "disabled"

    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() }
            ?: username.substringBefore("@").ifEmpty { "User" }

    val onboardingCompleted get() = data["onboarding_completed"].boolValue == true
    val leaderboardEnabled get() = data["leaderboard_enabled"].boolValue == true
    val welcomeStyle get() = data["welcome_style"].stringValue ?: "large"
    val syncCompletionToClassroom get() = data["sync_completion_to_classroom"].boolValue != false
    val lastSync: Instant? get() = education.studyspot.app.core.Dates.parse(data["last_sync"].stringValue)
    val blackboardInstanceUrl get() = data["blackboard_instance_url"].stringValue
    val timezone get() = data["timezone"].stringValue

    val gradingScale: List<GradeEntry>
        get() {
            val parsed = data["grading_scale"].arrayValue.orEmpty().mapNotNull { v ->
                val letter = v["letter"].stringValue ?: return@mapNotNull null
                val min = v["min"].doubleValue ?: return@mapNotNull null
                GradeEntry(letter, min)
            }
            return parsed.ifEmpty { Grading.defaultScale }
        }

    /** Enabled 2FA methods, accounting for the legacy single-method field. */
    val enabledTwoFactorMethods: List<TwoFactorMethod>
        get() {
            var raw = twofaMethods
            if (raw.isEmpty()) raw = data["twofa_methods"].arrayValue.orEmpty().mapNotNull { it.stringValue }
            if (raw.isEmpty() && twofaMethod != null) raw = listOf(twofaMethod)
            if (raw.isEmpty() && twofaEnabled) raw = listOf("totp")
            return raw.mapNotNull(TwoFactorMethod::from)
        }
}

object StringList : LenientListSerializer<String>(String.serializer())

// endregion

// region Entities

@Serializable
data class Subtask(
    @Serializable(with = FlexTextSerializer::class) val text: String = "",
    @Serializable(with = FlexBoolSerializer::class) val done: Boolean = false,
)

object SubtaskList : LenientListSerializer<Subtask>(Subtask.serializer())

/** /api/entities/Assignment */
@Serializable
data class Assignment(
    val id: String,
    @Serializable(with = FlexTextSerializer::class) val title: String = "Untitled",
    @Serializable(with = FlexStringSerializer::class) @SerialName("class_id") val classId: String? = null,
    @Serializable(with = FlexStringSerializer::class) @SerialName("class_name") val className: String? = null,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("due_date") val dueDate: Instant? = null,
    val priority: Priority = Priority.Medium,
    val type: AssignmentType = AssignmentType.Homework,
    @Serializable(with = FlexTextSerializer::class) val notes: String = "",
    @Serializable(with = FlexTextSerializer::class) val description: String = "",
    val source: Source = Source.Manual,
    @Serializable(with = FlexStringSerializer::class) @SerialName("external_id") val externalId: String? = null,
    @Serializable(with = FlexBoolSerializer::class) val completed: Boolean = false,
    @Serializable(with = FlexDoubleSerializer::class) val progress: Double = 0.0,
    @Serializable(with = SubtaskList::class) val subtasks: List<Subtask> = emptyList(),
    @Serializable(with = FlexDoubleSerializer::class) val points: Double = 0.0,
    @Serializable(with = FlexDoubleOrNullSerializer::class) val score: Double? = null,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("created_date") val createdDate: Instant? = null,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("updated_date") val updatedDate: Instant? = null,
) {
    /** Empty class ids are treated as "no class". */
    val classIdOrNull: String? get() = classId?.takeIf { it.isNotEmpty() }
    val displayTitle: String get() = title.ifBlank { "Untitled" }

    fun isOverdue(now: Instant = Instant.now()): Boolean = dueDate?.let { it < now && !completed } == true
    val isOverdue: Boolean get() = isOverdue()
    val isGraded: Boolean get() = score != null && points > 0

    fun isDueToday(zone: ZoneId = ZoneId.systemDefault()): Boolean =
        dueDate?.atZone(zone)?.toLocalDate() == LocalDate.now(zone)
}

/**
 * One weekly class meeting: weekday 0 = Sunday … 6 = Saturday, local times "HH:mm".
 * Stored in Class.schedule.
 */
@Serializable
data class ClassMeeting(
    @Serializable(with = FlexIntSerializer::class) val day: Int = 0,
    @Serializable(with = FlexTextSerializer::class) val start: String = "",
    @Serializable(with = FlexTextSerializer::class) val end: String = "",
) {
    val startTime: LocalTime? get() = parse(start)
    val endTime: LocalTime? get() = parse(end)

    /** The next time this meeting starts at or after [from]. */
    fun nextStart(from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val t = startTime ?: return null
        val dow = java.time.DayOfWeek.of(if (day == 0) 7 else day) // java: 1 = Monday … 7 = Sunday
        var candidate = from.with(TemporalAdjusters.nextOrSame(dow)).with(t).withSecond(0).withNano(0)
        if (candidate < from.withNano(0)) candidate = candidate.plusWeeks(1)
        return candidate
    }

    companion object {
        val weekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val weekdayShort = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        fun parse(s: String?): LocalTime? {
            val parts = s?.split(":")?.mapNotNull { it.toIntOrNull() } ?: return null
            if (parts.size < 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return null
            return LocalTime.of(parts[0], parts[1])
        }
    }
}

object MeetingList : LenientListSerializer<ClassMeeting>(ClassMeeting.serializer())

/** /api/entities/Class */
@Serializable
data class SchoolClass(
    val id: String,
    @Serializable(with = FlexTextSerializer::class) val name: String = "Unnamed",
    /** "#rrggbb"; parse with ui.components.parseHexColor. */
    @Serializable(with = FlexTextSerializer::class) val color: String = DEFAULT_COLOR,
    @Serializable(with = FlexStringSerializer::class) @SerialName("teacher_name") val teacherName: String? = null,
    @Serializable(with = FlexStringSerializer::class) val subject: String? = null,
    @Serializable(with = FlexStringSerializer::class) val room: String? = null,
    @Serializable(with = MeetingList::class) val schedule: List<ClassMeeting> = emptyList(),
    val source: Source = Source.Manual,
    @Serializable(with = FlexStringSerializer::class) @SerialName("external_id") val externalId: String? = null,
    @Serializable(with = FlexIntSerializer::class) @SerialName("best_streak") val bestStreak: Int = 0,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("created_date") val createdDate: Instant? = null,
) {
    val displayName: String get() = name.ifBlank { "Unnamed" }

    companion object {
        const val DEFAULT_COLOR = "#6366f1"
        /** Used for assignments without a class. */
        const val UNCATEGORIZED_COLOR = "#94a3b8"
    }
}

/** /api/entities/AssignmentTemplate */
@Serializable
data class AssignmentTemplate(
    val id: String,
    @Serializable(with = FlexTextSerializer::class) val name: String = "",
    @Serializable(with = FlexTextSerializer::class) val title: String = "",
    val type: AssignmentType = AssignmentType.Homework,
    val priority: Priority = Priority.Medium,
    @Serializable(with = FlexDoubleSerializer::class) val points: Double = 0.0,
    @Serializable(with = FlexTextSerializer::class) val notes: String = "",
    @Serializable(with = FlexInstantSerializer::class) @SerialName("updated_date") val updatedDate: Instant? = null,
)

/** /api/entities/StudySession (study timer history). */
@Serializable
data class StudySession(
    val id: String,
    @Serializable(with = FlexStringSerializer::class) @SerialName("assignment_id") val assignmentId: String? = null,
    @Serializable(with = FlexStringSerializer::class) @SerialName("assignment_title") val assignmentTitle: String? = null,
    @Serializable(with = FlexStringSerializer::class) @SerialName("class_id") val classId: String? = null,
    @Serializable(with = FlexStringSerializer::class) @SerialName("class_name") val className: String? = null,
    @Serializable(with = FlexDoubleSerializer::class) @SerialName("duration_minutes") val durationMinutes: Double = 0.0,
    /** "focus" or "break". */
    @Serializable(with = FlexTextSerializer::class) @SerialName("session_type") val sessionType: String = "focus",
    @Serializable(with = FlexInstantSerializer::class) @SerialName("started_at") val startedAt: Instant? = null,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("completed_at") val completedAt: Instant? = null,
    @Serializable(with = FlexStringSerializer::class) val note: String? = null,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("created_date") val createdDate: Instant? = null,
)

/** /api/entities/Notification (in-app notifications; the bell). */
@Serializable
data class AppNotification(
    val id: String,
    @Serializable(with = FlexTextSerializer::class) val title: String = "",
    @Serializable(with = FlexTextSerializer::class) val content: String = "",
    /** "streak", "test", "reminder" or "info". */
    @Serializable(with = FlexTextSerializer::class) val type: String = "info",
    @Serializable(with = FlexBoolSerializer::class) val read: Boolean = false,
    @Serializable(with = FlexStringSerializer::class) @SerialName("action_label") val actionLabel: String? = null,
    /** A website path such as "/classes"; see navigation.Router.openWebPath. */
    @Serializable(with = FlexStringSerializer::class) @SerialName("action_url") val actionUrl: String? = null,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("created_date") val createdDate: Instant? = null,
)

// endregion

// region Function results

/** functions/checkGoogleConnections */
@Serializable
data class Connections(
    @Serializable(with = FlexBoolSerializer::class) val calendar: Boolean = false,
    @Serializable(with = FlexBoolSerializer::class) val classroom: Boolean = false,
    @Serializable(with = FlexBoolSerializer::class) val blackboard: Boolean = false,
) {
    val any: Boolean get() = calendar || classroom || blackboard
}

@Serializable
data class LeaderboardEntry(
    val userId: String,
    @Serializable(with = FlexTextSerializer::class) val name: String = "",
    @Serializable(with = FlexIntSerializer::class) val bestStreak: Int = 0,
)

/** functions/getLeaderboard */
@Serializable
data class Leaderboard(
    val entries: List<LeaderboardEntry> = emptyList(),
    val currentUserId: String? = null,
)

@Serializable
data class ClassroomAttachment(
    @Serializable(with = FlexTextSerializer::class) val title: String = "",
    @Serializable(with = FlexTextSerializer::class) val url: String = "",
    @Serializable(with = FlexTextSerializer::class) val kind: String = "",
)

/** functions/getClassroomAttachments */
@Serializable
data class ClassroomAttachments(val attachments: List<ClassroomAttachment> = emptyList())

/** functions/adminListUsers rows. */
@Serializable
data class AdminUser(
    val id: String,
    @Serializable(with = FlexTextSerializer::class) val username: String = "",
    @Serializable(with = FlexStringSerializer::class) @SerialName("full_name") val fullName: String? = null,
    @Serializable(with = FlexStringSerializer::class) val name: String? = null,
    @Serializable(with = FlexTextSerializer::class) val role: String = "user",
    @Serializable(with = FlexStringSerializer::class) @SerialName("disabled_reason") val disabledReason: String? = null,
    @Serializable(with = FlexBoolSerializer::class) @SerialName("twofa_enabled") val twofaEnabled: Boolean = false,
    @Serializable(with = FlexStringSerializer::class) @SerialName("twofa_method") val twofaMethod: String? = null,
    @Serializable(with = FlexBoolSerializer::class) @SerialName("is_verified") val isVerified: Boolean = false,
    @Serializable(with = FlexBoolSerializer::class) @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
    @Serializable(with = FlexInstantSerializer::class) @SerialName("created_date") val createdDate: Instant? = null,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: fullName?.takeIf { it.isNotBlank() } ?: username
}

/** functions/adminListSecrets rows (status only; values are never returned). */
@Serializable
data class SecretStatus(
    val key: String,
    @Serializable(with = FlexTextSerializer::class) val group: String = "Other",
    @Serializable(with = FlexStringSerializer::class) val label: String? = null,
    @Serializable(with = FlexBoolSerializer::class) val isSet: Boolean = false,
    @Serializable(with = FlexInstantSerializer::class) val setAt: Instant? = null,
)

/** functions/adminDeployStatus */
@Serializable
data class DeployStatus(
    @Serializable(with = FlexTextSerializer::class) val status: String = "idle",
    @Serializable(with = FlexInstantSerializer::class) val startedAt: Instant? = null,
    @Serializable(with = FlexInstantSerializer::class) val finishedAt: Instant? = null,
    @Serializable(with = FlexTextSerializer::class) val log: String = "",
    @Serializable(with = FlexStringSerializer::class) val error: String? = null,
)

// endregion

/** Letter grades (mirrors ios/StudySpot/Logic/Grading.swift). */
object Grading {
    val defaultScale = listOf(
        GradeEntry("A", 90.0), GradeEntry("B", 80.0), GradeEntry("C", 70.0), GradeEntry("D", 60.0), GradeEntry("F", 0.0),
    )

    fun normalize(scale: List<GradeEntry>) = scale.ifEmpty { defaultScale }.sortedByDescending { it.min }

    fun letter(score: Double?, points: Double, scale: List<GradeEntry>): String? {
        if (score == null || points <= 0) return null
        val pct = score / points * 100
        val sorted = normalize(scale)
        return (sorted.firstOrNull { pct >= it.min } ?: sorted.lastOrNull())?.letter
    }

    fun percent(a: Assignment): Int? {
        val s = a.score ?: return null
        if (a.points <= 0) return null
        return Math.round(s / a.points * 100).toInt()
    }
}
