package education.studyspot.app.ui.admin

import education.studyspot.app.core.AppJson
import education.studyspot.app.core.Dates
import education.studyspot.app.core.arrayValue
import education.studyspot.app.core.boolValue
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.model.AdminUser
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Priority
import education.studyspot.app.model.User
import education.studyspot.app.ui.components.DateText
import education.studyspot.app.ui.components.clean
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

// Parsed admin function results (server/src/functions/adminUsers.js,
// adminSimulate.js). Mirrors the structs in ios/StudySpot/Views/Admin.

internal object AdminParse {
    fun users(res: JsonElement): List<AdminUser> =
        runCatching { AppJson.decodeFromJsonElement(ListSerializer(AdminUser.serializer()), res["users"] ?: JsonNull) }
            .getOrDefault(emptyList())

    fun user(value: JsonElement?): User? =
        value?.let { runCatching { AppJson.decodeFromJsonElement(User.serializer(), it) }.getOrNull() }

    fun shortDateTime(i: Instant?): String =
        i?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)).orEmpty()

    fun time(i: Instant?): String =
        i?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).orEmpty()
}

data class AdminGoogleConnection(val connector: String, val scope: String, val expiresAt: Instant?, val createdAt: Instant?)

data class AdminDevice(
    val id: String,
    val token: String,
    val kind: String,
    val environment: String,
    val bundleId: String,
    val updatedAt: Instant?,
) {
    val kindLabel: String
        get() = when (kind) {
            "alert" -> "iPhone notifications"
            "fcm" -> "Android phone"
            "live-activity-start" -> "iPhone class Live Activities"
            "timer-activity-start" -> "iPhone study timer Live Activities"
            else -> kind
        }

    companion object {
        fun from(d: JsonElement) = AdminDevice(
            id = d["id"].stringValue.orEmpty(),
            token = d["token"].stringValue.orEmpty(),
            kind = d["kind"].stringValue.orEmpty(),
            environment = d["environment"].stringValue.orEmpty(),
            bundleId = d["bundle_id"].stringValue.orEmpty(),
            updatedAt = Dates.parse(d["updated_at"].stringValue),
        )
    }
}

// region Records (the account's data)

enum class AdminFieldKind { Text, Multiline, Number, Bool, Date, Color, ClassRef, Subtasks, Choice }

data class AdminField(val key: String, val label: String, val kind: AdminFieldKind, val options: List<String> = emptyList())

enum class AdminRecordKind(val wire: String, val title: String) {
    Assignments("assignments", "Assignments"),
    Classes("classes", "Classes"),
    Templates("assignment_templates", "Templates"),
    StudySessions("study_sessions", "Study sessions"),
    Notifications("notifications", "Notifications");

    /** The columns the server lets an admin change (RECORDS in server/src/functions/adminUsers.js). */
    val fields: List<AdminField>
        get() {
            val types = AssignmentType.entries.map { it.wire }
            val priorities = Priority.entries.map { it.wire }
            return when (this) {
                Assignments -> listOf(
                    AdminField("title", "Title", AdminFieldKind.Text),
                    AdminField("class_id", "Class", AdminFieldKind.ClassRef),
                    AdminField("class_name", "Class name", AdminFieldKind.Text),
                    AdminField("due_date", "Due date", AdminFieldKind.Date),
                    AdminField("priority", "Priority", AdminFieldKind.Choice, priorities),
                    AdminField("type", "Type", AdminFieldKind.Choice, types),
                    AdminField("completed", "Completed", AdminFieldKind.Bool),
                    AdminField("progress", "Progress (%)", AdminFieldKind.Number),
                    AdminField("points", "Points", AdminFieldKind.Number),
                    AdminField("score", "Score", AdminFieldKind.Number),
                    AdminField("notes", "Notes", AdminFieldKind.Multiline),
                    AdminField("description", "Description", AdminFieldKind.Multiline),
                    AdminField("subtasks", "Subtasks", AdminFieldKind.Subtasks),
                )
                Classes -> listOf(
                    AdminField("name", "Name", AdminFieldKind.Text),
                    AdminField("color", "Color", AdminFieldKind.Color),
                    AdminField("teacher_name", "Teacher", AdminFieldKind.Text),
                    AdminField("best_streak", "Best streak", AdminFieldKind.Number),
                )
                Templates -> listOf(
                    AdminField("name", "Name", AdminFieldKind.Text),
                    AdminField("title", "Assignment title", AdminFieldKind.Text),
                    AdminField("type", "Type", AdminFieldKind.Choice, types),
                    AdminField("priority", "Priority", AdminFieldKind.Choice, priorities),
                    AdminField("points", "Points", AdminFieldKind.Number),
                    AdminField("notes", "Notes", AdminFieldKind.Multiline),
                )
                StudySessions -> listOf(
                    AdminField("duration_minutes", "Minutes", AdminFieldKind.Number),
                    AdminField("session_type", "Type", AdminFieldKind.Choice, listOf("focus", "break")),
                    AdminField("note", "Note", AdminFieldKind.Multiline),
                )
                Notifications -> listOf(
                    AdminField("title", "Title", AdminFieldKind.Text),
                    AdminField("content", "Message", AdminFieldKind.Multiline),
                    AdminField("read", "Read", AdminFieldKind.Bool),
                )
            }
        }

