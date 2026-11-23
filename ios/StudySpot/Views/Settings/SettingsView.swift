import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth

    private struct Item: Identifiable {
        var id: String
        var label: String
        var desc: String
        var symbol: String
        var danger = false
    }

    private let items: [Item] = [
        Item(id: "appearance", label: "Appearance", desc: "Theme, app icon, light/dark mode", symbol: "paintpalette"),
        Item(id: "google", label: "Google", desc: "Calendar and Classroom connections", symbol: "powerplug"),
        Item(id: "blackboard", label: "Blackboard", desc: "Connect your school’s Blackboard", symbol: "building.columns"),
        Item(id: "lms", label: "Canvas, Schoology & Brightspace", desc: "Add your school's calendar by link", symbol: "link"),
        Item(id: "sync", label: "Sync", desc: "Refresh synced data", symbol: "arrow.triangle.2.circlepath"),
        Item(id: "leaderboard", label: "Leaderboard", desc: "Show study-streak rankings", symbol: "trophy"),
        Item(id: "onboarding", label: "Onboarding", desc: "Run the setup wizard again", symbol: "arrow.counterclockwise"),
        Item(id: "parent", label: "Parent view", desc: "Share a read-only weekly summary", symbol: "person.2"),
        Item(id: "data", label: "Data", desc: "Export your assignments", symbol: "externaldrive"),
        Item(id: "reminders", label: "Reminders", desc: "Assignment notifications and class Live Activities", symbol: "alarm"),
        Item(id: "grading", label: "Grading", desc: "Letter-grade thresholds", symbol: "graduationcap"),
        Item(id: "security", label: "Security", desc: "Two-factor authentication", symbol: "lock"),
        Item(id: "account", label: "Account", desc: "Your name, username, and password", symbol: "person"),
        Item(id: "danger", label: "Danger Zone", desc: "Log out all devices, delete account", symbol: "exclamationmark.triangle", danger: true),
    ]

    var body: some View {
        List {
            Section {
                ForEach(items) { item in
                    NavigationLink {
                        destination(item.id)
                            .appBackground()
                            .navigationTitle(item.label)
                            .navigationBarTitleDisplayMode(.inline)
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: item.symbol)
                                .foregroundStyle(item.danger ? Color.red : Palette.secondary)
                                .frame(width: 34, height: 34)
                                .background((item.danger ? Color.red : Palette.secondary).opacity(0.12), in: Circle())
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.label).foregroundStyle(item.danger ? .red : .primary)
                                Text(item.desc).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            } footer: {
                Text("Manage your Google connections and account.")
            }
            .glassRows()

            Section {
                NavigationLink { ServerInfoView() } label: { Label("Server", systemImage: "server.rack") }
                NavigationLink { PrivacyPolicyView() } label: { Label("Privacy Policy", systemImage: "hand.raised") }
            }
            .glassRows()

            Section {
                Button(role: .destructive) { auth.signOut() } label: { Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right") }
            } footer: {
                Text("Signed in as \(auth.user?.username ?? "")")
            }
            .glassRows()
        }
        .appBackground()
        .navigationTitle("Settings")
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
    }

    @ViewBuilder
    private func destination(_ id: String) -> some View {
        switch id {
        case "appearance": Form { AppearanceSettings() }
        case "google": Form { GoogleSettings() }
        case "blackboard": Form { BlackboardSettings() }
        case "lms": Form { LMSCalendarLinksSettings() }
        case "sync": Form { SyncSettings() }
        case "leaderboard": Form { LeaderboardSetting() }
        case "onboarding": Form { OnboardingRestart() }
        case "parent": Form { GuardianLinksSettings() }
        case "data": Form { DataExportSettings() }
        case "reminders": Form { ReminderSettings() }
        case "grading": Form { GradingScaleEditor() }
        case "security": Form { TwoFactorSettings() }
        case "account": Form { AccountSettings() }
        default: Form { DangerZone() }
        }
    }
}

// MARK: - Appearance

