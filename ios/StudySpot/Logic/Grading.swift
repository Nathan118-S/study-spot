import Foundation

enum Grading {
    static let defaultScale: [GradeEntry] = [
        GradeEntry(letter: "A", min: 90),
        GradeEntry(letter: "B", min: 80),
        GradeEntry(letter: "C", min: 70),
        GradeEntry(letter: "D", min: 60),
        GradeEntry(letter: "F", min: 0),
    ]

    static func normalize(_ scale: [GradeEntry]) -> [GradeEntry] {
        (scale.isEmpty ? defaultScale : scale).sorted { $0.min > $1.min }
    }

    static func letter(score: Double?, points: Double, scale: [GradeEntry]) -> String? {
        guard let score, points > 0 else { return nil }
        let pct = score / points * 100
        let sorted = normalize(scale)
        for entry in sorted where pct >= entry.min { return entry.letter }
        return sorted.last?.letter
    }

    static func percent(_ a: Assignment) -> Int? {
        guard let s = a.score, a.points > 0 else { return nil }
        return Int((s / a.points * 100).rounded())
    }
}

enum StreakStatus: String, Sendable {
    case active, atrisk, broken, empty

    var label: String {
        switch self {
        case .active: "Active"
        case .atrisk: "At risk"
        case .broken: "Broken"
        case .empty: "No assignments"
        }
    }
}

struct ClassStreak: Identifiable, Sendable {
    var schoolClass: SchoolClass
    var verified: Bool
    var streak: Int
    var status: StreakStatus
    var total: Int
    var completed: Int
    var id: String { schoolClass.id }
}

struct StreakSummary: Sendable {
    var active = 0, atRisk = 0, broken = 0, best = 0, total = 0
}

enum Streaks {
    /// The run of completed assignments at the end of the by-due-date list,
    /// broken entirely by any overdue item.
    static func compute(_ assignments: [Assignment], now: Date = Date()) -> (streak: Int, status: StreakStatus, total: Int, completed: Int) {
        let sorted = assignments.sorted { ($0.dueDate ?? .distantPast) < ($1.dueDate ?? .distantPast) }
        let total = sorted.count
        let completed = sorted.filter(\.completed).count
        if total == 0 { return (0, .empty, 0, 0) }
        let hasOverdue = sorted.contains { a in
            guard let d = a.dueDate else { return false }
            return !a.completed && d < now
        }
        if hasOverdue { return (0, .broken, total, completed) }
        var run = 0
        for a in sorted.reversed() {
            if a.completed { run += 1 } else { break }
        }
        return (run, completed == total ? .active : .atrisk, total, completed)
    }

    static func build(classes: [SchoolClass], assignments: [Assignment], connections: Connections, now: Date = Date()) -> [ClassStreak] {
        classes.map { c in
            let items = assignments.filter { $0.classId == c.id }
            let verified = (c.source == .googleClassroom && connections.classroom) || (c.source == .blackboard && connections.blackboard)
            let r = compute(items, now: now)
            return ClassStreak(schoolClass: c, verified: verified, streak: r.streak, status: r.status, total: r.total, completed: r.completed)
        }
    }

    static func summary(_ streaks: [ClassStreak]) -> StreakSummary {
        StreakSummary(
            active: streaks.filter { $0.status == .active }.count,
            atRisk: streaks.filter { $0.status == .atrisk }.count,
            broken: streaks.filter { $0.status == .broken }.count,
            best: streaks.map(\.streak).max() ?? 0,
            total: streaks.count
        )
    }
}

struct WeekBucket: Identifiable, Sendable {
    var start: Date
    var end: Date
    var label: String
    var completed: Int
    var progressPoints: Double
    var cumulative: Int
    var id: Date { start }
}

enum Insights {
    static let weeks = 8

    static func weekly(_ assignments: [Assignment], now: Date = Date()) -> [WeekBucket] {
        var cal = Calendar(identifier: .gregorian)
        cal.firstWeekday = 2 // Monday
        cal.timeZone = .current
        let thisWeekStart = cal.dateInterval(of: .weekOfYear, for: now)?.start ?? cal.startOfDay(for: now)
        let fmt = DateFormatter()
        fmt.setLocalizedDateFormatFromTemplate("MMM d")

        var buckets: [WeekBucket] = (0..<weeks).reversed().map { i in
            let start = cal.date(byAdding: .day, value: -7 * i, to: thisWeekStart)!
            let end = cal.date(byAdding: .day, value: 7, to: start)!
            return WeekBucket(start: start, end: end, label: fmt.string(from: start), completed: 0, progressPoints: 0, cumulative: 0)
        }

        for a in assignments {
            let ref = a.updatedDate ?? a.createdDate ?? a.dueDate
            guard let ref else { continue }
            if let idx = buckets.firstIndex(where: { ref >= $0.start && ref < $0.end }) {
                if a.completed { buckets[idx].completed += 1 }
                if a.progress > 0 { buckets[idx].progressPoints += a.progress }
            }
        }
        var running = 0
        for i in buckets.indices {
            running += buckets[i].completed
            buckets[i].cumulative = running
        }
        return buckets
    }
}
