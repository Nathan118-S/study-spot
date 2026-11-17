import SwiftUI
import Observation

// MARK: - Admin home

struct AdminView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth

    var body: some View {
        Group {
            if auth.user?.isAdmin == true {
                List {
                    Section {
                        NavigationLink { AdminPeopleView() } label: {
                            AdminRow(title: "People", desc: "Everything about each account: profile, security, settings, and data. Needs 2FA.", symbol: "person.2")
                        }
                        NavigationLink { NotificationSenderView() } label: {
                            AdminRow(title: "Send Notification", desc: "Send a custom in-app notification to a user or everyone.", symbol: "bell.badge")
                        }
                        NavigationLink { AdminSecretsView() } label: {
                            AdminRow(title: "Integration Secrets", desc: "Paste Google, Blackboard, and Apple Push credentials — write-once, encrypted.", symbol: "key")
                        }
                        NavigationLink { AdminDeployView() } label: {
                            AdminRow(title: "Deploy", desc: "Pull the latest commit from GitHub and restart the app.", symbol: "arrow.up.circle")
                        }
                    } footer: {
                        Text("Manage users and send notifications.")
                    }
                }
            } else {
                ContentUnavailableView("Admins only", systemImage: "lock")
            }
        }
        .appBackground()
        .navigationTitle("Admin")
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        .adminUnlockSheet()
    }
}

private struct AdminRow: View {
    var title: String
    var desc: String
    var symbol: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).foregroundStyle(.red).frame(width: 34, height: 34).background(Color.red.opacity(0.1), in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(desc).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - People

@MainActor
@Observable
final class AdminPeopleModel {
    var users: [AdminUser] = []
    var loading = true

    func load(toasts: ToastCenter) async {
        loading = true
        defer { loading = false }
        do {
            let res = try await APIClient.shared.invoke("adminListUsers")
            let data = try JSONEncoder().encode(res["users"] ?? .array([]))
            users = try JSONDecoder().decode([AdminUser].self, from: data)
        } catch {
            toasts.show("Failed to load users", error.localizedDescription, error: true)
        }
    }
}

struct AdminPeopleView: View {
    @Environment(ToastCenter.self) private var toasts
    @State private var model = AdminPeopleModel()
    @State private var search = ""
    @State private var selected: AdminUser?

    var body: some View {
        let filtered = model.users.filter {
            search.isEmpty || $0.username.localizedCaseInsensitiveContains(search) || $0.displayName.localizedCaseInsensitiveContains(search)
        }
        List {
            Section {
                AdminUnlockStatus()
            } footer: {
                Text("Opening anyone's account needs two-factor authentication. It stays unlocked for 15 minutes.")
            }
            .glassRows()
            Section {
                if model.loading && model.users.isEmpty {
                    ProgressView().frame(maxWidth: .infinity)
                } else if filtered.isEmpty {
                    Text("No users found.").foregroundStyle(.secondary)
                }
                ForEach(filtered) { u in
                    Button { open(u) } label: { AdminPersonRow(user: u) }
                        .foregroundStyle(.primary)
                }
            }
            .glassRows()
        }
        .appBackground()
        .searchable(text: $search)
        .navigationTitle("People")
        .refreshable { await model.load(toasts: toasts) }
        .task { await model.load(toasts: toasts) }
        .navigationDestination(item: $selected) { u in
            AdminAccountView(userId: u.id, people: model) { selected = nil }
        }
        .onChange(of: selected) { _, now in
            // Back on the list: pick up any changes made to the account.
            if now == nil { Task { await model.load(toasts: toasts) } }
        }
    }

    private func open(_ u: AdminUser) {
        Task {
            do {
                try await AdminUnlock.shared.ensureUnlocked()
                selected = u
            } catch {
                // Unlock cancelled: stay on the list.
            }
        }
    }
}

private struct AdminPersonRow: View {
    var user: AdminUser

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(user.displayName).font(.body.weight(.medium))
                Text(user.username).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if user.role != "user" {
                Pill(text: user.role, color: user.role == "admin" ? .red : (user.role == "disabled" ? .secondary : .green))
            }
            if user.twofaEnabled { Image(systemName: "lock.fill").font(.caption).foregroundStyle(.secondary) }
            Image(systemName: user.onboardingCompleted ? "checkmark.circle.fill" : "circle.dashed")
                .font(.caption).foregroundStyle(user.onboardingCompleted ? .green : .secondary)
                .accessibilityLabel(user.onboardingCompleted ? "Onboarding complete" : "Onboarding pending")
            Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }
        .contentShape(Rectangle())
    }
}

