import SwiftUI

struct StreaksView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(Router.self) private var router
    @AppStorage("cf-streaks-unverified-dismissed") private var unverifiedDismissed = false

    var body: some View {
        let isDemo = auth.user?.isDemo == true
        let streaks = isDemo ? DemoData.streaks : Streaks.build(classes: data.classes, assignments: data.assignments, connections: data.connections)
        let summary = isDemo ? DemoData.streakSummary : Streaks.summary(streaks)

        List {
            Section {
                statusBox
            } header: {
                Text("Complete every assignment in a class to keep its streak alive. One overdue assignment breaks it. Streaks from synced Google Classroom and Blackboard classes are verified; manual classes are unverified.")
                    .font(.subheadline)
                    .textCase(nil)
                    .foregroundStyle(.secondary)
                    .padding(.bottom, 6)
            }
            .glassRows()

            Section {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    StatCard(label: "Active streaks", value: "\(summary.active)", symbol: "flame.fill", symbolTint: .orange)
                    StatCard(label: "At risk", value: "\(summary.atRisk)", symbol: "exclamationmark.shield.fill", symbolTint: .orange)
                    StatCard(label: "Broken", value: "\(summary.broken)", symbol: "exclamationmark.triangle.fill", symbolTint: .red)
                    StatCard(label: "Best streak", value: "\(summary.best)", symbol: "trophy.fill", symbolTint: .yellow)
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }

            Section {
                if streaks.isEmpty {
                    ContentUnavailableView {
                        Label("No classes yet", systemImage: "graduationcap")
                    } description: {
                        Text("Add a class to start a streak.")
                    } actions: {
                        Button("Add a class") { router.tab = .classes }.glassButtonStyle(prominent: true)
                    }
                } else {
                    ForEach(streaks) { StreakCardView(streak: $0) }
                }
            }
            .glassRows()

            if auth.user?.leaderboardEnabled == true {
                Section { LeaderboardView() } header: { Label("Leaderboard", systemImage: "trophy") }.glassRows()
            }
        }
        .navigationTitle("Streaks")
        .refreshable {
            await data.reloadData()
            await data.refreshConnections()
        }
    }

    @ViewBuilder private var statusBox: some View {
        let c = data.connections
        if c.classroom || c.blackboard || auth.user?.isDemo == true {
            let sources = [c.classroom ? "Google Classroom" : nil, c.blackboard ? "Blackboard" : nil].compactMap { $0 }.joined(separator: " and ")
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Verified").font(.subheadline.weight(.semibold)).foregroundStyle(.green)
                    Text("\(sources) \(c.classroom && c.blackboard ? "are" : "is") connected. Streaks from synced classes are verified.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            } icon: {
                Image(systemName: "checkmark.shield.fill").foregroundStyle(.green)
            }
        } else if !unverifiedDismissed {
            HStack(alignment: .top) {
                Label {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Unverified").font(.subheadline.weight(.semibold)).foregroundStyle(.orange)
                        Text("Neither Google Classroom nor Blackboard is connected. You can continue with streaks based on your manual classes, but your streaks can not be verified.")
                            .font(.caption).foregroundStyle(.secondary)
                        Button("Connect") { router.showSettings = true }.font(.caption.weight(.semibold))
                    }
                } icon: {
                    Image(systemName: "exclamationmark.shield.fill").foregroundStyle(.orange)
                }
                Spacer()
                Button { unverifiedDismissed = true } label: { Image(systemName: "xmark") }
                    .buttonStyle(.borderless).foregroundStyle(.secondary)
                    .accessibilityLabel("Dismiss")
            }
        }
    }
}

struct StreakCardView: View {
    var streak: ClassStreak

    var body: some View {
        let s = streak
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                ColorDot(hex: s.schoolClass.color, size: 10)
                Text(s.schoolClass.name).font(.body.weight(.medium)).lineLimit(1)
                if s.verified {
                    Pill(text: "Verified", color: .green, symbol: "checkmark.seal.fill")
                } else {
                    Pill(text: "Unverified", color: .orange, symbol: "exclamationmark.shield")
                }
                Spacer()
                Pill(text: s.status.label, color: s.status.color)
            }
            HStack(spacing: 12) {
                Image(systemName: "flame.fill").font(.system(size: 34)).foregroundStyle(s.status.flameColor)
                    .symbolEffect(.bounce, value: s.streak)
                VStack(alignment: .leading) {
                    Text("\(s.streak)").font(.title.bold())
                    Text("streak").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Text("\(s.completed)/\(s.total) done").font(.subheadline).foregroundStyle(.secondary)
            }
            if s.total > 0 {
                ProgressBar(value: Double(s.completed) / Double(s.total) * 100)
            }
        }
        .padding(.vertical, 4)
    }
}

struct LeaderboardView: View {
    @Environment(AuthStore.self) private var auth
    @State private var board: Leaderboard?
    @State private var loading = true
    private let medals = ["🥇", "🥈", "🥉"]

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity)
            } else if let board, !board.entries.isEmpty {
                ForEach(Array(board.entries.enumerated()), id: \.element.id) { i, e in
                    let isMe = e.userId == board.currentUserId
                    HStack {
                        Text(i < 3 ? medals[i] : "\(i + 1)").frame(width: 28)
                        Text(e.name).fontWeight(isMe ? .semibold : .regular)
                        if isMe { Text("(you)").font(.caption).foregroundStyle(.tint) }
                        Spacer()
                        Label("\(e.bestStreak)", systemImage: "flame.fill").labelStyle(.titleAndIcon)
                            .foregroundStyle(.orange).font(.subheadline.weight(.semibold))
                    }
                    .listRowBackground(
                        ZStack {
                            Rectangle().fill(.thinMaterial)
                            if isMe { Color.accentColor.opacity(0.12) }
                        }
                    )
                }
            } else {
                Text("No streaks yet. Complete assignments to climb the board!").foregroundStyle(.secondary)
            }
        }
        .task {
            defer { loading = false }
            if auth.user?.isDemo == true { board = DemoData.leaderboard; return }
            board = try? await APIClient.shared.invoke("getLeaderboard", as: Leaderboard.self)
        }
    }
}