    /** The row summary from adminGetUser's stats. */
    fun summary(stat: (String) -> Int): String = when (this) {
        Assignments -> "${stat("assignments")} · ${stat("completed")} done · ${stat("overdue")} overdue"
        Classes -> "${stat("classes")}"
        Templates -> "${stat("templates")}"
        StudySessions -> "${stat("study_sessions")} · ${stat("study_minutes")} min focus"
        Notifications -> "${stat("notifications")} · ${stat("unread_notifications")} unread"
    }
}

/** A raw database row from adminListRecords. */
data class AdminRecord(val id: String, val values: Map<String, JsonElement>) {
    fun string(key: String): String? = values[key].let { v ->
        (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotEmpty() }
    }

    fun date(key: String): Instant? = Dates.parse(string(key))

    fun title(kind: AdminRecordKind): String = when (kind) {
        AdminRecordKind.Assignments, AdminRecordKind.Notifications -> string("title") ?: "Untitled"
        AdminRecordKind.Classes, AdminRecordKind.Templates -> string("name") ?: "Untitled"
        AdminRecordKind.StudySessions -> "${(values["duration_minutes"].doubleValue ?: 0.0).clean} min ${string("session_type") ?: "focus"}"
    }

    fun subtitle(kind: AdminRecordKind): String {
        val parts = mutableListOf<String>()
        when (kind) {
            AdminRecordKind.Assignments -> {
                string("class_name")?.let(parts::add)
                parts.add(DateText.short(date("due_date")))
                if (values["completed"].boolValue == true) parts.add("Done")
            }
            AdminRecordKind.Classes -> {
                string("teacher_name")?.let(parts::add)
                parts.add("Best streak ${(values["best_streak"].doubleValue ?: 0.0).toInt()}")
            }
            AdminRecordKind.Templates -> {
                string("title")?.let(parts::add)
                string("type")?.let { parts.add(it.replaceFirstChar(Char::uppercase)) }
            }
            AdminRecordKind.StudySessions -> {
                date("created_at")?.let { parts.add(AdminParse.shortDateTime(it)) }
                string("class_name")?.let(parts::add)
                string("note")?.let(parts::add)
            }
            AdminRecordKind.Notifications -> {
                string("content")?.let(parts::add)
                date("created_at")?.let { parts.add(AdminParse.shortDateTime(it)) }
            }
        }
        return parts.joinToString(" · ")
    }

    companion object {
        fun from(json: JsonElement): AdminRecord {
            val values = (json as? JsonObject).orEmpty()
            return AdminRecord(values["id"].stringValue ?: UUID.randomUUID().toString(), values)
        }

        fun list(res: JsonElement): List<AdminRecord> = res["records"].arrayValue.orEmpty().map(::from)
    }
}

/** Fields in [values] that differ from [original] (class_id also sends class_name). */
fun adminRecordChanges(kind: AdminRecordKind, original: Map<String, JsonElement>, values: Map<String, JsonElement>): JsonObject {
    val out = linkedMapOf<String, JsonElement>()
    for (field in kind.fields) {
        val keys = if (field.key == "class_id") listOf("class_id", "class_name") else listOf(field.key)
        for (key in keys) {
            val now = values[key] ?: JsonNull
            if (now != (original[key] ?: JsonNull)) out[key] = now
        }
    }
    return JsonObject(out)
}