struct AppearanceSettings: View {
    @Environment(ThemeStore.self) private var theme

    var body: some View {
        @Bindable var theme = theme
        Section {
            Toggle(isOn: Binding(get: { theme.dark }, set: { theme.setDarkMode($0) })) {
                SettingLabel(title: "Dark mode", desc: "Switch between light and dark themes. Disabled while syncing with your device.",
                             symbol: theme.dark ? "moon" : "sun.max")
            }
            .disabled(theme.syncWithDevice)
            Toggle(isOn: $theme.syncWithDevice) {
                SettingLabel(title: "Sync with device", desc: "Automatically match light or dark mode to your system setting.", symbol: "iphone")
            }
            Toggle(isOn: $theme.animations) {
                SettingLabel(title: "Animations", desc: "Show entrance and transition animations. Turn off for a calmer, faster feel.", symbol: "sparkles")
            }
        }
        ColorThemeSection()
        AppIconSection()
        Section {
            WelcomeStylePicker()
        } header: {
            Text("Welcome message")
        } footer: {
            Text("Choose how Study Spot greets you when you open the app.")
        }
    }
}

struct SettingLabel: View {
    var title: String
    var desc: String
    var symbol: String
    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(desc).font(.caption).foregroundStyle(.secondary)
            }
        } icon: {
            Image(systemName: symbol)
        }
    }
}

struct WelcomeStylePicker: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    private let options = [("large", "Large popup", "Full-screen welcome each time you open the app."),
                           ("small", "Small popup", "A compact greeting card."),
                           ("off", "Off", "Skip the welcome message.")]

    var body: some View {
        let value = auth.user?.welcomeStyle ?? "large"
        ForEach(options, id: \.0) { o in
            Button {
                Task {
                    do { try await auth.update(["welcome_style": .string(o.0)]) }
                    catch { toasts.show("Couldn't save", error.localizedDescription, error: true) }
                }
            } label: {
                HStack {
                    VStack(alignment: .leading) {
                        Text(o.1)
                        Text(o.2).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if value == o.0 { Image(systemName: "checkmark").foregroundStyle(.tint) }
                }
            }
            .foregroundStyle(.primary)
        }
    }
}

// MARK: - Google

