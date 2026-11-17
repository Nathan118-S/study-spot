package education.studyspot.app.ui.admin

import education.studyspot.app.core.AppJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AdminModelsTest {

    @Test
    fun simOptionsReadsAndroidDevicesAndFcm() {
        val json = AppJson.parseToJsonElement(
            """
            {"apns":false,"fcm":true,
             "devices":{"notifications":0,"class_activity":0,"timer_activity":0,"android":2},
             "classes":[{"id":"c1","name":"Math","subject":"Algebra"}],
             "assignments":[{"id":"a1","title":"HW","class_name":"Math"}]}
            """.trimIndent(),
        )
        val o = SimOptions.from(json)
        assertTrue(o.fcm)
        assertFalse(o.apns)
        assertEquals(2, o.androidDevices)
        assertTrue(o.notificationPushReady)
        assertTrue(o.classReady)
        assertEquals("Algebra", o.classes.single().detail)
        assertEquals("HW", o.assignments.single().name)
    }

    @Test
    fun simResultSummarisesPlatforms() {
        val res = AppJson.parseToJsonElement(
            """
            {"ok":true,"inApp":true,"devices":2,"delivered":1,
             "results":[
               {"device":"…abcd1234","platform":"android","ok":true},
               {"device":"…efgh5678","platform":"android","ok":false,"status":404,"reason":"UNREGISTERED"}]}
            """.trimIndent(),
        )
        val r = SimResult.from("Notification", res)
        assertEquals("Delivered to 1 of 2 Android phones · added to the bell", r.summary)
        assertFalse(r.ok)
        assertEquals(listOf("Android …efgh5678: 404 UNREGISTERED"), r.failures)

        val none = SimResult.from("Notification", AppJson.parseToJsonElement("""{"devices":0,"delivered":0,"inApp":true,"results":[]}"""))
        assertEquals("No phone registered · added to the bell", none.summary)
        assertTrue(none.ok)

        val ended = SimResult.from("End", AppJson.parseToJsonElement("""{"devices":0,"delivered":0,"ended":0,"results":[]}"""))
        assertEquals("No class countdowns were reported open", ended.summary)
        assertTrue(ended.ok)
    }

    @Test
    fun recordChangesOnlyIncludesEditedFieldsAndClassName() {
        val original = mapOf(
            "id" to JsonPrimitive("a1"),
            "title" to JsonPrimitive("Old"),
            "class_id" to JsonPrimitive("c1"),
            "class_name" to JsonPrimitive("Math"),
            "points" to JsonPrimitive("25"),
            "created_at" to JsonPrimitive("2026-01-01T00:00:00Z"),
        )
        val edited = original + mapOf("title" to JsonPrimitive("New"), "class_id" to JsonNull, "created_at" to JsonPrimitive("x"))
        val changes = adminRecordChanges(AdminRecordKind.Assignments, original, edited)
        assertEquals(setOf("title", "class_id"), changes.keys)
        assertEquals(JsonNull, changes["class_id"])

        val reassigned = original + mapOf("class_id" to JsonPrimitive("c2"), "class_name" to JsonPrimitive("Art"))
        assertEquals(setOf("class_id", "class_name"), adminRecordChanges(AdminRecordKind.Assignments, original, reassigned).keys)
    }

    @Test
    fun recordTitles() {
        val session = AdminRecord.from(AppJson.parseToJsonElement("""{"id":"s1","duration_minutes":"25","session_type":"focus"}"""))
        assertEquals("25 min focus", session.title(AdminRecordKind.StudySessions))
        val cls = AdminRecord.from(AppJson.parseToJsonElement("""{"id":"c1","name":"Math","teacher_name":"Lee","best_streak":4}"""))
        assertEquals("Math", cls.title(AdminRecordKind.Classes))
        assertEquals("Lee · Best streak 4", cls.subtitle(AdminRecordKind.Classes))
    }

    @Test
    fun minutesLeftRoundsUpAndNeverZero() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals(15, minutesLeft(now.plusSeconds(15 * 60), now))
        assertEquals(1, minutesLeft(now.plusSeconds(10), now))
        assertEquals(2, minutesLeft(now.plusSeconds(61), now))
        assertEquals(1, minutesLeft(now.minusSeconds(30), now))
    }
}
