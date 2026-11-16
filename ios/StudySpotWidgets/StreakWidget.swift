import SwiftUI
import WidgetKit

/// Best current study streak: Home Screen small and Lock Screen sizes.
struct StreakWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StreakWidget", provider: SnapshotProvider()) { entry in
            StreakView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
                .widgetURL(DeepLink.streaks)
        }
        .configurationDisplayName("Streak")
        .description("Your best class streak and how many are active or at risk.")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryRectangular])
    }
}

struct StreakView: View {
    @Environment(\.widgetFamily) private var family
    var entry: SnapshotEntry
    private var snap: WidgetSnapshot { entry.snapshot }

    var body: some View {
        if !snap.signedIn {
            if family == .systemSmall { SignedOutView() } else { Image(systemName: "flame") }
        } else {
            switch family {
            case .accessoryCircular: circular
            case .accessoryRectangular: rectangular
            default: small
            }
        }
    }

    private var flameColor: Color { snap.bestStreak > 0 ? .orange : .secondary }

    private var small: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label("Streak", systemImage: "flame.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.orange)
            Spacer(minLength: 0)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Image(systemName: "flame.fill").font(.title).foregroundStyle(flameColor)
                Text("\(snap.bestStreak)").font(.system(size: 44, weight: .bold, design: .rounded))
            }
            if let name = snap.bestStreakClass {
                HStack(spacing: 5) {
                    Circle().fill(Color(hex: snap.bestStreakColor ?? "#94a3b8")).frame(width: 7, height: 7)
                    Text(name).lineLimit(1)
                }
                .font(.caption).foregroundStyle(.secondary)
            } else {
                Text("Finish assignments to start a streak").font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Text("\(snap.activeStreaks) active · \(snap.atRiskStreaks) at risk")
                .font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var circular: some View {
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: -2) {
                Image(systemName: "flame.fill").font(.body).widgetAccentable()
                Text("\(snap.bestStreak)").font(.title3.bold())
            }
        }
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            Label("\(snap.bestStreak)-day streak", systemImage: "flame.fill")
                .font(.headline).widgetAccentable()
            Text(snap.bestStreakClass ?? "No active streak").font(.caption).lineLimit(1)
            Text("\(snap.activeStreaks) active · \(snap.atRiskStreaks) at risk").font(.caption2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview("Small", as: .systemSmall) { StreakWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .preview) }
#Preview("Lock circular", as: .accessoryCircular) { StreakWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .preview) }
