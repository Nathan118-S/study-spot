package education.studyspot.app.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Turns the text recognised on a photographed worksheet into a draft
 * assignment ("Scan a worksheet" on the new-assignment form). Pure Kotlin so it
 * is unit-tested on the JVM; the iOS app uses the same heuristics in
 * ios/StudySpot/Logic/WorksheetParser.swift.
 *
 * Heuristics (keep both platforms in step):
 *  - Junk lines are dropped: form fields ("Name: ____", "Date:", "Period 3",
 *    "Teacher", "Score"), lines that are mostly blanks/underscores, page
 *    numbers and lines without at least two letters.
 *  - Title: among the first few useful lines, the tallest one when line heights
 *    are known, otherwise the first one. Lines mentioning "due", instructions
 *    ("Directions: …"), teacher lines ("Mr. Ortiz …") and a line that is just
 *    one of the user's class names are skipped.
 *  - Due date: dates on a line that mentions "due" win; otherwise the first
 *    date found. Understands "Nov 25", "November 25th, 2026", "25 Nov",
 *    "11/25", "11/25/26", "2026-11-25", weekday names ("Friday" = the next
 *    Friday after today), "today", "tomorrow". A date without a year that
 *    would be more than 30 days ago is moved to next year.
 *  - Class: the user's class whose full name appears in the text (longest
 *    wins); otherwise the class sharing the most distinctive words (5+ letters).
 *  - Description: the remaining useful lines, at most [MAX_DESCRIPTION] chars.
 */
object WorksheetParser {

    data class Line(val text: String, val height: Double? = null)

    data class ClassRef(val id: String, val name: String)

    data class Result(
        val title: String,
        val dueDate: LocalDate?,
        val classId: String?,
        val description: String,
    )

    const val MAX_DESCRIPTION = 1000
    private const val MAX_TITLE = 120
    private const val TITLE_WINDOW = 8

    fun parse(lines: List<Line>, classes: List<ClassRef>, today: LocalDate): Result {
        val cleaned = lines.mapNotNull { l ->
            val t = normalize(l.text)
            if (t.isEmpty()) null else Line(t, l.height)
        }
        val useful = cleaned.filterNot { isJunk(it.text) }

        val titleLine = pickTitle(useful, classes)
        val title = titleLine?.text?.let(::cleanTitle).orEmpty()

        val due = findDueDate(cleaned.map { it.text }, today)
        val classId = matchClass(cleaned.joinToString("\n") { it.text }, classes)

        val rest = useful.filter { it !== titleLine }.joinToString("\n") { it.text }.trim()
        return Result(title, due, classId, truncate(rest, MAX_DESCRIPTION))
    }

    /** Convenience for plain text (one line per row, no heights). */
    fun parse(text: String, classes: List<ClassRef>, today: LocalDate): Result =
        parse(text.lines().map { Line(it) }, classes, today)

    // ---- Lines ----

