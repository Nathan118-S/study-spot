import Foundation
import WidgetKit

/// Writes a small snapshot of the user's data into the shared App Group and
/// asks WidgetKit to refresh when it changed.
@MainActor
enum WidgetBridge {
    static func publish(user: User?, data: DataStore, accent: AccentChoice) {
        guard let user, data.hasLoaded else {
            if user == nil, SharedStore.save(.signedOut) { WidgetCenter.shared.reloadAllTimelines() }
            return
        }
        let colors = Dictionary(data.classes.map { ($0.id, $0.color) }, uniquingKeysWith: { a, _ in a })
        let upcoming = data.assignments
            .filter { !$0.completed }
            .sorted { ($0.dueDate ?? .distantFuture) < ($1.dueDate ?? .distantFuture) }
            .prefix(20)
            .map { a in
                WidgetAssignment(
                    id: a.id, title: a.title, className: a.className ?? "Uncategorized",
                    classColor: a.classId.flatMap { colors[$0] } ?? "#94a3b8",
                    dueDate: a.dueDate, priority: a.priority.rawValue, progress: a.progress
                )
            }

        let streaks = user.isDemo
            ? DemoData.streaks
            : Streaks.build(classes: data.classes, assignments: data.assignments, connections: data.connections)
        let best = streaks.max { $0.streak < $1.streak }
        let summary = Streaks.summary(streaks)

        var monday = Calendar(identifier: .gregorian)
        monday.firstWeekday = 2
        let weekStart = monday.dateInterval(of: .weekOfYear, for: Date())?.start ?? Date()
        let doneThisWeek = data.assignments.filter { a in
            a.completed && (a.updatedDate ?? a.createdDate ?? .distantPast) >= weekStart
        }.count

        let snapshot = WidgetSnapshot(
            signedIn: true,
            userName: user.displayName,
            accentHex: accent.hex,
            upcoming: Array(upcoming),
            completedThisWeek: doneThisWeek,
            bestStreak: best?.streak ?? 0,
            bestStreakClass: (best?.streak ?? 0) > 0 ? best?.schoolClass.name : nil,
            bestStreakColor: (best?.streak ?? 0) > 0 ? best?.schoolClass.color : nil,
            activeStreaks: summary.active,
            atRiskStreaks: summary.atRisk
        )
        if SharedStore.save(snapshot) { WidgetCenter.shared.reloadAllTimelines() }
    }
}