struct GoogleSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(ToastCenter.self) private var toasts
    @State private var busy: String?
    @State private var loading = true
    @State private var showLinkGuide = false
    @State private var showFileImport = false

    var body: some View {
        Section {
            Text("Connect your own Google account to import assignments. Each user connects separately — your data stays private to you.")
                .font(.subheadline).foregroundStyle(.secondary)
                .sheet(isPresented: $showLinkGuide) { CalendarLinkGuide(kind: .google) }
                .sheet(isPresented: $showFileImport) { CalendarFileImportSheet() }
        } footer: {
            SchoolAccessNote(service: .google, onUseCalendarLink: { showLinkGuide = true })
        }
        Section {
            ConnectionRow(title: "Google Calendar", desc: "Import calendar events labeled as assignments.", symbol: "calendar",
                          connected: data.connections.calendar, loading: loading, busy: busy == "google_calendar",
                          onConnect: { Task { await connect("google_calendar") } },
                          onDisconnect: { Task { await disconnect("google_calendar") } })
        }
        Section {
            ConnectionRow(title: "Google Classroom", desc: "Import courses and coursework with due dates.", symbol: "book",
                          connected: data.connections.classroom, loading: loading, busy: busy == "google_classroom",
                          onConnect: { Task { await connect("google_classroom") } },
                          onDisconnect: { Task { await disconnect("google_classroom") } })
            if data.connections.classroom && !loading {
                Toggle(isOn: Binding(
                    get: { auth.user?.syncCompletionToClassroom ?? true },
                    set: { v in Task { try? await auth.update(["sync_completion_to_classroom": .bool(v)]) } }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Mark Classroom assignments done")
                        Text("When you complete a Google Classroom assignment here, turn it in on Classroom so your teacher sees it as done.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .task {
            await data.refreshConnections()
            loading = false
        }
        CalendarLinksSettings(kind: .google, onAdd: { showLinkGuide = true }, onImportFile: { showFileImport = true })
    }

    private func connect(_ id: String) async {
        guard auth.user?.isDemo != true else { return }
        busy = id
        defer { busy = nil }
        do {
            let url = try await APIClient.shared.googleConnectorURL(id)
            _ = try await WebAuthSession.shared.start(url)
            toasts.show("Connected")
        } catch is WebAuthSession.Cancelled {
        } catch {
            toasts.show("Couldn't connect", error.localizedDescription, error: true)
        }
        await data.refreshConnections()
    }

    private func disconnect(_ id: String) async {
        guard auth.user?.isDemo != true else { return }
        busy = id
        defer { busy = nil }
        try? await APIClient.shared.disconnectGoogle(id)
        await data.refreshConnections()
    }
}

struct ConnectionRow: View {
    var title: String
    var desc: String
    var symbol: String
    var connected: Bool
    var loading: Bool
    var busy: Bool
    var onConnect: () -> Void
    var onDisconnect: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: symbol).foregroundStyle(Palette.secondary).frame(width: 24)
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(title).font(.body.weight(.medium))
                        if loading { ProgressView().controlSize(.mini) }
                        else if connected { Pill(text: "Connected", color: .green, filled: true) }
                        else { Pill(text: "Not connected") }
                    }
                    Text(desc).font(.caption).foregroundStyle(.secondary)
                }
            }
            HStack {
                Spacer()
                if connected {
                    Button(action: onDisconnect) {
                        if busy { ProgressView() } else { Text("Disconnect") }
                    }
                    .glassButtonStyle()
                } else {
                    Button(action: onConnect) {
                        if busy { ProgressView() } else { Label("Connect", systemImage: "link") }
                    }
                    .glassButtonStyle(prominent: true)
                }
            }
            .disabled(busy || loading)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Blackboard

