package education.studyspot.app.data

import education.studyspot.app.core.AppJson
import education.studyspot.app.core.Entities
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.User
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** The offline write queue (data/OfflineQueue.kt) and its disk storage. */
class OfflineQueueTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun create(entity: String, id: String, vararg fields: Pair<String, String>) = PendingOp(
        kind = PendingOp.Kind.Create,
        entity = entity,
        id = id,
        body = JsonObject(fields.associate { (k, v) -> k to JsonPrimitive(v) }),
    )

    private fun update(entity: String, id: String, vararg fields: Pair<String, Any>) = PendingOp(
        kind = PendingOp.Kind.Update,
        entity = entity,
        id = id,
        body = JsonObject(
            fields.associate { (k, v) ->
                k to when (v) {
                    is Boolean -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                }
            },
        ),
    )

    private fun delete(entity: String, id: String) = PendingOp(kind = PendingOp.Kind.Delete, entity = entity, id = id)

    @Test
    fun replaysInOrder() = runTest {
        val q = OfflineQueue()
        q.enqueue(update(Entities.ASSIGNMENT, "a1", "completed" to true))
        q.enqueue(delete(Entities.ASSIGNMENT, "a2"))
        q.enqueue(update(Entities.NOTIFICATION, "n1", "read" to true))
        val seen = mutableListOf<String>()
        val report = q.replay { op ->
            seen += "${op.kind} ${op.id}"
            SendResult.Done()
        }
        assertEquals(listOf("Update a1", "Delete a2", "Update n1"), seen)
        assertEquals(3, report.sent)
        assertEquals(0, report.remaining)
        assertTrue(q.isEmpty)
    }

    @Test
    fun seqNumbersIncreaseAndSurviveRestore() {
        val q = OfflineQueue()
        q.enqueue(delete(Entities.ASSIGNMENT, "a"))
        q.enqueue(delete(Entities.ASSIGNMENT, "b"))
        val seqs = q.ops.value.map { it.seq }
        assertEquals(seqs.sorted(), seqs)
        assertEquals(2, seqs.toSet().size)

        val restored = OfflineQueue()
        restored.restore(q.ops.value.reversed())
        assertEquals(listOf("a", "b"), restored.ops.value.map { it.id })
        restored.enqueue(delete(Entities.ASSIGNMENT, "c"))
        assertTrue(restored.ops.value.last().seq > seqs.max())
    }

    @Test
    fun createRemapsTempIdInLaterOps() = runTest {
        val q = OfflineQueue()
        val classTemp = OfflineQueue.newTempId()
        val assignmentTemp = OfflineQueue.newTempId()
        q.enqueue(create(Entities.CLASS, classTemp, "name" to "Biology"))
        q.enqueue(create(Entities.ASSIGNMENT, assignmentTemp, "title" to "Lab report", "class_id" to classTemp))
        q.enqueue(update(Entities.ASSIGNMENT, assignmentTemp, "completed" to true))
        q.enqueue(update(Entities.CLASS, classTemp, "color" to "#ff0000"))

        val sent = mutableListOf<PendingOp>()
        val report = q.replay { op ->
            sent += op
            when {
                op.kind == PendingOp.Kind.Create && op.entity == Entities.CLASS -> SendResult.Done("class-1")
                op.kind == PendingOp.Kind.Create -> SendResult.Done("assignment-1")
                else -> SendResult.Done()
            }
        }

        assertEquals(classTemp, sent[0].id)
        // The assignment's create carries the class's real id…
        assertEquals("class-1", sent[1].body!!["class_id"]!!.jsonPrimitive.content)
        // …and later ops target the real ids.
        assertEquals("assignment-1", sent[2].id)
        assertEquals("class-1", sent[3].id)
        assertEquals(mapOf(classTemp to "class-1", assignmentTemp to "assignment-1"), report.idMap)
        assertTrue(sent.filter { it.kind != PendingOp.Kind.Create }.none { OfflineQueue.isTempId(it.id) })
    }

    @Test
    fun rejected4xxIsDroppedAndReplayContinues() = runTest {
        val q = OfflineQueue()
        q.enqueue(update(Entities.ASSIGNMENT, "a1", "title" to "x"))
        q.enqueue(update(Entities.ASSIGNMENT, "a2", "title" to "y"))
        val report = q.replay { op ->
            if (op.id == "a1") SendResult.Rejected("Not allowed") else SendResult.Done()
        }
        assertEquals(listOf("Not allowed"), report.rejected)
        assertEquals(1, report.sent)
        assertTrue(q.isEmpty)
    }

    @Test
    fun rejectedCreateDropsOpsThatNeedItsId() = runTest {
        val q = OfflineQueue()
        val temp = OfflineQueue.newTempId()
        q.enqueue(create(Entities.ASSIGNMENT, temp, "title" to ""))
        q.enqueue(update(Entities.ASSIGNMENT, temp, "completed" to true))
        q.enqueue(update(Entities.ASSIGNMENT, "other", "completed" to true))
        val sent = mutableListOf<String>()
        val report = q.replay { op ->
            sent += op.id
            if (op.kind == PendingOp.Kind.Create) SendResult.Rejected("Title is required") else SendResult.Done()
        }
        assertEquals(listOf(temp, "other"), sent)
        assertEquals(listOf("Title is required"), report.rejected)
        assertTrue(q.isEmpty)
    }

    @Test
    fun retryLaterKeepsOpAndStops() = runTest {
        val q = OfflineQueue()
        q.enqueue(update(Entities.ASSIGNMENT, "a1", "title" to "x"))
        q.enqueue(update(Entities.ASSIGNMENT, "a2", "title" to "y"))
        var calls = 0
        val report = q.replay {
            calls++
            SendResult.RetryLater // offline or 5xx
        }
        assertEquals(1, calls)
        assertEquals(2, report.remaining)
        assertEquals(listOf("a1", "a2"), q.ops.value.map { it.id })

        // Later, back online: everything goes, still in order.
        val order = mutableListOf<String>()
        q.replay { op -> order += op.id; SendResult.Done() }
        assertEquals(listOf("a1", "a2"), order)
    }

    @Test
    fun opsEnqueuedDuringReplayAreSentToo() = runTest {
        val q = OfflineQueue()
        q.enqueue(update(Entities.ASSIGNMENT, "a1", "title" to "x"))
        val order = mutableListOf<String>()
        q.replay { op ->
            order += op.id
            if (op.id == "a1") q.enqueue(update(Entities.ASSIGNMENT, "a2", "title" to "y"))
            SendResult.Done()
        }
        assertEquals(listOf("a1", "a2"), order)
    }

    @Test
    fun deletingSomethingCreatedOfflineCancelsItsOps() = runTest {
        val q = OfflineQueue()
        val temp = OfflineQueue.newTempId()
        q.enqueue(update(Entities.ASSIGNMENT, "keep", "title" to "x"))
        q.enqueue(create(Entities.ASSIGNMENT, temp, "title" to "Draft"))
        q.enqueue(update(Entities.ASSIGNMENT, temp, "completed" to true))
        q.enqueue(delete(Entities.ASSIGNMENT, temp))
        assertEquals(listOf("keep"), q.ops.value.map { it.id })
    }

    @Test
    fun persistIsCalledOnEveryChange() = runTest {
        val snapshots = mutableListOf<Int>()
        val q = OfflineQueue(persist = { snapshots += it.size })
        q.enqueue(delete(Entities.ASSIGNMENT, "a"))
        q.enqueue(delete(Entities.ASSIGNMENT, "b"))
        q.replay { SendResult.Done() }
        assertEquals(listOf(1, 2, 1, 0), snapshots)
    }

    @Test
    fun remapReplacesNestedValuesOnly() {
        val op = PendingOp(
            kind = PendingOp.Kind.Update,
            entity = Entities.ASSIGNMENT,
            id = "x",
            body = buildJsonObject {
                put("class_id", "local-1")
                put("title", "local-1 notes")
                put("completed", true)
            },
        )
        val mapped = op.remapped("local-1", "real-1")
        assertEquals("real-1", mapped.body!!["class_id"]!!.jsonPrimitive.content)
        assertEquals("local-1 notes", mapped.body["title"]!!.jsonPrimitive.content)
        assertEquals("x", mapped.id)
    }

    @Test
    fun applyPendingOpCreatesUpdatesAndDeletes() {
        val base = listOf(Assignment(id = "a1", title = "Essay"), Assignment(id = "a2", title = "Quiz"))
        val temp = "local-abc"
        var list = applyPendingOp(base, create(Entities.ASSIGNMENT, temp, "title" to "Poster"), Assignment.serializer(), AppJson, { it.id })
        assertEquals(listOf(temp, "a1", "a2"), list.map { it.id })
        // Idempotent: applying the same create again doesn't duplicate.
        list = applyPendingOp(list, create(Entities.ASSIGNMENT, temp, "title" to "Poster"), Assignment.serializer(), AppJson, { it.id })
        assertEquals(3, list.size)

        val due = "2026-12-01T15:00:00.000Z"
        list = applyPendingOp(
            list,
            update(Entities.ASSIGNMENT, "a1", "completed" to true, "due_date" to due),
            Assignment.serializer(),
            AppJson,
            { it.id },
        )
        val a1 = list.first { it.id == "a1" }
        assertTrue(a1.completed)
        assertEquals("Essay", a1.title)
        assertEquals(Instant.parse(due), a1.dueDate)

        list = applyPendingOp(list, delete(Entities.ASSIGNMENT, "a2"), Assignment.serializer(), AppJson, { it.id })
        assertEquals(listOf(temp, "a1"), list.map { it.id })
    }

    @Test
    fun bannerText() {
        val zone = java.time.ZoneOffset.UTC
        val now = Instant.parse("2026-11-23T15:00:00Z")
        assertNull(offlineBannerTextFor(offline = false, pending = 0, cached = false, lastUpdated = now, now = now, zone = zone))
        val offline = offlineBannerTextFor(offline = true, pending = 2, cached = true, lastUpdated = now.minusSeconds(3600), now = now, zone = zone)!!
        assertTrue(offline.startsWith("Offline — changes will sync · 2 changes waiting to sync · Last updated "))
        assertEquals("1 change waiting to sync", offlineBannerTextFor(offline = false, pending = 1, cached = false, lastUpdated = now, now = now, zone = zone))
    }

    private fun offlineBannerTextFor(offline: Boolean, pending: Int, cached: Boolean, lastUpdated: Instant?, now: Instant, zone: java.time.ZoneId) =
        education.studyspot.app.ui.components.offlineBannerText(offline, pending, cached, lastUpdated, now, zone)

    @Test
    fun storageRoundTripsAndIsPerUser() {
        val storage = OfflineStorage(tmp.newFolder("offline"))
        val user = User(id = "u1", username = "alex")
        storage.writeCache(
            OfflineCache(
                userId = "u1",
                savedAt = 1_700_000_000_000,
                user = user,
                assignments = listOf(Assignment(id = "a1", title = "Essay", completed = true)),
            ),
        )
        storage.writeQueue("u1", listOf(update(Entities.ASSIGNMENT, "a1", "completed" to true).copy(seq = 1)))

        val cache = storage.readCache("u1")!!
        assertEquals("alex", cache.user!!.username)
        assertEquals("Essay", cache.assignments.single().title)
        assertTrue(cache.assignments.single().completed)
        assertEquals(1, storage.readQueue("u1").size)

        // Another account never sees it.
        assertNull(storage.readCache("u2"))
        assertTrue(storage.readQueue("u2").isEmpty())

        storage.clear()
        assertNull(storage.readCache())
        assertFalse(storage.readQueue("u1").isNotEmpty())
    }
}
