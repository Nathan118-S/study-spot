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
                            AdminRow(title: "People", desc: "Manage roles, passwords, streaks, 2FA, and accounts.", symbol: "person.2")
                        }
                        NavigationLink { NotificationSenderView() } label: {
                            AdminRow(title: "Send Notification", desc: "Send a custom in-app notification to a user or everyone.", symbol: "bell.badge")
                        }
                        NavigationLink { AdminSecretsView() } label: {
                            AdminRow(title: "Integration Secrets", desc: "Paste SMTP, Google, and Blackboard credentials — write-once, encrypted.", symbol: "key")
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
        .navigationTitle("Admin")
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
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
    var busy: String?

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

    /// Runs an admin action for one user, with a toast for the outcome.
    func run(_ u: AdminUser, toasts: ToastCenter, success: String, detail: String? = nil, reload: Bool = true,
             _ action: () async throws -> JSONValue) async {
        busy = u.id
        defer { busy = nil }
        do {
            let res = try await action()
            toasts.show(success, detail ?? Self.describe(res))
            if reload { await load(toasts: toasts) }
        } catch {
            toasts.show("Failed", error.localizedDescription, error: true)
        }
    }

    private static func describe(_ res: JSONValue) -> String? {
        if let n = res["restored"]?.doubleValue { return "\(Int(n)) overdue assignment(s) marked complete." }
        if let a = res["movedAssignments"]?.doubleValue, let c = res["movedClasses"]?.doubleValue {
            return "Moved \(Int(a)) assignment(s) and \(Int(c)) class(es)."
        }
        return nil
    }
}

struct AdminPeopleView: View {
    @Environment(ToastCenter.self) private var toasts
    @State private var model = AdminPeopleModel()
    @State private var search = ""
    @State private var selected: AdminUser?

    var body: some View {
        let filtered = model.users.filter {
            search.isEmpty || $0.email.localizedCaseInsensitiveContains(search) || $0.displayName.localizedCaseInsensitiveContains(search)
        }
        List {
            if model.loading && model.users.isEmpty {
                ProgressView().frame(maxWidth: .infinity)
            } else if filtered.isEmpty {
                Text("No users found.").foregroundStyle(.secondary)
            }
            ForEach(filtered) { u in
                Button { selected = u } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(u.displayName).font(.body.weight(.medium))
                            Text(u.email).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if u.role != "user" {
                            Pill(text: u.role, color: u.role == "admin" ? .red : (u.role == "disabled" ? .secondary : .green))
                        }
                        if u.twofaEnabled { Image(systemName: "lock.fill").font(.caption).foregroundStyle(.secondary) }
                        if !u.isVerified { Image(systemName: "envelope.badge").font(.caption).foregroundStyle(.orange) }
                        Image(systemName: u.onboardingCompleted ? "checkmark.circle.fill" : "circle.dashed")
                            .font(.caption).foregroundStyle(u.onboardingCompleted ? .green : .secondary)
                            .accessibilityLabel(u.onboardingCompleted ? "Onboarding complete" : "Onboarding pending")
                    }
                }
                .foregroundStyle(.primary)
            }
        }
        .searchable(text: $search)
        .navigationTitle("People")
        .refreshable { await model.load(toasts: toasts) }
        .task { await model.load(toasts: toasts) }
        .sheet(item: $selected) { u in
            NavigationStack { UserManageView(userId: u.id, model: model) }
        }
    }
}