struct BlackboardSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(ToastCenter.self) private var toasts
    @State private var url = ""
    @State private var busy = false
    @State private var error = ""
    /// The school hasn't approved Study Spot in Blackboard (offer a calendar link).
    @State private var notApproved = false
    @State private var showLinkGuide = false

    var body: some View {
        Section {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "building.columns").foregroundStyle(Palette.secondary)
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Blackboard Learn").font(.body.weight(.medium))
                        if data.connections.blackboard { Pill(text: "Connected", color: .green, filled: true) } else { Pill(text: "Not connected") }
                    }
                    Text("Import courses and due-dated gradebook items from your school's Blackboard instance.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .sheet(isPresented: $showLinkGuide) { CalendarLinkGuide(kind: .blackboard) }
        }
        if data.connections.blackboard {
            Section {
                Text(auth.user?.blackboardInstanceURL ?? "Connected").font(.footnote.monospaced())
                Button(role: .destructive) { Task { await disconnect() } } label: {
                    if busy { ProgressView() } else { Text("Disconnect") }
                }
                .disabled(busy)
            }
        } else {
            Section {
                TextField("https://your-school.blackboard.com", text: $url)
                    .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button { Task { await connect() } } label: {
                    if busy { ProgressView() } else { Label("Connect", systemImage: "link") }
                }
                .disabled(busy)
            } footer: {
                connectFooter
            }
        }
        CalendarLinksSettings(kind: .blackboard, onAdd: { showLinkGuide = true })
    }

    private var connectFooter: some View {
        VStack(alignment: .leading, spacing: 6) {
            if !error.isEmpty { Text(error).foregroundStyle(.red) }
            if notApproved {
                Button { showLinkGuide = true } label: {
                    Label("Use a calendar link instead", systemImage: "link")
                }
                .font(.footnote.weight(.semibold))
                .buttonStyle(.borderless)
            }
            SchoolAccessNote(service: .blackboard, onUseCalendarLink: { showLinkGuide = true })
        }
    }

    private func connect() async {
        error = ""
        notApproved = false
        var trimmed = url.trimmingCharacters(in: .whitespaces)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        guard !trimmed.isEmpty else { error = "Enter your Blackboard Learn URL."; return }
        guard auth.user?.isDemo != true else { return }
        busy = true
        defer { busy = false }
        do {
            let res = try await APIClient.shared.invoke("blackboardAuthUrl", ["instanceUrl": .string(trimmed), "client": "ios"])
            guard let s = res["authUrl"]?.stringValue, let authURL = URL(string: s) else {
                error = "Failed to start connection"
                return
            }
            let callback = try await WebAuthSession.shared.start(authURL)
            guard let code = callback.param("code") else {
                let description = callback.param("error_description") ?? ""
                if description.range(of: "not registered|unregistered|invalid application", options: [.regularExpression, .caseInsensitive]) != nil {
                    // The school hasn't added Study Spot under REST API Integrations.
                    notApproved = true
                    error = "Your school hasn’t approved Study Spot in Blackboard yet. A Blackboard admin at your school needs to add Study Spot under REST API Integrations. Ask your school’s IT team, or add your assignments yourself until then."
                } else {
                    error = description.isEmpty ? "Blackboard did not return an authorization code." : description
                }
                return
            }
            try await APIClient.shared.invoke("blackboardCallback", ["code": .string(code), "state": .from(callback.param("state"))])
            await auth.refreshUser()
            await data.refreshConnections()
            toasts.show("Blackboard connected")
        } catch is WebAuthSession.Cancelled {
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func disconnect() async {
        busy = true
        defer { busy = false }
        do {
            try await APIClient.shared.invoke("disconnectBlackboard")
            await data.refreshConnections()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

// MARK: - Sync, leaderboard, onboarding

struct SyncSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text("Auto-refresh").font(.body.weight(.medium))
                if let last = auth.user?.lastSync {
                    Text("Last synced \(last.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary)
                } else {
                    Text("Not synced yet. The app auto-syncs every 6 hours.").font(.caption).foregroundStyle(.secondary)
                }
            }
            Button {
                Task { await data.runSync(silent: false, auth: auth) }
            } label: {
                HStack { Label("Sync now", systemImage: "arrow.triangle.2.circlepath"); Spacer(); if data.isSyncing { ProgressView() } }
            }
            .disabled(data.isSyncing || !data.connections.any)
            Button { Task { await data.refreshConnections() } } label: { Label("Refresh status", systemImage: "arrow.clockwise") }
        } footer: {
            if !data.syncMessage.isEmpty { Text(data.syncMessage) }
        }
    }
}

struct LeaderboardSetting: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts

    var body: some View {
        Section {
            Toggle(isOn: Binding(
                get: { auth.user?.leaderboardEnabled ?? false },
                set: { v in
                    guard auth.user?.isDemo != true else { return }
                    Task {
                        do { try await auth.update(["leaderboard_enabled": .bool(v)]) }
                        catch { toasts.show("Couldn't save", error.localizedDescription, error: true) }
                    }
                }
            )) {
                SettingLabel(title: "Study-streak leaderboard",
                             desc: "Display the top study streaks on the Streaks page so you can compete with other students. Off by default. Your streak is never shared unless you turn this on.",
                             symbol: "trophy")
            }
        }
    }
}

struct OnboardingRestart: View {
    @Environment(AuthStore.self) private var auth
    @Environment(Router.self) private var router
    @State private var busy = false

    var body: some View {
        Section {
            Text("Re-run the first-time setup wizard to revisit your theme, leaderboard, security, services, and grading choices.")
                .font(.subheadline).foregroundStyle(.secondary)
            Button {
                Task {
                    busy = true
                    defer { busy = false }
                    // Close Settings first; the wizard is a full-screen cover
                    // that can't appear over an open sheet.
                    router.showSettings = false
                    try? await Task.sleep(for: .milliseconds(450))
                    try? await auth.update(["onboarding_completed": false])
                }
            } label: {
                HStack { Label("Restart onboarding", systemImage: "arrow.counterclockwise"); if busy { Spacer(); ProgressView() } }
            }
            .disabled(busy || auth.user?.isDemo == true)
        }
    }
}

