import SwiftUI
import WidgetKit

@main
struct StudySpotWidgetsBundle: WidgetBundle {
    var body: some Widget {
        UpNextWidget()
        StreakWidget()
        StudyTimerLiveActivity()
        ClassStartLiveActivity()
        if #available(iOS 18.0, *) {
            StudyTimerControl()
        }
    }
}

// MARK: - Timeline

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry(date: Date(), snapshot: .preview)
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        let snap: WidgetSnapshot = context.isPreview ? .preview : (SharedStore.load() ?? .preview)
        completion(SnapshotEntry(date: Date(), snapshot: snap))
    }

    /// One entry now, plus one at each upcoming due time in the next day so
    /// "Overdue" and "Due today" flip on time without the app running.
    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let now = Date()
        let snap = SharedStore.load() ?? .signedOut
        let horizon = now.addingTimeInterval(24 * 3600)
        let midnight = Calendar.current.startOfDay(for: now).addingTimeInterval(24 * 3600)
        var dates = [now, midnight]
        dates += snap.upcoming.compactMap(\.dueDate).filter { $0 > now && $0 < horizon }
        let entries = Array(Set(dates)).sorted().prefix(12).map { SnapshotEntry(date: $0, snapshot: snap) }
        completion(Timeline(entries: Array(entries), policy: .after(now.addingTimeInterval(30 * 60))))
    }
}

// MARK: - Shared bits

extension WidgetSnapshot {
    var accent: Color { Color(hex: accentHex) }
}

struct SignedOutView: View {
    var body: some View {
        VStack(spacing: 6) {
            LogoMark(size: 34)
            Text("Open Study Spot to sign in").font(.caption).multilineTextAlignment(.center).foregroundStyle(.secondary)
        }
    }
}

struct DueText: View {
    var assignment: WidgetAssignment
    var now: Date

    var body: some View {
        if let d = assignment.dueDate {
            if assignment.isOverdue(at: now) {
                Text("Overdue").foregroundStyle(.red)
            } else if Calendar.current.isDate(d, inSameDayAs: now) {
                Text("Today, \(d.formatted(date: .omitted, time: .shortened))")
            } else if let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: now),
                      Calendar.current.isDate(d, inSameDayAs: tomorrow) {
                Text("Tomorrow, \(d.formatted(date: .omitted, time: .shortened))")
            } else {
                Text(d.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day()))
            }
        } else {
            Text("No due date")
        }
    }
}

struct AssignmentLine: View {
    var assignment: WidgetAssignment
    var now: Date

    var body: some View {
        Link(destination: DeepLink.assignment(assignment.id)) {
            HStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 2).fill(Color(hex: assignment.classColor)).frame(width: 4)
                VStack(alignment: .leading, spacing: 1) {
                    Text(assignment.title).font(.subheadline.weight(.medium)).lineLimit(1)
                    HStack(spacing: 4) {
                        Text(assignment.className).lineLimit(1)
                        Text("·")
                        DueText(assignment: assignment, now: now)
                            .foregroundStyle(assignment.isOverdue(at: now) ? Color.red : Color.secondary)
                    }
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                if assignment.progress > 0 {
                    Text("\(Int(assignment.progress))%").font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                }
            }
            .frame(maxHeight: 34)
        }
    }
}