// endregion

// region Simulate on phone

data class SimItem(val id: String, val name: String, val detail: String?)

/** adminSimulateOptions: push setup, registered devices, classes and assignments. */
data class SimOptions(
    val apns: Boolean = false,
    val fcm: Boolean = false,
    val notificationDevices: Int = 0,
    val classDevices: Int = 0,
    val timerDevices: Int = 0,
    val androidDevices: Int = 0,
    val classes: List<SimItem> = emptyList(),
    val assignments: List<SimItem> = emptyList(),
) {
    /** Some phone can receive a pushed notification. */
    val notificationPushReady get() = (apns && notificationDevices > 0) || (fcm && androidDevices > 0)
    val classReady get() = (apns && classDevices > 0) || (fcm && androidDevices > 0)
    val timerReady get() = (apns && timerDevices > 0) || (fcm && androidDevices > 0)
    val anyPush get() = apns || fcm

    companion object {
        fun from(json: JsonElement): SimOptions {
            val d = json["devices"]
            fun n(key: String) = (d[key].doubleValue ?: 0.0).toInt()
            return SimOptions(
                apns = json["apns"].boolValue == true,
                fcm = json["fcm"].boolValue == true,
                notificationDevices = n("notifications"),
                classDevices = n("class_activity"),
                timerDevices = n("timer_activity"),
                androidDevices = n("android"),
                classes = json["classes"].arrayValue.orEmpty().map { c ->
                    SimItem(c["id"].stringValue.orEmpty(), c["name"].stringValue ?: "Class", c["subject"].stringValue)
                },
                assignments = json["assignments"].arrayValue.orEmpty().map { a ->
                    SimItem(a["id"].stringValue.orEmpty(), a["title"].stringValue ?: "Assignment", a["class_name"].stringValue)
                },
            )
        }
    }
}

data class SimResult(
    val title: String,
    val summary: String,
    val ok: Boolean,
    val failures: List<String> = emptyList(),
    val date: Instant = Instant.now(),
    val id: String = UUID.randomUUID().toString(),
) {
    companion object {
        fun error(title: String, message: String) = SimResult(title, message, ok = false)

        fun from(title: String, res: JsonElement): SimResult {
            val devices = (res["devices"].doubleValue ?: 0.0).toInt()
            val delivered = (res["delivered"].doubleValue ?: 0.0).toInt()
            val results = res["results"].arrayValue.orEmpty()
            val android = results.count { it["platform"].stringValue == "android" }
            val ios = results.size - android
            val parts = mutableListOf<String>()
            val ended = res["ended"].doubleValue
            when {
                ended != null -> parts.add(if (ended == 0.0) "No class countdowns were reported open" else "Ended ${ended.toInt()} countdown${if (ended == 1.0) "" else "s"}")
                devices == 0 -> parts.add("No phone registered")
                else -> {
                    val which = when {
                        android > 0 && ios > 0 -> "phone"
                        android > 0 -> "Android phone"
                        else -> "iPhone"
                    }
                    parts.add("Delivered to $delivered of $devices $which${if (devices == 1) "" else "s"}")
                }
            }
            val inApp = res["inApp"].boolValue == true
            if (inApp) parts.add("added to the bell")
            Dates.parse(res["startsAt"].stringValue)?.let { parts.add("class starts ${AdminParse.time(it)}") }
            Dates.parse(res["endsAt"].stringValue)?.let { parts.add("ends ${AdminParse.time(it)}") }
            val ok = if (devices > 0) delivered == devices else inApp || ended != null
            val failures = results.filter { it["ok"].boolValue != true }.map { r ->
                val platform = if (r["platform"].stringValue == "android") "Android" else "iPhone"
                val device = r["device"].stringValue ?: "device"
                val env = r["environment"].stringValue?.let { " ($it)" }.orEmpty()
                val status = (r["status"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let { "$it " }.orEmpty()
                "$platform $device$env: $status${r["reason"].stringValue ?: "failed"}"
            }
            return SimResult(title, parts.joinToString(" · "), ok, failures)
        }
    }
}

// endregion