// MARK: - Data export

struct DataExportSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(ToastCenter.self) private var toasts
    @State private var busy = false
    @State private var fileURL: URL?
    @State private var counts: (Int, Int)?

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text("Export your data").font(.body.weight(.medium))
                Text("Create an Excel spreadsheet of all your assignments and classes.").font(.caption).foregroundStyle(.secondary)
                if let counts { Text("Last export: \(counts.0) assignments, \(counts.1) classes.").font(.caption).foregroundStyle(.secondary) }
            }
            Button { Task { await build() } } label: {
                HStack { Label("Prepare export", systemImage: "arrow.down.doc"); Spacer(); if busy { ProgressView() } }
            }
            .disabled(busy)
            if let fileURL {
                ShareLink(item: fileURL) { Label("Share or save study-spot-export.xls", systemImage: "square.and.arrow.up") }
            }
        }
    }

    private func build() async {
        busy = true
        defer { busy = false }
        do {
            let assignments: [Assignment]
            let classes: [SchoolClass]
            if auth.user?.isDemo == true {
                assignments = data.assignments; classes = data.classes
            } else {
                assignments = try await APIClient.shared.assignments(limit: 2000)
                classes = try await APIClient.shared.classes()
            }
            fileURL = try ExcelExport.export(assignments: assignments, classes: classes)
            counts = (assignments.count, classes.count)
        } catch {
            toasts.show("Export failed", error.localizedDescription, error: true)
        }
    }
}

// MARK: - Reminders

struct ReminderSettings: View {
    @Environment(AuthStore.self) private var auth
    @State private var enabled = true
    @State private var leads: [Double] = [24]
    @State private var dndEnabled = false
    @State private var dndStart = ReminderSettings.time("22:00")
    @State private var dndEnd = ReminderSettings.time("07:00")
    @State private var custom = ""
    @State private var loaded = false

    private static let presets: [Double] = [1, 6, 12, 24, 48, 72]

    static func time(_ s: String) -> Date {
        let parts = s.split(separator: ":").compactMap { Int($0) }
        return Calendar.current.date(bySettingHour: parts.first ?? 0, minute: parts.count > 1 ? parts[1] : 0, second: 0, of: Date()) ?? Date()
    }

    static func hhmm(_ d: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }

    static func label(_ h: Double) -> String {
        if h < 24 { return "\(h.clean)h before" }
        let d = h / 24
        return d == d.rounded() ? "\(Int(d))d before" : "\(h.clean)h before"
    }

