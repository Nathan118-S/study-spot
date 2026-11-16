import Foundation
import SwiftUI
#if canImport(ActivityKit)
import ActivityKit
#endif

// Compiled into both the app and the widget extension.

/// App Group shared between the app and its widgets. Must match both
/// targets' entitlements (StudySpot.entitlements / StudySpotWidgets.entitlements).
enum SharedStore {
    static let appGroup = "group.com.studyspot.app"
    private static let snapshotKey = "widget_snapshot_v1"

    static var defaults: UserDefaults? { UserDefaults(suiteName: appGroup) }

    /// Saves the snapshot; returns false if it was unchanged.
    @discardableResult
    static func save(_ snapshot: WidgetSnapshot) -> Bool {
        guard let data = try? JSONEncoder().encode(snapshot) else { return false }
        if defaults?.data(forKey: snapshotKey) == data { return false }
        defaults?.set(data, forKey: snapshotKey)
        return true
    }

    static func load() -> WidgetSnapshot? {
        guard let data = defaults?.data(forKey: snapshotKey) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }
}

/// What the widgets show: a small, self-contained copy of the user's data,
/// written by the app whenever assignments, classes or the accent change.
struct WidgetSnapshot: Codable, Equatable {
    var signedIn: Bool
    var userName: String
    var accentHex: String
    /// Unfinished assignments, soonest first (undated last).
    var upcoming: [WidgetAssignment]
    var completedThisWeek: Int
    var bestStreak: Int
    var bestStreakClass: String?
    var bestStreakColor: String?
    var activeStreaks: Int
    var atRiskStreaks: Int

    static let signedOut = WidgetSnapshot(signedIn: false, userName: "", accentHex: "#f19d37", upcoming: [],
                                          completedThisWeek: 0, bestStreak: 0, bestStreakClass: nil,
                                          bestStreakColor: nil, activeStreaks: 0, atRiskStreaks: 0)

    static var preview: WidgetSnapshot {
        let now = Date()
        func at(_ h: Double) -> Date { now.addingTimeInterval(h * 3600) }
        return WidgetSnapshot(
            signedIn: true, userName: "Alex", accentHex: "#f19d37",
            upcoming: [
                WidgetAssignment(id: "p1", title: "Problem Set 4", className: "AP Calculus", classColor: "#6366f1", dueDate: at(-20), priority: "high", progress: 40),
                WidgetAssignment(id: "p2", title: "Essay Draft", className: "English Literature", classColor: "#ec4899", dueDate: at(3), priority: "high", progress: 60),
                WidgetAssignment(id: "p3", title: "Vectors Worksheet", className: "Physics", classColor: "#10b981", dueDate: at(6), priority: "high", progress: 0),
                WidgetAssignment(id: "p4", title: "Quiz 3: Derivatives", className: "AP Calculus", classColor: "#6366f1", dueDate: at(26), priority: "medium", progress: 0),
                WidgetAssignment(id: "p5", title: "Lab Report: Pendulums", className: "Physics", classColor: "#10b981", dueDate: at(50), priority: "medium", progress: 20),
                WidgetAssignment(id: "p6", title: "Socratic Seminar Prep", className: "English Literature", classColor: "#ec4899", dueDate: at(52), priority: "medium", progress: 25),
                WidgetAssignment(id: "p7", title: "Chapter 5 Reading", className: "US History", classColor: "#f59e0b", dueDate: at(98), priority: "low", progress: 0),
            ],
            completedThisWeek: 4, bestStreak: 14, bestStreakClass: "AP Calculus", bestStreakColor: "#6366f1",
            activeStreaks: 2, atRiskStreaks: 1
        )
    }

    /// Still due later today (anything already past due counts as overdue).
    func dueToday(at date: Date) -> [WidgetAssignment] {
        upcoming.filter { a in
            !a.isOverdue(at: date) && (a.dueDate.map { Calendar.current.isDate($0, inSameDayAs: date) } ?? false)
        }
    }

    func overdue(at date: Date) -> [WidgetAssignment] {
        upcoming.filter { $0.isOverdue(at: date) }
    }

    func dueThisWeek(at date: Date) -> [WidgetAssignment] {
        let end = date.addingTimeInterval(7 * 86400)
        return upcoming.filter { a in a.dueDate.map { $0 >= date && $0 < end } ?? false }
    }
}

struct WidgetAssignment: Codable, Equatable, Identifiable, Hashable {
    var id: String
    var title: String
    var className: String
    var classColor: String
    var dueDate: Date?
    var priority: String
    var progress: Double

    func isOverdue(at date: Date) -> Bool { dueDate.map { $0 < date } ?? false }
}

/// Deep links used by widgets and Live Activities.
enum DeepLink {
    static func assignment(_ id: String) -> URL { URL(string: "studyspot://assignment/\(id)")! }
    static let timer = URL(string: "studyspot://timer")!
    static let streaks = URL(string: "studyspot://tab/streaks")!
    static let dashboard = URL(string: "studyspot://tab/dashboard")!
}

#if canImport(ActivityKit)
/// Live Activity for the study timer (Lock Screen + Dynamic Island).
struct StudyTimerAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var phase: String            // focus | short | long
        var phaseLabel: String       // "Focus", "Short Break", "Focus complete"…
        var endsAt: Date?            // nil while paused or finished
        var remaining: Double        // seconds left (used while paused)
        var total: Double            // phase length in seconds
        var finished: Bool = false

        var isRunning: Bool { endsAt != nil && !finished }
        var phaseStart: Date { (endsAt ?? Date()).addingTimeInterval(-total) }
        var progress: Double { total > 0 ? max(0, min(1, 1 - remaining / total)) : 0 }
    }

    var assignmentTitle: String?
    var className: String?
    var accentHex: String
}
#endif

extension Color {
    /// "#rrggbb" → Color (falls back to slate gray).
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else {
            self = Color(red: 0.58, green: 0.64, blue: 0.72)
            return
        }
        self.init(red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
    }
}