    internal fun normalize(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    private val labelWords = setOf(
        "name", "student", "date", "period", "per", "pd", "hour", "hr", "block", "class", "teacher",
        "score", "grade", "points", "pts", "total", "section", "seat", "row", "id", "no", "first", "last",
    )
    private val labelAlt = labelWords.joinToString("|")
    /** "Name: ____", "Date ______", "Class: Biology", "Score #". */
    private val fieldLabel = Regex("^($labelAlt)\\s*(#|no\\.?)?\\s*[:#_]", RegexOption.IGNORE_CASE)
    /** "Period 3", "Block 2", "Hr. 4". */
    private val periodLabel = Regex("^(period|per|pd|hour|hr|block)\\.?\\s*\\d+\\b", RegexOption.IGNORE_CASE)
    private val pageNumber = Regex("^(page|pg\\.?|p\\.)?\\s*\\d+(\\s*(of|/)\\s*\\d+)?$", RegexOption.IGNORE_CASE)

    internal fun isJunk(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        if (letters < 2) return true
        val blanks = text.count { it == '_' }
        val visible = text.count { !it.isWhitespace() }
        if (blanks >= 3 && blanks * 2 >= visible) return true
        if (pageNumber.matches(text)) return true
        // A header made only of field labels: "Name Date Period".
        val words = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if (words.isNotEmpty() && words.all { it in labelWords }) return true
        if (periodLabel.containsMatchIn(text)) return true
        val m = fieldLabel.find(text) ?: return false
        // "Name: ____ Date: ____", "Class: Biology", but not a long sentence.
        val afterLetters = text.substring(m.range.last + 1).count { it.isLetter() }
        return blanks >= 2 || afterLetters <= 24
    }

    private val instruction = Regex("^(directions|instructions|note|notes|reminder)\\b", RegexOption.IGNORE_CASE)
    private val teacherLine = Regex("^(mr|mrs|ms|miss|dr|prof)\\.?\\s", RegexOption.IGNORE_CASE)

    private fun pickTitle(useful: List<Line>, classes: List<ClassRef>): Line? {
        val classNames = classes.map { words(it.name).joinToString(" ") }.toSet()
        val candidates = useful.take(TITLE_WINDOW).filter { l ->
            l.text.length <= 100 &&
                words(l.text).joinToString(" ") !in classNames && // the course header, not the title
                !teacherLine.containsMatchIn(l.text) &&
                !instruction.containsMatchIn(l.text) &&
                !Regex("\\bdue\\b", RegexOption.IGNORE_CASE).containsMatchIn(l.text) &&
                l.text.count { it.isLetter() } >= 3
        }
        if (candidates.isEmpty()) return useful.firstOrNull()
        val withHeights = candidates.filter { it.height != null }
        if (withHeights.size == candidates.size) {
            val max = withHeights.maxOf { it.height!! }
            // Earliest line within 5% of the tallest.
            return withHeights.first { it.height!! >= max * 0.95 }
        }
        return candidates.first()
    }

    private fun cleanTitle(s: String): String =
        s.trim().trimEnd(':', '-', '–', '—', ' ').let { if (it.length > MAX_TITLE) it.take(MAX_TITLE).trimEnd() else it }

    internal fun truncate(s: String, max: Int): String {
        if (s.length <= max) return s
        val cut = s.take(max)
        val space = cut.lastIndexOfAny(charArrayOf(' ', '\n'))
        return (if (space > max / 2) cut.take(space) else cut).trimEnd() + "…"
    }

    // ---- Dates ----

    private val months: Map<String, Month> = buildMap {
        Month.entries.forEach { m ->
            put(m.getDisplayName(TextStyle.FULL, Locale.US).lowercase(), m)
            put(m.getDisplayName(TextStyle.SHORT, Locale.US).lowercase(), m)
        }
        put("sept", Month.SEPTEMBER)
    }
    private val weekdays: Map<String, DayOfWeek> = buildMap {
        DayOfWeek.entries.forEach { d ->
            put(d.getDisplayName(TextStyle.FULL, Locale.US).lowercase(), d)
            put(d.getDisplayName(TextStyle.SHORT, Locale.US).lowercase(), d)
        }
        put("tues", DayOfWeek.TUESDAY); put("wed", DayOfWeek.WEDNESDAY)
        put("thur", DayOfWeek.THURSDAY); put("thurs", DayOfWeek.THURSDAY)
    }

    private val monthAlt = months.keys.sortedByDescending { it.length }.joinToString("|")
    private val weekdayAlt = weekdays.keys.sortedByDescending { it.length }.joinToString("|")

    private val monthDay = Regex("\\b($monthAlt)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s*(\\d{4}))?", RegexOption.IGNORE_CASE)
    private val dayMonth = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($monthAlt)\\.?\\b(?:,?\\s*(\\d{4}))?", RegexOption.IGNORE_CASE)
    private val iso = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
    /** 11/25/2026, 11-25-26, 11.25.26 */
    private val numericFull = Regex("(?<![\\d/.-])(\\d{1,2})([/.-])(\\d{1,2})\\2(\\d{2}|\\d{4})(?![\\d/.-])")
    /** 11/25 — only trusted next to "due" (it could be a fraction). */
    private val numericShort = Regex("(?<![\\d/.-])(\\d{1,2})/(\\d{1,2})(?![\\d/.-])")
    private val weekday = Regex("\\b(?:next\\s+)?($weekdayAlt)\\b\\.?", RegexOption.IGNORE_CASE)
    private val relative = Regex("\\b(today|tonight|tomorrow)\\b", RegexOption.IGNORE_CASE)
    private val dueWord = Regex("\\b(due|turn(?:ed)?\\s+in|submit|deadline)\\b", RegexOption.IGNORE_CASE)
    private val dateField = Regex("^date\\b", RegexOption.IGNORE_CASE)

    /**
     * Dates in one line, in the order they appear. [loose] (used on lines that
     * mention "due") also accepts "11/25", weekday names and "today"/"tomorrow",
     * which are too ambiguous elsewhere ("3/4 cup", "the sun").
     */
    internal fun datesIn(line: String, today: LocalDate, loose: Boolean = true): List<LocalDate> {
        val found = mutableListOf<Pair<Int, LocalDate>>()
        iso.findAll(line).forEach { m ->
            date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { found += m.range.first to it }
        }
        monthDay.findAll(line).forEach { m ->
            val month = months[m.groupValues[1].lowercase()] ?: return@forEach
            withYear(month.value, m.groupValues[2].toInt(), m.groupValues[3], today)?.let { found += m.range.first to it }
        }
        dayMonth.findAll(line).forEach { m ->
            val month = months[m.groupValues[2].lowercase()] ?: return@forEach
            if (found.any { it.first == m.range.first }) return@forEach
            withYear(month.value, m.groupValues[1].toInt(), m.groupValues[3], today)?.let { found += m.range.first to it }
        }
        // US order (month/day), like the rest of the app.
        numericFull.findAll(line).forEach { m ->
            withYear(m.groupValues[1].toInt(), m.groupValues[3].toInt(), m.groupValues[4], today)?.let { found += m.range.first to it }
        }
        if (loose) {
            numericShort.findAll(line).forEach { m ->
                withYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), "", today)?.let { found += m.range.first to it }
            }
            // A weekday next to a full date ("Friday, Nov 25") only names that date.
            if (found.isEmpty()) {
                weekday.findAll(line).forEach { m ->
                    val d = weekdays[m.groupValues[1].lowercase()] ?: return@forEach
                    var days = (d.value - today.dayOfWeek.value + 7) % 7
                    if (days == 0) days = 7
                    found += m.range.first to today.plusDays(days.toLong())
                }
                relative.findAll(line).forEach { m ->
                    val word = m.groupValues[1].lowercase()
                    found += m.range.first to (if (word == "tomorrow") today.plusDays(1) else today)
                }
            }
        }
        return found.sortedBy { it.first }.map { it.second }.distinct()
    }

    internal fun findDueDate(lines: List<String>, today: LocalDate): LocalDate? {
        // 1. A date on a line mentioning "due" (preferring one after the word),
        //    or on the line right after a bare "Due:".
        lines.forEachIndexed { i, line ->
            val m = dueWord.find(line) ?: return@forEachIndexed
            datesIn(line.substring(m.range.first), today).firstOrNull()?.let { return it }
            datesIn(line, today).firstOrNull()?.let { return it }
            if (i + 1 < lines.size) datesIn(lines[i + 1], today).firstOrNull()?.let { return it }
        }
        // 2. The first unambiguous date anywhere, ignoring the "Date:" field
        //    (that's when the sheet was handed out, not when it's due).
        for (line in lines) {
            if (dateField.containsMatchIn(line)) continue
            datesIn(line, today, loose = false).firstOrNull()?.let { return it }
        }
        return null
    }

    private fun date(y: Int, m: Int, d: Int): LocalDate? =
        runCatching { LocalDate.of(y, m, d) }.getOrNull()

    private fun withYear(month: Int, day: Int, yearText: String, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        if (yearText.isNotEmpty()) {
            val y = yearText.toInt().let { if (it < 100) 2000 + it else it }
            return date(y, month, day)
        }
        val thisYear = date(today.year, month, day) ?: date(today.year + 1, month, day) ?: return null
        return if (thisYear.isBefore(today.minusDays(30))) date(today.year + 1, month, day) else thisYear
    }

    // ---- Classes ----

    private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private val stopWords = setOf("class", "classes", "honors", "course", "period", "section", "intro", "introduction", "advanced", "general")

    internal fun matchClass(text: String, classes: List<ClassRef>): String? {
        val haystack = " " + words(text).joinToString(" ") + " "

        val full = classes
            .filter { c -> words(c.name).isNotEmpty() && haystack.contains(" " + words(c.name).joinToString(" ") + " ") }
            .maxByOrNull { it.name.length }
        if (full != null) return full.id

        val scored = classes.map { c ->
            val distinctive = words(c.name).filter { it.length >= 5 && it !in stopWords }
            c to distinctive.count { haystack.contains(" $it ") }
        }.filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second } ?: return null
        val top = scored.filter { it.second == best }
        return if (top.size == 1) top.first().first.id else null
    }
}