    var body: some View {
        Section {
            Toggle(isOn: $enabled) {
                SettingLabel(title: "Assignment reminders", desc: "Get a notification before assignments are due.", symbol: "bell")
            }
            .onChange(of: enabled) { _, v in save(["reminders_enabled": .bool(v)]) }
            Toggle(isOn: Binding(
                get: { auth.user?.data["push_reminders"]?.boolValue != false },
                set: { v in Task { try? await auth.update(["push_reminders": .bool(v)]) } }
            )) {
                SettingLabel(title: "iPhone notifications",
                             desc: "Send reminders to this iPhone as notifications. They also appear in the in-app notification center.",
                             symbol: "iphone.radiowaves.left.and.right")
            }
            .disabled(!enabled || auth.user?.isDemo == true)
        } footer: {
            Text("Control when you get notified about upcoming assignments. If notifications are turned off for Study Spot in iOS Settings, reminders still show in the app.")
        }

        Section {
            ForEach(leads, id: \.self) { h in
                HStack {
                    Label(Self.label(h), systemImage: "clock")
                    Spacer()
                    Button { remove(h) } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Remove \(Self.label(h))")
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(Self.presets.filter { !leads.contains($0) }, id: \.self) { p in
                        Button("+ \(Self.label(p))") { add(p) }.glassButtonStyle().controlSize(.small)
                    }
                }
            }
            HStack {
                TextField("Custom hours before", text: $custom).keyboardType(.decimalPad)
                Button("Add") { add(Double(custom) ?? 0); custom = "" }
                    .disabled((Double(custom) ?? 0) <= 0)
            }
        } header: {
            Text("Remind me")
        } footer: {
            Text("Add the exact times you want a reminder before each due date.")
        }
        .disabled(!enabled)

        Section {
            Toggle(isOn: $dndEnabled) {
                SettingLabel(title: "Do not disturb", desc: "Pause reminders during a daily quiet window (your local time).", symbol: "moon")
            }
            .onChange(of: dndEnabled) { _, v in save(["dnd_enabled": .bool(v)]) }
            DatePicker("Start", selection: $dndStart, displayedComponents: .hourAndMinute)
                .onChange(of: dndStart) { _, v in save(["dnd_start": .string(Self.hhmm(v))]) }
            DatePicker("End", selection: $dndEnd, displayedComponents: .hourAndMinute)
                .onChange(of: dndEnd) { _, v in save(["dnd_end": .string(Self.hhmm(v))]) }
        }
        .disabled(!enabled)

        Section {
            Toggle(isOn: Binding(
                get: { auth.user?.data["class_live_activities"]?.boolValue != false },
                set: { v in Task { try? await auth.update(["class_live_activities": .bool(v)]) } }
            )) {
                SettingLabel(title: "Live Activity before class",
                             desc: "10 minutes before each class, show a countdown on the Lock Screen and in the Dynamic Island, with anything due that day. Add meeting times to your classes to use it.",
                             symbol: "clock.badge")
            }
            .disabled(auth.user?.isDemo == true)
        }
        .onAppear(perform: load)
    }

    private func load() {
        guard !loaded, let d = auth.user?.data else { return }
        enabled = d["reminders_enabled"]?.boolValue != false
        if let arr = d["reminder_lead_hours"]?.arrayValue {
            let vals = Array(Set(arr.compactMap(\.doubleValue).filter { $0 > 0 })).sorted()
            leads = vals
        } else if let n = d["reminder_lead_hours"]?.doubleValue, n > 0 {
            leads = [n]
        }
        dndEnabled = d["dnd_enabled"]?.boolValue == true
        dndStart = Self.time(d["dnd_start"]?.stringValue ?? "22:00")
        dndEnd = Self.time(d["dnd_end"]?.stringValue ?? "07:00")
        // Let the onChange handlers from this initial load settle before saving.
        DispatchQueue.main.async { loaded = true }
    }

    private func save(_ patch: [String: JSONValue]) {
        guard loaded, auth.user?.isDemo != true else { return }
        Task { try? await auth.update(patch) }
    }

    private func add(_ h: Double) {
        guard h > 0, !leads.contains(h) else { return }
        leads = (leads + [h]).sorted()
        save(["reminder_lead_hours": .array(leads.map { .number($0) })])
    }

    private func remove(_ h: Double) {
        leads.removeAll { $0 == h }
        save(["reminder_lead_hours": .array(leads.map { .number($0) })])
    }
}

// MARK: - Grading

