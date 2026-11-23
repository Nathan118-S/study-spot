import Foundation

/// Turns the text recognised on a photographed worksheet into a draft
/// assignment ("Scan worksheet" on the new-assignment form). Same heuristics
/// as the Android app's logic/WorksheetParser.kt — keep the two in step:
///
/// - Junk lines are dropped: form fields ("Name: ____", "Date:", "Period 3",
///   "Teacher", "Score"), lines that are mostly blanks/underscores, page
///   numbers and lines without at least two letters.
/// - Title: among the first few useful lines, the tallest one when line
///   heights are known, otherwise the first one. Lines mentioning "due",
///   instructions ("Directions: …"), teacher lines ("Mr. Ortiz …") and a line
///   that is just one of the user's class names are skipped.
/// - Due date: dates on a line that mentions "due" (or the line after a bare
///   "Due:") win; otherwise the first unambiguous date (one with a month name
///   or a year), ignoring the "Date:" field. Dates are found with
///   NSDataDetector, so "Nov 25", "11/25", "Friday" and "tomorrow" all work. A
///   date without a year that would be more than 30 days ago moves to next year.
/// - Class: the user's class whose full name appears in the text (longest
///   wins); otherwise the class sharing the most distinctive words (5+ letters).
/// - Description: the remaining useful lines, at most `maxDescription` chars.
enum WorksheetParser {
    struct Line: Hashable, Sendable {
        var text: String
        var height: Double? = nil
    }

    struct ClassRef: Sendable {
        var id: String
        var name: String
    }

    struct Result: Sendable {
        var title: String
        /// Start of the due day; the form keeps its own time of day.
        var dueDate: Date?
        var classId: String?
        var description: String
    }

    static let maxDescription = 1000
    private static let maxTitle = 120
    private static let titleWindow = 8

    static func parse(_ lines: [Line], classes: [ClassRef], now: Date = Date(), calendar: Calendar = .current) -> Result {
        let cleaned: [Line] = lines.compactMap { l in
            let t = normalize(l.text)
            return t.isEmpty ? nil : Line(text: t, height: l.height)
        }
        let usefulIdx = cleaned.indices.filter { !isJunk(cleaned[$0].text) }

        let titleIdx = pickTitle(usefulIdx, in: cleaned, classes: classes)
        let title = titleIdx.map { cleanTitle(cleaned[$0].text) } ?? ""
        let due = findDueDate(cleaned.map(\.text), now: now, calendar: calendar)
        let classId = matchClass(cleaned.map(\.text).joined(separator: "\n"), classes: classes)
        let rest = usefulIdx.filter { $0 != titleIdx }.map { cleaned[$0].text }
            .joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        return Result(title: title, dueDate: due, classId: classId, description: truncate(rest, maxDescription))
    }

    // MARK: Lines

    static func normalize(_ s: String) -> String {
        s.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }

    private static let labelWords: Set<String> = [
        "name", "student", "date", "period", "per", "pd", "hour", "hr", "block", "class", "teacher",
        "score", "grade", "points", "pts", "total", "section", "seat", "row", "id", "no", "first", "last",
    ]
    private static let fieldLabel = regex("^(\(labelWords.joined(separator: "|")))\\s*(#|no\\.?)?\\s*[:#_]")
    private static let periodLabel = regex("^(period|per|pd|hour|hr|block)\\.?\\s*\\d+\\b")
    private static let pageNumber = regex("^(page|pg\\.?|p\\.)?\\s*\\d+(\\s*(of|/)\\s*\\d+)?$")
    private static let instruction = regex("^(directions|instructions|note|notes|reminder)\\b")
    private static let teacherLine = regex("^(mr|mrs|ms|miss|dr|prof)\\.?\\s")
    private static let dueWordInTitle = regex("\\bdue\\b")

    static func isJunk(_ text: String) -> Bool {
        let letters = text.filter(\.isLetter).count
        if letters < 2 { return true }
        let blanks = text.filter { $0 == "_" }.count
        let visible = text.filter { !$0.isWhitespace }.count
        if blanks >= 3 && blanks * 2 >= visible { return true }
        if matches(pageNumber, text) { return true }
        // A header made only of field labels: "Name Date Period".
        let ws = text.lowercased().components(separatedBy: CharacterSet.letters.inverted).filter { !$0.isEmpty }
        if !ws.isEmpty && ws.allSatisfy({ labelWords.contains($0) }) { return true }
        if matches(periodLabel, text) { return true }
        guard let m = firstMatch(fieldLabel, text), let r = Range(m.range, in: text) else { return false }
        // "Name: ____ Date: ____", "Class: Biology", but not a long sentence.
        let afterLetters = text[r.upperBound...].filter(\.isLetter).count
        return blanks >= 2 || afterLetters <= 24
    }

    private static func pickTitle(_ useful: [Int], in lines: [Line], classes: [ClassRef]) -> Int? {
        let classNames = Set(classes.map { words($0.name).joined(separator: " ") })
        let candidates = useful.prefix(titleWindow).filter { i in
            let t = lines[i].text
            return t.count <= 100
                && !classNames.contains(words(t).joined(separator: " ")) // the course header, not the title
                && !matches(teacherLine, t)
                && !matches(instruction, t)
                && !matches(dueWordInTitle, t)
                && t.filter(\.isLetter).count >= 3
        }
        guard !candidates.isEmpty else { return useful.first }
        let heights = candidates.compactMap { lines[$0].height }
        if heights.count == candidates.count, let maxH = heights.max() {
            // Earliest line within 5% of the tallest.
            return candidates.first { (lines[$0].height ?? 0) >= maxH * 0.95 }
        }
        return candidates.first
    }