struct UserManageView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    var userId: String
    var model: AdminPeopleModel

    @State private var name = ""
    @State private var showDisable = false
    @State private var disableReason = ""
    @State private var confirmReset2fa = false
    @State private var confirmDelete = false
    @State private var showMerge = false

    private var u: AdminUser? { model.users.first { $0.id == userId } }
    private let api = APIClient.shared

    var body: some View {
        Group {
            if let u { content(u) } else { ContentUnavailableView("User not found", systemImage: "person.slash") }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        .onAppear { name = (u?.name ?? u?.fullName ?? "") }
    }

    private func content(_ u: AdminUser) -> some View {
        let isSelf = u.id == auth.user?.id
        let busy = model.busy == u.id
        let savedName = (u.name ?? u.fullName ?? "").trimmingCharacters(in: .whitespaces)
        return Form {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(u.displayName).font(.headline)
                    Text(u.email).font(.subheadline).foregroundStyle(.secondary)
                    HStack {
                        Pill(text: u.role)
                        Pill(text: u.isVerified ? "Verified" : "Unverified", color: u.isVerified ? .green : .orange)
                        if u.twofaEnabled { Pill(text: "2FA: \(u.twofaMethod ?? "on")") }
                    }
                    if let created = u.createdDate {
                        Text("Joined \(created.formatted(date: .abbreviated, time: .omitted))").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                TextField("Display name", text: $name)
                Button("Save name") {
                    Task {
                        await model.run(u, toasts: toasts, success: "Name updated", detail: u.email) {
                            try await api.invoke("adminUpdateUser", ["userId": .string(u.id), "name": .string(name)])
                        }
                    }
                }
                .disabled(busy || name.trimmingCharacters(in: .whitespaces) == savedName)
            } header: { Text("Display name") } footer: { Text("Shown in emails and across the app.") }

            Section("Actions") {
                action("Reset password", "Send a password-reset link to this user's email.", "key") {
                    await model.run(u, toasts: toasts, success: "Reset email sent", detail: u.email, reload: false) {
                        try await api.forgotPassword(email: u.email); return .null
                    }
                }
                action("Restore streak", "Mark overdue assignments complete to rebuild their streak.", "flame") {
                    await model.run(u, toasts: toasts, success: "Streak restored", reload: false) {
                        try await api.invoke("adminRestoreStreak", ["userId": .string(u.id)])
                    }
                }
                if !u.isVerified {
                    action("Resend verification", "This user hasn't verified their email. Send the code again.", "envelope") {
                        await model.run(u, toasts: toasts, success: "Verification email sent", detail: u.email, reload: false) {
                            try await api.invoke("adminVerifyUser", ["userId": .string(u.id)])
                        }
                    }
                }
                action("Test in-app notification", "Send a test notification to this user's notification center.", "bell") {
                    await model.run(u, toasts: toasts, success: "Test notification sent", detail: "Notification sent to \(u.email).", reload: false) {
                        try await api.invoke("adminSendTestNotification", ["userId": .string(u.id), "type": "push"])
                    }
                }
                action("Test email", "Send a test email to confirm delivery is working.", "envelope.open") {
                    await model.run(u, toasts: toasts, success: "Test email sent", detail: "Email sent to \(u.email).", reload: false) {
                        try await api.invoke("adminSendTestNotification", ["userId": .string(u.id), "type": "email"])
                    }
                }
                if !isSelf {
                    Button { showMerge = true } label: {
                        SettingLabel(title: "Merge accounts", desc: "Move this user's assignments and classes into another account.", symbol: "arrow.triangle.merge")
                    }
                    .foregroundStyle(.primary)
                }
                action("Reset onboarding", u.onboardingCompleted ? "Require the setup wizard to run again on next visit." : "Onboarding has not been completed yet.", "arrow.counterclockwise") {
                    await model.run(u, toasts: toasts, success: "Onboarding reset", detail: "\(u.email) will be asked to set up again.") {
                        try await api.invoke("adminResetOnboarding", ["userId": .string(u.id)])
                    }
                }
                .disabled(!u.onboardingCompleted)
                Button { confirmReset2fa = true } label: {
                    SettingLabel(title: "Reset 2FA", desc: "Clear their second-factor setup so they can sign in without a code.", symbol: "lock.open")
                }
                .foregroundStyle(.primary)
                .disabled(!u.twofaEnabled)
            }
            .disabled(busy)

            if !isSelf {
                Section("Danger Zone") {
                    Picker("Role", selection: Binding(
                        get: { u.role == "disabled" ? "user" : u.role },
                        set: { role in
                            Task {
                                await model.run(u, toasts: toasts, success: "Role updated", detail: "\(u.email) is now \(role)") {
                                    try await api.invoke("adminUpdateUserRole", ["userId": .string(u.id), "role": .string(role)])
                                }
                            }
                        }
                    )) {
                        Text("User").tag("user")
                        Text("Admin").tag("admin")
                        Text("Demo").tag("demo")
                    }
                    .disabled(u.role == "disabled")

                    if u.role == "disabled" {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Account disabled").foregroundStyle(.red)
                            if let r = u.disabledReason, !r.isEmpty { Text(r).font(.caption).foregroundStyle(.secondary) }
                        }
                        Button("Re-enable account") {
                            Task {
                                await model.run(u, toasts: toasts, success: "Account re-enabled", detail: "\(u.email) can log in again.") {
                                    try await api.invoke("adminUpdateUserRole", ["userId": .string(u.id), "role": "user"])
                                }
                            }
                        }
                    } else {
                        Button("Disable account", role: .destructive) { disableReason = ""; showDisable = true }
                    }
                    Button("Delete user", role: .destructive) { confirmDelete = true }
                }
                .disabled(busy)
            }
        }
        .alert("Reset 2FA for \(u.email)?", isPresented: $confirmReset2fa) {
            Button("Cancel", role: .cancel) {}
            Button("Reset", role: .destructive) {
                Task {
                    await model.run(u, toasts: toasts, success: "2FA reset", detail: "\(u.email) can sign in without a code.") {
                        try await api.invoke("adminReset2fa", ["userId": .string(u.id)])
                    }
                }
            }
        }
        .alert("Disable \(u.email)?", isPresented: $showDisable) {
            TextField("Reason (optional)", text: $disableReason)
            Button("Cancel", role: .cancel) {}
            Button("Disable", role: .destructive) {
                Task {
                    await model.run(u, toasts: toasts, success: "Account disabled", detail: "\(u.email) can no longer log in.") {
                        try await api.invoke("adminUpdateUserRole", ["userId": .string(u.id), "role": "disabled", "reason": .string(disableReason)])
                    }
                }
            }
        } message: {
            Text("They'll see the reason when they try to log in.")
        }
        .alert("Delete \(u.email)?", isPresented: $confirmDelete) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                Task {
                    await model.run(u, toasts: toasts, success: "User deleted", detail: u.email) {
                        try await api.invoke("adminDeleteUser", ["userId": .string(u.id)])
                    }
                    dismiss()
                }
            }
        } message: {
            Text("This permanently deletes the account and all of its data.")
        }
        .sheet(isPresented: $showMerge) {
            NavigationStack { MergeView(source: u, model: model) { dismiss() } }
        }
    }

    private func action(_ title: String, _ desc: String, _ symbol: String, _ run: @escaping () async -> Void) -> some View {
        Button { Task { await run() } } label: { SettingLabel(title: title, desc: desc, symbol: symbol) }
            .foregroundStyle(.primary)
    }
}

