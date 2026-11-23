package education.studyspot.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * One write made while offline, replayed later against the generic entity API
 * (`/api/entities/<entity>`, server/src/routes/entities.js). Every higher-level
 * change (toggle complete, reschedule, bulk edits, mark read…) is stored as the
 * plain create / update / delete it sends, so replay needs no special cases.
 */
@Serializable
data class PendingOp(
    val seq: Long = 0,
    val kind: Kind,
    /** An [education.studyspot.app.core.Entities] name. */
    val entity: String,
    /** The row's id; for [Kind.Create] the temporary local id (see [OfflineQueue.newTempId]). */
    val id: String,
    /** Create fields or update patch (server column names). */
    val body: JsonObject? = null,
) {
    @Serializable
    enum class Kind {
        @SerialName("create") Create,
        @SerialName("update") Update,
        @SerialName("delete") Delete,
    }

    /** True if this op mentions [tempId] as its id or anywhere in its body (e.g. class_id). */
    fun references(tempId: String): Boolean = id == tempId || body?.let { mentions(it, tempId) } == true

    /** This op with every occurrence of [from] (id and body string values) replaced by [to]. */
    fun remapped(from: String, to: String): PendingOp =
        if (!references(from)) this else copy(id = if (id == from) to else id, body = body?.let { replace(it, from, to) as JsonObject })

    private companion object {
        fun mentions(e: JsonElement, value: String): Boolean = when (e) {
            is JsonObject -> e.values.any { mentions(it, value) }
            is JsonArray -> e.any { mentions(it, value) }
            is JsonPrimitive -> e.isString && e.content == value
        }

        fun replace(e: JsonElement, from: String, to: String): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.mapValues { (_, v) -> replace(v, from, to) })
            is JsonArray -> JsonArray(e.map { replace(it, from, to) })
            is JsonPrimitive -> if (e.isString && e.content == from) JsonPrimitive(to) else e
        }
    }
}

/** How the server answered one replayed op. */
sealed interface SendResult {
    /** Accepted. For a create, [createdId] is the server's id for the row. */
    data class Done(val createdId: String? = null) : SendResult
    /** Refused for good (a 4xx): the op is dropped and [message] shown. */
    data class Rejected(val message: String) : SendResult
    /** Still offline, or the server had a problem (5xx): keep the op and stop for now. */
    data object RetryLater : SendResult
}

data class ReplayReport(
    /** Ops the server accepted. */
    val sent: Int = 0,
    /** Messages for ops the server refused (and that were dropped). */
    val rejected: List<String> = emptyList(),
    /** Temporary id -> server id for every create that went through. */
    val idMap: Map<String, String> = emptyMap(),
    /** Ops still waiting (replay stopped at a [SendResult.RetryLater]). */
    val remaining: Int = 0,
)

/**
 * The persisted, ordered queue of writes made while offline. Pure Kotlin (no
 * Android), so it is unit tested directly (OfflineQueueTest).
 *
 * - Ops replay strictly in the order they were made, one at a time.
 * - A create made offline uses a temporary id ([newTempId]). When it replays,
 *   the server's id replaces the temporary one in every later op (its own
 *   updates/deletes, and fields such as an assignment's class_id).
 * - Deleting something that was created offline and never sent just removes
 *   it from the queue: the server never hears about it.
 * - A refused op (4xx) is dropped; if it was a create, the later ops that need
 *   its id are dropped with it. Offline or 5xx stops replay; the op stays first.
 *
 * Conflicts are last write wins: replayed patches overwrite whatever the server
 * has, field by field, with no merging or version checks. Simple and
 * predictable for a single-user planner.
 *
 * [persist] is called with the new list after every change.
 */