    private static func cleanTitle(_ s: String) -> String {
        var t = s.trimmingCharacters(in: .whitespaces)
        while let last = t.last, ":-–— ".contains(last) { t.removeLast() }
        return t.count > maxTitle ? String(t.prefix(maxTitle)).trimmingCharacters(in: .whitespaces) : t
    }

    static func truncate(_ s: String, _ max: Int) -> String {
        guard s.count > max else { return s }
        let cut = String(s.prefix(max))
        if let space = cut.lastIndex(where: { $0 == " " || $0 == "\n" }),
           cut.distance(from: cut.startIndex, to: space) > max / 2 {
            return String(cut[..<space]).trimmingCharacters(in: .whitespacesAndNewlines) + "…"
        }
        return cut.trimmingCharacters(in: .whitespacesAndNewlines) + "…"
    }

    // MARK: Dates

    private static let dueWord = regex("\\b(due|turn(?:ed)?\\s+in|submit|deadline)\\b")
    private static let dateField = regex("^date\\b")
    private static let monthName = regex("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s*\\d|\\d\\s*(of\\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)")
    private static let explicitYear = regex("\\b\\d{4}\\b|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}")
    private static let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.date.rawValue)

    /// Dates in one line, in order, as start-of-day. `loose` (lines that mention
    /// "due") also accepts "11/25", weekday names and "today"/"tomorrow", which
    /// are too ambiguous elsewhere ("3/4 cup", "the sun").
    static func dates(in line: String, now: Date, calendar: Calendar, loose: Bool = true) -> [Date] {
        guard let detector = Self.detector else { return [] }
        let ns = line as NSString
        var out: [Date] = []
        for m in detector.matches(in: line, range: NSRange(location: 0, length: ns.length)) {
            guard let date = m.date else { continue }
            let text = ns.substring(with: m.range)
            let hasYear = matches(explicitYear, text)
            if !loose && !(matches(monthName, text) || hasYear) { continue }
            var day = calendar.startOfDay(for: date)
            if !hasYear {
                let today = calendar.startOfDay(for: now)
                if let floor = calendar.date(byAdding: .day, value: -30, to: today), day < floor,
                   let next = calendar.date(byAdding: .year, value: 1, to: day) {
                    day = next
                } else if let ceiling = calendar.date(byAdding: .day, value: 335, to: today), day > ceiling,
                          let prev = calendar.date(byAdding: .year, value: -1, to: day) {
                    day = prev // "11/10" scanned on Nov 23 means this November, not next.
                }
            }
            if !out.contains(day) { out.append(day) }
        }
        return out
    }

    static func findDueDate(_ lines: [String], now: Date = Date(), calendar: Calendar = .current) -> Date? {
        // 1. A date on a line mentioning "due" (preferring one after the word),
        //    or on the line right after a bare "Due:".
        for (i, line) in lines.enumerated() {
            guard let m = firstMatch(dueWord, line), let r = Range(m.range, in: line) else { continue }
            if let d = dates(in: String(line[r.lowerBound...]), now: now, calendar: calendar).first { return d }
            if let d = dates(in: line, now: now, calendar: calendar).first { return d }
            if i + 1 < lines.count, let d = dates(in: lines[i + 1], now: now, calendar: calendar).first { return d }
        }
        // 2. The first unambiguous date anywhere, ignoring the "Date:" field
        //    (that's when the sheet was handed out, not when it's due).
        for line in lines where !matches(dateField, line) {
            if let d = dates(in: line, now: now, calendar: calendar, loose: false).first { return d }
        }
        return nil
    }

    // MARK: Classes

    private static let stopWords: Set<String> = ["class", "classes", "honors", "course", "period", "section", "intro", "introduction", "advanced", "general"]

    private static func words(_ s: String) -> [String] {
        s.lowercased().components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
    }

    static func matchClass(_ text: String, classes: [ClassRef]) -> String? {
        let haystack = " " + words(text).joined(separator: " ") + " "
        let full = classes
            .filter { c in !words(c.name).isEmpty && haystack.contains(" " + words(c.name).joined(separator: " ") + " ") }
            .max { $0.name.count < $1.name.count }
        if let full { return full.id }

        let scored: [(ClassRef, Int)] = classes.compactMap { c in
            let distinctive = words(c.name).filter { $0.count >= 5 && !stopWords.contains($0) }
            let n = distinctive.filter { haystack.contains(" \($0) ") }.count
            return n > 0 ? (c, n) : nil
        }
        guard let best = scored.map(\.1).max() else { return nil }
        let top = scored.filter { $0.1 == best }
        return top.count == 1 ? top[0].0.id : nil
    }

    // MARK: Regex helpers

    private static func regex(_ pattern: String) -> NSRegularExpression {
        // Patterns are constants; a typo should fail loudly in development.
        try! NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
    }

    private static func firstMatch(_ re: NSRegularExpression, _ s: String) -> NSTextCheckingResult? {
        re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s))
    }

    private static func matches(_ re: NSRegularExpression, _ s: String) -> Bool {
        firstMatch(re, s) != nil
    }
}