// MARK: - Send notification

struct NotificationSenderView: View {
    @Environment(ToastCenter.self) private var toasts
    @State private var users: [AdminUser] = []
    @State private var recipient = "all"
    @State private var title = ""
    @State private var content = ""
    @State private var type = "info"
    @State private var sending = false

    var body: some View {
        Form {
            Section {
                Picker("Recipient", selection: $recipient) {
                    Text("All users").tag("all")
                    ForEach(users) { Text($0.displayName).tag($0.id) }
                }
                Picker("Type", selection: $type) {
                    Text("Info").tag("info")
                    Text("Reminder").tag("reminder")
                    Text("Streak").tag("streak")
                    Text("Test").tag("test")
                }
            }
            Section("Message") {
                TextField("Notification title", text: $title)
                TextField("Message (optional)", text: $content, axis: .vertical).lineLimit(3...6)
            }
            Button {
                Task { await send() }
            } label: {
                HStack { Text("Send notification").bold(); Spacer(); if sending { ProgressView() } }
            }
            .disabled(sending || title.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .navigationTitle("Send Notification")
        .task {
            if let res = (try? await APIClient.shared.invoke("adminListUsers")),
               let data = try? JSONEncoder().encode(res["users"] ?? .array([])) {
                users = (try? JSONDecoder().decode([AdminUser].self, from: data)) ?? []
            }
        }
    }

    private func send() async {
        sending = true
        defer { sending = false }
        do {
            let res = try await APIClient.shared.invoke("adminSendNotification", [
                "userId": .string(recipient), "title": .string(title), "content": .string(content), "type": .string(type),
            ])
            let n = Int(res["sent"]?.doubleValue ?? 0)
            toasts.show("Notification sent", recipient == "all" ? "Delivered to \(n) user(s)." : "Delivered.")
            title = ""; content = ""
        } catch {
            toasts.show("Failed to send", error.localizedDescription, error: true)
        }
    }
}

// MARK: - Secrets

struct AdminSecretsView: View {
    @Environment(ToastCenter.self) private var toasts
    @State private var secrets: [SecretStatus] = []
    @State private var drafts: [String: String] = [:]
    @State private var loading = true
    @State private var saving = false
    @State private var confirmClear: SecretStatus?

    var body: some View {
        let groups = Dictionary(grouping: secrets, by: \.group)
        let order = secrets.map(\.group).reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
        Form {
            Section {
                Text("Each value is pasted once, encrypted at rest, and can't be viewed again — only cleared so a new value can be entered.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            if loading { ProgressView().frame(maxWidth: .infinity) }
            ForEach(order, id: \.self) { group in
                Section(group) {
                    ForEach(groups[group] ?? []) { s in
                        if s.isSet {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(s.label)
                                    Text(s.setAt.map { "Set \($0.formatted(date: .abbreviated, time: .shortened))" } ?? "Set")
                                        .font(.caption).foregroundStyle(.green)
                                }
                                Spacer()
                                Button("Clear", role: .destructive) { confirmClear = s }.buttonStyle(.borderless)
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(s.label).font(.caption).foregroundStyle(.secondary)
                                SecureField("Paste \(s.label.lowercased())", text: Binding(
                                    get: { drafts[s.key] ?? "" }, set: { drafts[s.key] = $0 }
                                ))
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                            }
                        }
                    }
                }
            }
            Section {
                let pending = drafts.values.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.count
                Button { Task { await save() } } label: {
                    HStack { Text(pending > 0 ? "Save \(pending) secret(s)" : "Save").bold(); Spacer(); if saving { ProgressView() } }
                }
                .disabled(saving || pending == 0)
            }
        }
        .navigationTitle("Integration Secrets")
        .task { await load() }
        .refreshable { await load() }
        .alert("Clear \(confirmClear?.label ?? "")?", isPresented: Binding(get: { confirmClear != nil }, set: { if !$0 { confirmClear = nil } })) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) { if let s = confirmClear { Task { await clear(s) } } }
        } message: {
            Text("The stored value is deleted so it can be re-entered.")
        }
    }

