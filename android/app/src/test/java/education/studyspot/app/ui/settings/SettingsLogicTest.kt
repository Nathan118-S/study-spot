package education.studyspot.app.ui.settings

import education.studyspot.app.core.AppJson
import education.studyspot.app.model.GradeEntry
import education.studyspot.app.model.User
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class SettingsLogicTest {

    private fun user(data: String) = AppJson.decodeFromString(User.serializer(), """{"id":"u1","data":$data}""")

    @Test
    fun reminderLeadLabels() {
        assertEquals("1h before", ReminderLeads.label(1.0))
        assertEquals("1.5h before", ReminderLeads.label(1.5))
        assertEquals("1d before", ReminderLeads.label(24.0))
        assertEquals("3d before", ReminderLeads.label(72.0))
        assertEquals("36h before", ReminderLeads.label(36.0))
    }

    @Test
    fun reminderLeadsFromUserData() {
        assertEquals(listOf(24.0), ReminderLeads.from(user("{}")))
        assertEquals(listOf(6.0), ReminderLeads.from(user("""{"reminder_lead_hours":6}""")))
        assertEquals(listOf(1.0, 6.0, 24.0), ReminderLeads.from(user("""{"reminder_lead_hours":[24,"6",1,6,-2]}""")))
        assertEquals(emptyList<Double>(), ReminderLeads.from(user("""{"reminder_lead_hours":[]}""")))
    }

    @Test
    fun hourMinuteRoundTrip() {
        assertEquals(LocalTime.of(22, 0), HourMinute.parse("22:00", LocalTime.NOON))
        assertEquals(LocalTime.of(7, 5), HourMinute.parse("7:05", LocalTime.NOON))
        assertEquals(LocalTime.NOON, HourMinute.parse("25:00", LocalTime.NOON))
        assertEquals(LocalTime.NOON, HourMinute.parse(null, LocalTime.NOON))
        assertEquals("07:05", HourMinute.format(LocalTime.of(7, 5)))
    }

    @Test
    fun gradeScaleDropsBlankRowsAndSorts() {
        val rows = listOf(GradeRow("C", "70"), GradeRow(" ", "50"), GradeRow("A", "90"), GradeRow("B+", "87,5"), GradeRow("X", "abc"))
        assertEquals(listOf(GradeEntry("A", 90.0), GradeEntry("B+", 87.5), GradeEntry("C", 70.0)), gradeScaleFrom(rows))
        assertEquals("""[{"letter":"A","min":90.0}]""", gradeScaleJson(listOf(GradeEntry("A", 90.0))).toString())
    }
}
