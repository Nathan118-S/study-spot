package education.studyspot.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.ui.components.clean
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Dependency-free Excel export using SpreadsheetML 2003 (.xls): a real
 * multi-sheet workbook that Excel, Google Sheets, Numbers and LibreOffice open.
 * Mirrors ios/StudySpot/Logic/ExcelExport.swift and src/lib/exportExcel.js.
 */
object ExcelExport {
    const val FILE_NAME = "study-spot-export.xls"
    const val MIME_TYPE = "application/vnd.ms-excel"

    sealed interface Cell {
        data class Text(val value: String) : Cell
        data class Number(val value: Double) : Cell
    }

    data class Sheet(val name: String, val columns: List<String>, val rows: List<List<Cell>>)

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        // Control characters other than tab/newline aren't allowed in XML 1.0.
        .filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }

    private fun cell(c: Cell): String = when (c) {
        is Cell.Number -> "<Cell><Data ss:Type=\"Number\">${c.value.clean}</Data></Cell>"
        is Cell.Text -> "<Cell><Data ss:Type=\"String\">${escape(c.value)}</Data></Cell>"
    }

    fun workbook(sheets: List<Sheet>): String {
        val worksheets = sheets.joinToString("\n") { sheet ->
            val header = "<Row>" + sheet.columns.joinToString("") { cell(Cell.Text(it)) } + "</Row>"
            val body = sheet.rows.joinToString("") { row -> "<Row>" + row.joinToString("") { cell(it) } + "</Row>" }
            "<Worksheet ss:Name=\"${escape(sheet.name)}\"><Table>$header$body</Table></Worksheet>"
        }
        return "<?xml version=\"1.0\"?>\n" +
            "<?mso-application progid=\"Excel.Sheet\"?>\n" +
            "<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n" +
            " xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n" +
            " xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n" +
            " xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n" +
            "$worksheets\n</Workbook>"
    }

    val assignmentColumns = listOf("Title", "Class", "Type", "Priority", "Due date", "Completed", "Progress", "Points", "Score", "Source", "Notes")
    val classColumns = listOf("Name", "Teacher", "Color", "Source")

    /** The Study Spot export: an Assignments sheet and a Classes sheet. */
    fun build(
        assignments: List<Assignment>,
        classes: List<SchoolClass>,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val classNames = classes.associate { it.id to it.name }
        val df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
        val aRows = assignments.map { a ->
            listOf(
                Cell.Text(a.title),
                Cell.Text(a.classIdOrNull?.let { classNames[it] } ?: a.className.orEmpty()),
                Cell.Text(a.type.wire),
                Cell.Text(a.priority.wire),
                Cell.Text(a.dueDate?.atZone(zone)?.format(df).orEmpty()),
                Cell.Text(if (a.completed) "Yes" else "No"),
                Cell.Number(a.progress),
                Cell.Number(a.points),
                a.score?.let { Cell.Number(it) } ?: Cell.Text(""),
                Cell.Text(a.source.wire),
                Cell.Text(a.notes),
            )
        }
        val cRows = classes.map { c ->
            listOf(Cell.Text(c.name), Cell.Text(c.teacherName.orEmpty()), Cell.Text(c.color), Cell.Text(c.source.wire))
        }
        return workbook(
            listOf(
                Sheet("Assignments", assignmentColumns, aRows),
                Sheet("Classes", classColumns, cRows),
            ),
        )
    }

    /** Writes the workbook to the app cache and returns a shareable content:// URI (see res/xml/file_paths.xml). */
    fun write(context: Context, xml: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, FILE_NAME)
        file.writeText(xml, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