    private func apply(_ res: JSONValue) {
        if let data = try? JSONEncoder().encode(res["secrets"] ?? .array([])),
           let list = try? JSONDecoder().decode([SecretStatus].self, from: data) {
            secrets = list
        }
    }

    private func load() async {
        defer { loading = false }
        do { apply(try await APIClient.shared.invoke("adminListSecrets")) }
        catch { toasts.show("Failed to load secrets", error.localizedDescription, error: true) }
    }

    private func save() async {
        let values = drafts.filter { !$0.value.trimmingCharacters(in: .whitespaces).isEmpty }.mapValues { JSONValue.string($0) }
        guard !values.isEmpty else { return }
        saving = true
        defer { saving = false }
        do {
            let res = try await APIClient.shared.invoke("adminSetSecrets", ["values": .object(values)])
            apply(res)
            drafts = [:]
            let failed = (res["results"]?.objectValue ?? [:]).filter { $0.value["ok"]?.boolValue != true }
            if failed.isEmpty {
                toasts.show("Secrets saved", "They are encrypted and cannot be viewed again — clear one to replace it.")
            } else {
                toasts.show("Some secrets were not saved",
                            failed.map { "\($0.key): \($0.value["error"]?.stringValue ?? "error")" }.joined(separator: "; "), error: true)
            }
        } catch {
            toasts.show("Save failed", error.localizedDescription, error: true)
        }
    }

    private func clear(_ s: SecretStatus) async {
        do {
            apply(try await APIClient.shared.invoke("adminClearSecret", ["key": .string(s.key)]))
            toasts.show("Cleared", "\(s.key) can now be re-entered.")
        } catch {
            toasts.show("Failed to clear", error.localizedDescription, error: true)
        }
    }
}

// MARK: - Deploy

struct AdminDeployView: View {
    @Environment(ToastCenter.self) private var toasts
    @Environment(AuthStore.self) private var auth
    @State private var status: DeployStatus?
    @State private var confirm = false
    @State private var starting = false

    private var busy: Bool { status?.status == "running" || status?.status == "restarting" }

    private var label: String {
        switch status?.status {
        case "running": "Pulling & rebuilding…"
        case "restarting": "Restarting…"
        case "failed": "Failed"
        default: "Idle"
        }
    }

    var body: some View {
        Form {
            Section {
                Text("Pull the latest commit from GitHub, rebuild, and restart the app.").font(.subheadline).foregroundStyle(.secondary)
                HStack {
                    Text("Status")
                    Spacer()
                    if busy { ProgressView().padding(.trailing, 4) }
                    Pill(text: label, color: status?.status == "failed" ? .red : (busy ? .orange : .secondary))
                }
                if let s = status?.startedAt { LabeledContent("Started", value: s.formatted(date: .abbreviated, time: .standard)) }
                if let f = status?.finishedAt { LabeledContent("Finished", value: f.formatted(date: .abbreviated, time: .standard)) }
                if let e = status?.error { Text(e).font(.caption).foregroundStyle(.red) }
                Button("Deploy latest") { confirm = true }.disabled(busy || starting)
            }
            if let log = status?.log, !log.isEmpty {
                Section("Log") {
                    ScrollView(.horizontal) {
                        Text(log).font(.caption2.monospaced()).textSelection(.enabled)
                    }
                    .frame(minHeight: 200)
                }
            }
        }
        .navigationTitle("Deploy")
        .alert("Pull latest and restart now?", isPresented: $confirm) {
            Button("Cancel", role: .cancel) {}
            Button("Deploy", role: .destructive) { Task { await deploy() } }
        } message: {
            Text("The server will be briefly unavailable while it restarts.")
        }
        .task(id: busy) {
            await fetch()
            // Poll while a deploy is in progress.
            while busy && !Task.isCancelled {
                try? await Task.sleep(for: .seconds(2))
                await fetch()
                if status?.status == "restarting" {
                    try? await Task.sleep(for: .seconds(6))
                    await auth.refreshUser()
                    await fetch()
                }
            }
        }
    }

    private func fetch() async {
        if let s = (try? await APIClient.shared.invoke("adminDeployStatus", as: DeployStatus.self)) { status = s }
    }

    private func deploy() async {
        starting = true
        defer { starting = false }
        do {
            status = try await APIClient.shared.invoke("adminDeployLatest", as: DeployStatus.self)
        } catch {
            toasts.show("Failed to start deploy", error.localizedDescription, error: true)
        }
    }
}