struct GradingScaleEditor: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    @State private var rows: [GradeEntry] = []
    @State private var saving = false

    var body: some View {
        Section {
            ForEach(rows.indices, id: \.self) { i in
                HStack {
                    TextField("Letter", text: Binding(get: { rows[safe: i]?.letter ?? "" }, set: { if rows.indices.contains(i) { rows[i].letter = String($0.prefix(3)) } }))
                        .frame(width: 70)
                    Text("≥").foregroundStyle(.secondary)
                    TextField("0", value: Binding(get: { rows[safe: i]?.min ?? 0 }, set: { if rows.indices.contains(i) { rows[i].min = $0 } }), format: .number)
                        .keyboardType(.decimalPad)
                        .frame(width: 70)
                    Text("%").foregroundStyle(.secondary)
                }
            }
            .onDelete { rows.remove(atOffsets: $0) }
            Button { rows.append(GradeEntry(letter: "", min: 0)) } label: { Label("Add row", systemImage: "plus") }
        } header: {
            Text("Letter grades")
        } footer: {
            Text("Set the minimum percentage for each letter grade. This is used to compute letter grades on graded assignments.")
        }
        Section {
            Button { Task { await save() } } label: {
                HStack { Text("Save scale").bold(); Spacer(); if saving { ProgressView() } }
            }
            .disabled(saving)
            Button("Reset to default") { rows = Grading.defaultScale }
        }
        .onAppear { if rows.isEmpty { rows = auth.user?.gradingScale ?? Grading.defaultScale } }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        let sorted = rows.filter { !$0.letter.trimmingCharacters(in: .whitespaces).isEmpty }.sorted { $0.min > $1.min }
        do {
            if auth.user?.isDemo != true {
                try await auth.update(["grading_scale": .array(sorted.map { ["letter": .string($0.letter), "min": .number($0.min)] })])
            }
            rows = sorted
            toasts.show("Grading scale saved")
        } catch {
            toasts.show("Couldn't save", error.localizedDescription, error: true)
        }
    }
}

extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}

// MARK: - Account & danger zone

struct AccountSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    @State private var name = ""
    @State private var saving = false

    var body: some View {
        let current = auth.user?.name ?? auth.user?.fullName ?? ""
        Section {
            TextField("Your name", text: $name)
                .onChange(of: name) { _, v in if v.count > 80 { name = String(v.prefix(80)) } }
            Button {
                Task {
                    saving = true
                    defer { saving = false }
                    do {
                        try await auth.update(["name": .string(name.trimmingCharacters(in: .whitespaces))])
                        toasts.show("Name saved")
                    } catch {
                        toasts.show("Failed to save", error.localizedDescription, error: true)
                    }
                }
            } label: {
                HStack { Text("Save"); Spacer(); if saving { ProgressView() } }
            }
            .disabled(saving || name.trimmingCharacters(in: .whitespaces) == current.trimmingCharacters(in: .whitespaces))
        } header: {
            Text("Name")
        } footer: {
            Text("Used to personalize the app and its notifications.")
        }
        .onAppear { name = current }
        UsernameSettings()
        ChangePasswordSettings()
    }
}

