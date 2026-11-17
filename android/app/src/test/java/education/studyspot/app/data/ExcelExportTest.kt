package education.studyspot.app.data

import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentType
import education.studyspot.app.model.Priority
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ExcelExportTest {

    @Test
    fun escapesXmlAndDropsControlCharacters() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot; &apos;e&apos;", ExcelExport.escape("a & b <c> \"d\" 'e'"))
        assertEquals("tab\tnew\nline", ExcelExport.escape("tab\tnew\nline\u0001"))
    }

    @Test
    fun workbookHasSpreadsheetMlHeaderAndSheets() {
        val xml = ExcelExport.workbook(
            listOf(
                ExcelExport.Sheet("A & B", listOf("Name", "N"), listOf(listOf(ExcelExport.Cell.Text("x"), ExcelExport.Cell.Number(2.5)))),
            ),
        )
        assertTrue(xml.startsWith("<?xml version=\"1.0\"?>\n<?mso-application progid=\"Excel.Sheet\"?>\n<Workbook"))
        assertTrue(xml.contains("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\""))
        assertTrue(xml.contains("<Worksheet ss:Name=\"A &amp; B\"><Table>"))
        assertTrue(xml.contains("<Row><Cell><Data ss:Type=\"String\">Name</Data></Cell><Cell><Data ss:Type=\"String\">N</Data></Cell></Row>"))
        assertTrue(xml.contains("<Cell><Data ss:Type=\"Number\">2.5</Data></Cell>"))
        assertTrue(xml.endsWith("</Workbook>"))
    }

    @Test
    fun buildsAssignmentsAndClassesSheets() {
        val math = SchoolClass(id = "c1", name = "Math <AP>", color = "#ff0000", teacherName = "Ms. Lee", source = Source.GoogleClassroom)
        val a = Assignment(
            id = "a1",
            title = "Problem set 3",
            classId = "c1",
            className = "Old name",
            dueDate = Instant.parse("2026-03-04T15:00:00Z"),
            priority = Priority.High,
            type = AssignmentType.Project,
            completed = true,
            progress = 100.0,
            points = 25.0,
            score = 23.5,
            notes = "Show work",
            source = Source.GoogleClassroom,
        )
        val b = Assignment(id = "a2", title = "Read ch. 4", className = "History")
        val xml = ExcelExport.build(listOf(a, b), listOf(math), ZoneId.of("UTC"), Locale.US)

        assertTrue(xml.contains("<Worksheet ss:Name=\"Assignments\">"))
        assertTrue(xml.contains("<Worksheet ss:Name=\"Classes\">"))
        ExcelExport.assignmentColumns.forEach { assertTrue(it, xml.contains("<Data ss:Type=\"String\">$it</Data>")) }
        // Class name comes from the class list, not the stale class_name.
        assertTrue(xml.contains("<Data ss:Type=\"String\">Math &lt;AP&gt;</Data>"))
        assertFalse(xml.contains("Old name"))
        assertTrue(xml.contains("<Data ss:Type=\"String\">project</Data><"))
        assertTrue(xml.contains("<Data ss:Type=\"String\">high</Data>"))
        assertTrue(xml.contains("<Data ss:Type=\"String\">Yes</Data>"))
        assertTrue(xml.contains("<Data ss:Type=\"Number\">100</Data>"))
        assertTrue(xml.contains("<Data ss:Type=\"Number\">23.5</Data>"))
        assertTrue(xml.contains("<Data ss:Type=\"String\">google_classroom</Data>"))
        assertTrue(xml.contains("3/4/26"))
        // No class id: falls back to class_name; no score: empty text cell.
        assertTrue(xml.contains("<Data ss:Type=\"String\">History</Data>"))
        assertTrue(xml.contains("<Data ss:Type=\"String\">No</Data></Cell><Cell><Data ss:Type=\"Number\">0</Data></Cell>"))
        assertTrue(xml.contains("<Data ss:Type=\"Number\">0</Data></Cell><Cell><Data ss:Type=\"String\"></Data></Cell><Cell><Data ss:Type=\"String\">manual</Data>"))
        assertTrue(xml.contains("<Data ss:Type=\"String\">Ms. Lee</Data>"))
        assertTrue(xml.contains("<Data ss:Type=\"String\">#ff0000</Data>"))
        // Header + 2 assignment rows + header + 1 class row.
        assertEquals(5, Regex("<Row>").findAll(xml).count())
    }
}
