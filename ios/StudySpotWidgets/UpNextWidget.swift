import SwiftUI
import WidgetKit

/// Upcoming assignments: Home Screen (small/medium/large) and Lock Screen
/// (circular, rectangular, inline).
struct UpNextWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "UpNextWidget", provider: SnapshotProvider()) { entry in
            UpNextView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Up Next")
        .description("Your next assignments, what's due today, and what's overdue.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge,
                            .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct UpNextView: View {
    @Environment(\.widgetFamily) private var family
    var entry: SnapshotEntry

    private var snap: WidgetSnapshot { entry.snapshot }
    private var now: Date { entry.date }

    var body: some View {
        Group {
            if !snap.signedIn {
                switch family {
                case .accessoryInline: Text("Study Spot: sign in")
                case .accessoryCircular, .accessoryRectangular: Image(systemName: "graduationcap.fill")
                default: SignedOutView()
                }
            } else {
                switch family {
                case .systemSmall: small
                case .systemMedium: medium
                case .systemLarge: large
                case .accessoryCircular: circular
                case .accessoryRectangular: rectangular
                case .accessoryInline: inline
                default: small
                }
            }
        }
        .tint(snap.accent)
    }

    private var next: WidgetAssignment? { snap.upcoming.first }

    // MARK: Home Screen

    private var header: some View {
        HStack(spacing: 4) {
            LogoMark(size: 16)
            Text("Up Next")
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(snap.accent)
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 6) {
            header
            if let a = next {
                Spacer(minLength: 0)
                HStack(spacing: 5) {
                    Circle().fill(Color(hex: a.classColor)).frame(width: 7, height: 7)
                    Text(a.className).lineLimit(1)
                }
                .font(.caption2).foregroundStyle(.secondary)
                Text(a.title).font(.headline).lineLimit(2)
                DueText(assignment: a, now: now)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(a.isOverdue(at: now) ? Color.red : snap.accent)
                Spacer(minLength: 0)
                Text("\(snap.dueToday(at: now).count) due today · \(snap.overdue(at: now).count) overdue")
                    .font(.caption2).foregroundStyle(.secondary)
            } else {
                Spacer()
                allDone
                Spacer()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(next.map { DeepLink.assignment($0.id) } ?? DeepLink.dashboard)
    }

    private var medium: some View {
        HStack(alignment: .top, spacing: 14) {
            VStack(alignment: .leading, spacing: 8) {
                header
                Spacer(minLength: 0)
                stat("\(snap.dueToday(at: now).count)", "Due today", snap.accent)
                stat("\(snap.overdue(at: now).count)", "Overdue", .red)
            }
            .frame(width: 84, alignment: .leading)
            VStack(alignment: .leading, spacing: 6) {
                if snap.upcoming.isEmpty {
                    Spacer(); allDone; Spacer()
                } else {
                    ForEach(snap.upcoming.prefix(3)) { AssignmentLine(assignment: $0, now: now) }
                    Spacer(minLength: 0)
                }
            }
        }
        .widgetURL(DeepLink.dashboard)
    }

    private var large: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                header
                Spacer()
                Text(now, format: .dateTime.weekday(.wide).month(.abbreviated).day())
                    .font(.caption).foregroundStyle(.secondary)
            }
            HStack(spacing: 8) {
                statBox("\(snap.dueToday(at: now).count)", "Due today", snap.accent)
                statBox("\(snap.overdue(at: now).count)", "Overdue", .red)
                statBox("\(snap.dueThisWeek(at: now).count)", "This week", .orange)
                statBox("\(snap.completedThisWeek)", "Done", .green)
            }
            if snap.upcoming.isEmpty {
                Spacer(); allDone.frame(maxWidth: .infinity); Spacer()
            } else {
                VStack(alignment: .leading, spacing: 7) {
                    ForEach(snap.upcoming.prefix(6)) { AssignmentLine(assignment: $0, now: now) }
                }
                Spacer(minLength: 0)
            }
        }
        .widgetURL(DeepLink.dashboard)
    }

    private var allDone: some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: "checkmark.circle.fill").font(.title2).foregroundStyle(.green)
            Text("All caught up").font(.headline)
            Text("Nothing left to do.").font(.caption).foregroundStyle(.secondary)
        }
    }

    private func stat(_ value: String, _ label: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(value).font(.title.bold()).foregroundStyle(color).contentTransition(.numericText())
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
    }

    private func statBox(_ value: String, _ label: String, _ color: Color) -> some View {
        VStack(spacing: 1) {
            Text(value).font(.title3.bold()).foregroundStyle(color)
            Text(label).font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(.background.opacity(0.6), in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: Lock Screen

    private var circular: some View {
        let today = snap.dueToday(at: now).count
        let overdue = snap.overdue(at: now).count
        return ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: -1) {
                Text("\(today + overdue)").font(.title2.bold()).widgetAccentable()
                Text(overdue > 0 ? "late+due" : "due").font(.system(size: 9, weight: .semibold)).textCase(.uppercase)
            }
        }
        .widgetURL(DeepLink.dashboard)
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let a = next {
                Label {
                    Text("Up Next").font(.caption.weight(.semibold))
                } icon: {
                    Image(systemName: a.isOverdue(at: now) ? "exclamationmark.circle.fill" : "graduationcap.fill")
                }
                .widgetAccentable()
                Text(a.title).font(.headline).lineLimit(1)
                HStack(spacing: 3) {
                    DueText(assignment: a, now: now)
                    Text("· \(a.className)").lineLimit(1)
                }
                .font(.caption)
            } else {
                Label("All caught up", systemImage: "checkmark.circle.fill").font(.headline).widgetAccentable()
                Text("Nothing left to do.").font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(next.map { DeepLink.assignment($0.id) } ?? DeepLink.dashboard)
    }

    private var inline: some View {
        let today = snap.dueToday(at: now).count
        let overdue = snap.overdue(at: now).count
        return ViewThatFits {
            if overdue > 0 {
                Label("\(overdue) overdue · \(today) due today", systemImage: "exclamationmark.circle")
            } else if let a = next {
                Label("\(today) due today · \(a.title)", systemImage: "graduationcap")
            } else {
                Label("All caught up", systemImage: "checkmark.circle")
            }
            Label("\(today + overdue) due", systemImage: "graduationcap")
        }
    }
}

#Preview("Small", as: .systemSmall) { UpNextWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .preview) }
#Preview("Medium", as: .systemMedium) { UpNextWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .preview) }
#Preview("Large", as: .systemLarge) { UpNextWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .preview) }
#Preview("Lock rectangular", as: .accessoryRectangular) { UpNextWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .preview) }
#Preview("Lock circular", as: .accessoryCircular) { UpNextWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .preview) }
#Preview("Lock inline", as: .accessoryInline) { UpNextWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .preview) }
