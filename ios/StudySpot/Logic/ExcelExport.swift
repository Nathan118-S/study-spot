import Foundation

/// Dependency-free Excel export using SpreadsheetML 2003 (.xls) — a real
/// multi-sheet workbook that Excel, Numbers and LibreOffice open natively.
enum ExcelExport {
    enum Cell { case text(String), number(Double) }

    struct Sheet {
        var name: String
        var columns: [String]
        var rows: [[Cell]]
    }

    static func escape(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }

    private static func cell(_ c: Cell) -> String {
        switch c {
        case .number(let n): return "<Cell><Data ss:Type=\"Number\">\(n)</Data></Cell>"
        case .text(let s): return "<Cell><Data ss:Type=\"String\">\(escape(s))</Data></Cell>"
        }
    }

    static func workbook(_ sheets: [Sheet]) -> String {
        let worksheets = sheets.map { sheet in
            let header = "<Row>" + sheet.columns.map { cell(.text($0)) }.joined() + "</Row>"
            let body = sheet.rows.map { "<Row>" + $0.map(cell).joined() + "</Row>" }.joined()
            return "<Worksheet ss:Name=\"\(escape(sheet.name))\"><Table>\(header)\(body)</Table></Worksheet>"
        }.joined(separator: "\n")
        return """
        <?xml version="1.0"?>
        <?mso-application progid="Excel.Sheet"?>
        <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
         xmlns:o="urn:schemas-microsoft-com:office:office"
         xmlns:x="urn:schemas-microsoft-com:office:excel"
         xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
        \(worksheets)
        </Workbook>
        """
    }

    /// Builds the Study Spot export and writes it to a temporary file.
    static func export(assignments: [Assignment], classes: [SchoolClass]) throws -> URL {
        let classNames = Dictionary(classes.map { ($0.id, $0.name) }, uniquingKeysWith: { a, _ in a })
        let df = DateFormatter()
        df.dateStyle = .short
        df.timeStyle = .short
        let aRows: [[Cell]] = assignments.map { a in
            [
                .text(a.title),
                .text(a.classId.flatMap { classNames[$0] } ?? a.className ?? ""),
                .text(a.type.rawValue),
                .text(a.priority.rawValue),
                .text(a.dueDate.map(df.string(from:)) ?? ""),
                .text(a.completed ? "Yes" : "No"),
                .number(a.progress),
                .number(a.points),
                a.score.map { .number($0) } ?? .text(""),
                .text(a.source.rawValue),
                .text(a.notes),
            ]
        }
        let cRows: [[Cell]] = classes.map { c in
            [.text(c.name), .text(c.teacherName ?? ""), .text(c.color), .text(c.source.rawValue)]
        }
        let xml = workbook([
            Sheet(name: "Assignments",
                  columns: ["Title", "Class", "Type", "Priority", "Due date", "Completed", "Progress", "Points", "Score", "Source", "Notes"],
                  rows: aRows),
            Sheet(name: "Classes", columns: ["Name", "Teacher", "Color", "Source"], rows: cRows),
        ])
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("study-spot-export.xls")
        try xml.data(using: .utf8)!.write(to: url, options: .atomic)
        return url
    }
}
