package education.studyspot.app.logic

import education.studyspot.app.logic.WorksheetParser.ClassRef
import education.studyspot.app.logic.WorksheetParser.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorksheetParserTest {
    // A Monday.
    private val today = LocalDate.of(2026, 11, 23)
    private val classes = listOf(
        ClassRef("bio", "AP Biology"),
        ClassRef("eng", "English 10"),
        ClassRef("hist", "World History"),
        ClassRef("chem", "Chemistry Honors"),
    )

    @Test fun parsesATypicalWorksheet() {
        val text = """
            Name: ______________ Date: ________ Period: ___
            AP Biology
            Cell Respiration Review
            Due: Nov 25
            Directions: Answer each question in complete sentences.
            1. What is the role of ATP in the cell?
            2. Compare aerobic and anaerobic respiration.
        """.trimIndent()
        val r = WorksheetParser.parse(text, classes, today)
        assertEquals("Cell Respiration Review", r.title)
        assertEquals(LocalDate.of(2026, 11, 25), r.dueDate)
        assertEquals("bio", r.classId)
        assertTrue(r.description.startsWith("AP Biology"))
        assertFalse(r.description.contains("Cell Respiration Review"))
        assertTrue(r.description.contains("1. What is the role of ATP"))
        assertFalse(r.description.contains("Name:"))
    }

    @Test fun tallestLineIsTheTitleWhenHeightsAreKnown() {
        val lines = listOf(
            Line("Name ____________ Date ________", 12.0),
            Line("Mr. Ortiz - World History", 12.0),
            Line("The Causes of World War I", 30.0),
            Line("Read pages 210-224 and answer the questions.", 12.0),
            Line("Due Friday", 12.0),
        )
        val r = WorksheetParser.parse(lines, classes, today)
        assertEquals("The Causes of World War I", r.title)
        assertEquals(LocalDate.of(2026, 11, 27), r.dueDate)
        assertEquals("hist", r.classId)
        assertTrue(r.description.contains("Mr. Ortiz"))
        assertFalse(r.description.contains("The Causes of World War I"))
    }

    @Test fun junkLines() {
        listOf(
            "Name: ____________",
            "Name _______ Date _______ Period ____",
            "Name Date Period",
            "Period 3",
            "Date:",
            "Score: ___ / 20",
            "Page 2 of 4",
            "3",
            "__________________",
            "Class: Biology",
        ).forEach { assertTrue(it, WorksheetParser.isJunk(it)) }
        listOf(
            "Photosynthesis Lab",
            "Date of the French Revolution essay prompt and outline",
            "Classwork: Fractions",
        ).forEach { assertFalse(it, WorksheetParser.isJunk(it)) }
    }

    @Test fun dueDateFormats() {
        fun due(s: String) = WorksheetParser.findDueDate(listOf(s), today)
        assertEquals(LocalDate.of(2026, 11, 25), due("Due: Nov 25"))
        assertEquals(LocalDate.of(2026, 11, 25), due("Due Nov. 25th"))
        assertEquals(LocalDate.of(2026, 12, 3), due("Due: December 3rd, 2026"))
        assertEquals(LocalDate.of(2026, 12, 3), due("Due 3 December"))
        assertEquals(LocalDate.of(2026, 11, 25), due("Due 11/25"))
        assertEquals(LocalDate.of(2026, 11, 25), due("due date: 11/25/26"))
        assertEquals(LocalDate.of(2026, 11, 25), due("Due 2026-11-25"))
        assertEquals(LocalDate.of(2026, 11, 27), due("Due Friday"))
        assertEquals(LocalDate.of(2026, 11, 27), due("Due: Fri."))
        assertEquals(LocalDate.of(2026, 11, 30), due("Due Monday")) // today is Monday -> next week
        assertEquals(LocalDate.of(2026, 11, 24), due("Due tomorrow"))
        assertEquals(LocalDate.of(2026, 11, 23), due("Turn in today"))
        assertEquals(LocalDate.of(2026, 11, 25), due("Due Wednesday, Nov 25"))
    }

    @Test fun dateWithoutYearRollsIntoNextYear() {
        assertEquals(LocalDate.of(2027, 1, 8), WorksheetParser.findDueDate(listOf("Due Jan 8"), today))
        // Recently past dates stay in this year (a sheet scanned a bit late).
        assertEquals(LocalDate.of(2026, 11, 10), WorksheetParser.findDueDate(listOf("Due 11/10"), today))
    }

    @Test fun prefersTheDueDateOverOtherDates() {
        val lines = listOf(
            "Date: Nov 20, 2026",
            "Unit test on December 10",
            "Project due on Dec 4",
        )
        assertEquals(LocalDate.of(2026, 12, 4), WorksheetParser.findDueDate(lines, today))
    }

    @Test fun dueLabelOnItsOwnLine() {
        assertEquals(LocalDate.of(2026, 12, 1), WorksheetParser.findDueDate(listOf("Due:", "12/1"), today))
    }

    @Test fun fallbackIgnoresAmbiguousNumbers() {
        val lines = listOf("Fractions practice", "Add 3/4 and 1/2", "On Sunday we went to the park")
        assertNull(WorksheetParser.findDueDate(lines, today))
        assertEquals(
            LocalDate.of(2026, 12, 2),
            WorksheetParser.findDueDate(listOf("Quiz Wednesday December 2", "Add 3/4"), today),
        )
        // "Section 3.5" isn't March 5.
        assertNull(WorksheetParser.findDueDate(listOf("Section 3.5 review"), today))
    }

    @Test fun classMatching() {
        assertEquals("eng", WorksheetParser.matchClass("ENGLISH 10 - Ms. Lee", classes))
        assertEquals("chem", WorksheetParser.matchClass("Chemistry worksheet: moles", classes))
        assertEquals("bio", WorksheetParser.matchClass("biology unit 4", classes))
        assertNull(WorksheetParser.matchClass("Algebra II", classes))
        // Longest full name wins.
        val more = classes + ClassRef("hist2", "AP World History")
        assertEquals("hist2", WorksheetParser.matchClass("AP World History - DBQ", more))
        // Ambiguous partial matches pick nothing.
        val twoHistories = listOf(ClassRef("a", "US History"), ClassRef("b", "World History"))
        assertNull(WorksheetParser.matchClass("History essay", twoHistories))
    }

    @Test fun descriptionIsCapped() {
        val body = (1..200).joinToString("\n") { "Question $it: explain the answer." }
        val r = WorksheetParser.parse("Big Review Packet\n$body", emptyList(), today)
        assertEquals("Big Review Packet", r.title)
        assertTrue(r.description.length <= WorksheetParser.MAX_DESCRIPTION + 1)
        assertTrue(r.description.endsWith("…"))
        assertNull(r.classId)
    }

    @Test fun emptyInput() {
        val r = WorksheetParser.parse("", classes, today)
        assertEquals("", r.title)
        assertNull(r.dueDate)
        assertNull(r.classId)
        assertEquals("", r.description)
    }
}