class OfflineQueue(
    initial: List<PendingOp> = emptyList(),
    private val persist: (List<PendingOp>) -> Unit = {},
) {
    private val _ops = MutableStateFlow(initial.sortedBy { it.seq })
    val ops: StateFlow<List<PendingOp>> = _ops.asStateFlow()

    private var nextSeq: Long = (initial.maxOfOrNull { it.seq } ?: 0) + 1

    val isEmpty: Boolean get() = _ops.value.isEmpty()
    val size: Int get() = _ops.value.size

    /** Adds [op] at the end (its seq is assigned here). */
    @Synchronized
    fun enqueue(op: PendingOp) {
        if (op.kind == PendingOp.Kind.Delete && isTempId(op.id) && _ops.value.any { it.kind == PendingOp.Kind.Create && it.id == op.id }) {
            // Never reached the server: forget it and everything that touches it.
            change { list -> list.filterNot { it.references(op.id) } }
            return
        }
        val stamped = op.copy(seq = nextSeq++)
        change { it + stamped }
    }

    /** Replaces the queue with ops read back from disk (no [persist] call). */
    @Synchronized
    fun restore(ops: List<PendingOp>) {
        _ops.value = ops.sortedBy { it.seq }
        nextSeq = maxOf(nextSeq, (ops.maxOfOrNull { it.seq } ?: 0) + 1)
    }

    @Synchronized
    fun clear() {
        change { emptyList() }
    }

    /**
     * Sends the queued ops in order through [send] until the queue is empty or
     * [send] says [SendResult.RetryLater]. Ops enqueued while this runs are
     * sent too.
     */
    suspend fun replay(send: suspend (PendingOp) -> SendResult): ReplayReport {
        var sent = 0
        val rejected = mutableListOf<String>()
        val idMap = mutableMapOf<String, String>()
        while (true) {
            val head = _ops.value.firstOrNull() ?: break
            when (val result = send(head)) {
                is SendResult.Done -> {
                    sent++
                    val newId = result.createdId
                    synchronized(this) {
                        change { list ->
                            val rest = list.filterNot { it.seq == head.seq }
                            if (head.kind == PendingOp.Kind.Create && newId != null && newId != head.id) {
                                rest.map { it.remapped(head.id, newId) }
                            } else {
                                rest
                            }
                        }
                    }
                    if (head.kind == PendingOp.Kind.Create && newId != null) idMap[head.id] = newId
                }
                is SendResult.Rejected -> {
                    rejected += result.message
                    synchronized(this) {
                        change { list ->
                            val rest = list.filterNot { it.seq == head.seq }
                            if (head.kind == PendingOp.Kind.Create && isTempId(head.id)) rest.filterNot { it.references(head.id) } else rest
                        }
                    }
                }
                SendResult.RetryLater -> break
            }
        }
        return ReplayReport(sent, rejected, idMap, _ops.value.size)
    }

    private fun change(transform: (List<PendingOp>) -> List<PendingOp>) {
        _ops.update(transform)
        persist(_ops.value)
    }

    companion object {
        private const val TEMP_PREFIX = "local-"

        fun newTempId(): String = TEMP_PREFIX + UUID.randomUUID().toString()

        fun isTempId(id: String?): Boolean = id?.startsWith(TEMP_PREFIX) == true
    }
}

/**
 * [op] applied to a local list of rows, the way the server would apply it:
 * a create adds the row (or replaces one with the same id), an update merges
 * the patch into the row's JSON, a delete removes it. Idempotent, so queued
 * ops can be laid over freshly loaded (or cached) data at any time.
 */
fun <T> applyPendingOp(
    list: List<T>,
    op: PendingOp,
    serializer: KSerializer<T>,
    json: Json,
    idOf: (T) -> String,
    prependCreates: Boolean = true,
): List<T> = when (op.kind) {
    PendingOp.Kind.Create -> {
        val fields = JsonObject((op.body ?: JsonObject(emptyMap())) + ("id" to JsonPrimitive(op.id)))
        val row = runCatching { json.decodeFromJsonElement(serializer, fields) }.getOrNull()
        when {
            row == null -> list
            list.any { idOf(it) == op.id } -> list.map { if (idOf(it) == op.id) row else it }
            prependCreates -> listOf(row) + list
            else -> list + row
        }
    }
    PendingOp.Kind.Update -> list.map { item ->
        val patch = op.body
        if (idOf(item) != op.id || patch == null) {
            item
        } else {
            runCatching {
                val current = json.encodeToJsonElement(serializer, item) as JsonObject
                json.decodeFromJsonElement(serializer, JsonObject(current + patch))
            }.getOrDefault(item)
        }
    }
    PendingOp.Kind.Delete -> list.filterNot { idOf(it) == op.id }
}