struct MergeView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    var source: AdminUser
    var model: AdminPeopleModel
    var onMerged: () -> Void
    @State private var targetId = ""
    @State private var confirm = false

    var body: some View {
        Form {
            Section {
                Text("Move \(source.email)'s assignments and classes into another account, then delete \(source.email).")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            Section("Keep which account?") {
                Picker("Target account", selection: $targetId) {
                    Text("Choose…").tag("")
                    ForEach(model.users.filter { $0.id != source.id }) { u in
                        Text(u.fullName.map { "\($0) — \(u.email)" } ?? u.email).tag(u.id)
                    }
                }
            }
            Button("Merge accounts", role: .destructive) { confirm = true }.disabled(targetId.isEmpty)
        }
        .navigationTitle("Merge account")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        .alert("Merge accounts?", isPresented: $confirm) {
            Button("Cancel", role: .cancel) {}
            Button("Merge", role: .destructive) {
                Task {
                    await model.run(source, toasts: toasts, success: "Accounts merged") {
                        try await APIClient.shared.invoke("adminMergeAccounts", ["sourceId": .string(source.id), "targetId": .string(targetId)])
                    }
                    dismiss()
                    onMerged()
                }
            }
        } message: {
            Text("\(source.email) will be deleted after its data is moved. This can't be undone.")
        }
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
    @State private var testTo = ""
    @State private var sendingTest = false

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
            Section {
                TextField("you@example.com", text: $testTo).keyboardType(.emailAddress).textInputAutocapitalization(.never)
                Button { Task { await sendTest() } } label: {
                    HStack { Text("Send test email"); Spacer(); if sendingTest { ProgressView() } }
                }
                .disabled(sendingTest || testTo.trimmingCharacters(in: .whitespaces).isEmpty)
            } header: {
                Text("Test email delivery")
            } footer: {
                Text("Confirms sign-up codes, password resets, and reminders can be delivered.")
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

    private func sendTest() async {
        sendingTest = true
        defer { sendingTest = false }
        let to = testTo.trimmingCharacters(in: .whitespaces)
        do {
            try await APIClient.shared.invoke("adminSendTestEmail", ["to": .string(to)])
            toasts.show("Test email sent", "Check \(to) for delivery.")
        } catch {
            toasts.show("Test email failed", error.localizedDescription, error: true)
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
