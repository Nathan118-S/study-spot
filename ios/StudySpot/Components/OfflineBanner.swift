import SwiftUI

/// The small offline pill under the main screens' navigation bar:
/// "Offline — changes will sync", how many changes are waiting, and when the
/// data was last updated (while showing cached data). Hidden when online and
/// up to date (AppToolbarModifier).
struct OfflineBanner: View {
    @Environment(DataStore.self) private var data

    var body: some View {
        if let text = Self.text(offline: data.isOffline, pending: data.pendingChanges,
                                cached: data.showingCachedData, lastUpdated: data.lastUpdated) {
            HStack(spacing: 6) {
                Image(systemName: data.isOffline ? "icloud.slash" : "arrow.triangle.2.circlepath.icloud")
                Text(text)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .font(.caption.weight(.medium))
            .foregroundStyle(Palette.secondary)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Palette.secondary.opacity(0.1), in: Capsule())
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
            .frame(maxWidth: .infinity)
            .accessibilityElement(children: .combine)
            .transition(.opacity)
        }
    }

    static func text(offline: Bool, pending: Int, cached: Bool, lastUpdated: Date?, now: Date = Date()) -> String? {
        var parts: [String] = []
        if offline { parts.append("Offline — changes will sync") }
        if pending > 0 { parts.append(pending == 1 ? "1 change waiting to sync" : "\(pending) changes waiting to sync") }
        if offline || cached, let lastUpdated { parts.append("Last updated \(formatted(lastUpdated, now: now))") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private static func formatted(_ date: Date, now: Date) -> String {
        let cal = Calendar.current
        if cal.isDate(date, inSameDayAs: now) { return date.formatted(date: .omitted, time: .shortened) }
        if let yesterday = cal.date(byAdding: .day, value: -1, to: now), cal.isDate(date, inSameDayAs: yesterday) {
            return "yesterday \(date.formatted(date: .omitted, time: .shortened))"
        }
        return date.formatted(date: .abbreviated, time: .omitted)
    }
}