struct UsernameSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    @State private var username = ""
    @State private var saving = false
    @State private var error = ""

    private var trimmed: String { username.trimmingCharacters(in: .whitespaces).lowercased() }

    var body: some View {
        let current = auth.user?.username ?? ""
        Section {
            TextField("Username", text: $username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.username)
                .onChange(of: username) { _, v in
                    error = ""
                    if v.count > 30 { username = String(v.prefix(30)) }
                }
            Button {
                Task { await save() }
            } label: {
                HStack { Text("Save username"); Spacer(); if saving { ProgressView() } }
            }
            .disabled(saving || trimmed.isEmpty || trimmed == current || auth.user?.isDemo == true)
        } header: {
            Text("Username")
        } footer: {
            if !error.isEmpty {
                Text(error).foregroundStyle(.red)
            } else {
                Text("You sign in with your username. 3–30 characters: lowercase letters, numbers, periods, underscores, and hyphens, starting with a letter or number.")
            }
        }
        .onAppear { if username.isEmpty { username = current } }
    }

    private func save() async {
        error = ""
        saving = true
        defer { saving = false }
        do {
            try await auth.update(["username": .string(trimmed)])
            username = auth.user?.username ?? trimmed
            toasts.show("Username saved")
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct ChangePasswordSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    @State private var current = ""
    @State private var newPassword = ""
    @State private var confirm = ""
    @State private var saving = false
    @State private var error = ""

    private var mismatch: Bool { !confirm.isEmpty && newPassword != confirm }

    var body: some View {
        Section {
            SecureField("Current password", text: $current)
                .textContentType(.password)
            SecureField("New password", text: $newPassword)
                .textContentType(.newPassword)
            SecureField("Confirm new password", text: $confirm)
                .textContentType(.newPassword)
            Button {
                Task { await save() }
            } label: {
                HStack { Text("Change password"); Spacer(); if saving { ProgressView() } }
            }
            .disabled(saving || current.isEmpty || newPassword.isEmpty || newPassword != confirm || auth.user?.isDemo == true)
        } header: {
            Text("Password")
        } footer: {
            if !error.isEmpty {
                Text(error).foregroundStyle(.red)
            } else if mismatch {
                Text("The new passwords don't match.").foregroundStyle(.red)
            }
        }
        .onChange(of: current) { _, _ in error = "" }
        .onChange(of: newPassword) { _, _ in error = "" }
    }

    private func save() async {
        error = ""
        guard newPassword == confirm else { error = "The new passwords don't match."; return }
        saving = true
        defer { saving = false }
        do {
            try await APIClient.shared.changePassword(current: current, newPassword: newPassword)
            current = ""; newPassword = ""; confirm = ""
            toasts.show("Password changed")
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct DangerZone: View {
    @Environment(AuthStore.self) private var auth
    @State private var confirmLogoutAll = false
    @State private var confirmDelete = false
    @State private var busy = false
    @State private var password = ""
    @State private var deleteError: String?

    var body: some View {
        Section {
            Button { confirmLogoutAll = true } label: {
                SettingLabel(title: "Log out of all devices", desc: "Signs out this device and any other devices currently logged into your account.", symbol: "rectangle.portrait.and.arrow.right")
            }
            .foregroundStyle(.primary)
        }
        Section {
            Button(role: .destructive) { confirmDelete = true } label: {
                SettingLabel(title: "Delete account", desc: "Permanently deletes your account and everything in it. This can't be undone.", symbol: "trash")
            }
        } footer: {
            if let deleteError { Text(deleteError).foregroundStyle(.red) }
        }
        .disabled(busy)
        .alert("Log out of all devices?", isPresented: $confirmLogoutAll) {
            Button("Cancel", role: .cancel) {}
            Button("Log out all", role: .destructive) {
                Task {
                    busy = true
                    try? await auth.update(["sessions_invalidated_at": .string(ISO8601.string(from: Date()))])
                    auth.signOut()
                }
            }
        } message: {
            Text("You'll be signed out here, and any other device signed into your account will be signed out the next time it opens the app.")
        }
        .alert("Delete account?", isPresented: $confirmDelete) {
            SecureField("Password", text: $password)
                .textContentType(.password)
            Button("Cancel", role: .cancel) { password = "" }
            Button("Delete", role: .destructive) { Task { await deleteAccount() } }
        } message: {
            Text("This permanently deletes your account, assignments, classes, study history, connections and settings. Enter your password to confirm. This can't be undone.")
        }
    }

    private func deleteAccount() async {
        guard auth.user?.isDemo != true else { return }
        busy = true
        deleteError = nil
        defer { busy = false; password = "" }
        do {
            _ = try await APIClient.shared.send("/auth/me", method: "DELETE", body: ["password": .string(password)])
            auth.signOut()
        } catch {
            deleteError = error.localizedDescription
        }
    }
}

struct ServerInfoView: View {
    @State private var showEditor = false
    var body: some View {
        Form {
            Section {
                LabeledContent("Server", value: AppConfig.serverURL)
                LabeledContent("Website", value: AppConfig.webURL)
                LabeledContent("Passkey domain", value: AppConfig.passkeyRelyingParty)
                Button("Change server…") { showEditor = true }
            } footer: {
                Text("Changing the server signs you out.")
            }
        }
        .navigationTitle("Server")
        .sheet(isPresented: $showEditor) { ServerSettingsView() }
    }
}
