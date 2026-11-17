import SwiftUI

struct NotificationsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(DataStore.self) private var data
    @Environment(Router.self) private var router

    var body: some View {
        List {
            if data.notifications.isEmpty {
                ContentUnavailableView("No notifications yet", systemImage: "bell.slash")
            }
            ForEach(data.notifications) { n in
                Button {
                    Task { await data.markRead(n) }
                    if let path = n.actionURL, !path.isEmpty { router.open(path: path) }
                } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: icon(n.type))
                            .font(.subheadline)
                            .foregroundStyle(tint(n.type))
                            .frame(width: 32, height: 32)
                            .background(tint(n.type).opacity(0.12), in: Circle())
                        VStack(alignment: .leading, spacing: 3) {
                            Text(n.title).font(.subheadline.weight(.semibold))
                            if !n.content.isEmpty { Text(n.content).font(.caption).foregroundStyle(.secondary) }
                            HStack {
                                Text(DateText.ago(n.createdDate))
                                if let label = n.actionLabel, !label.isEmpty { Text("· \(label)") }
                            }
                            .font(.caption2).foregroundStyle(.tertiary)
                        }
                        Spacer()
                        if !n.read { Circle().fill(Color.accentColor).frame(width: 8, height: 8).padding(.top, 6) }
                    }
                }
                .foregroundStyle(.primary)
                .listRowBackground(
                    ZStack {
                        Rectangle().fill(.thinMaterial)
                        if !n.read { Color.accentColor.opacity(0.08) }
                    }
                )
            }
        }
        .appBackground()
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await data.loadNotifications() }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() }.tint(Palette.secondary) }
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { Task { await data.markAllRead() } } label: { Label("Mark all read", systemImage: "checkmark.circle") }
                        .disabled(data.unreadCount == 0)
                    Button(role: .destructive) { Task { await data.clearNotifications() } } label: { Label("Clear all", systemImage: "trash") }
                        .disabled(data.notifications.isEmpty)
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .task { await data.loadNotifications() }
    }

    private func icon(_ type: String) -> String {
        switch type {
        case "streak": "flame.fill"
        case "test": "testtube.2"
        case "reminder": "bell.fill"
        default: "info.circle.fill"
        }
    }

    private func tint(_ type: String) -> Color {
        switch type {
        case "streak": .orange
        case "test": .blue
        case "reminder": .accentColor
        default: .secondary
        }
    }
}
