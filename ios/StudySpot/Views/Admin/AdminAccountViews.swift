import SwiftUI
import Observation

// Full account manager for one user (admin only). Every call here needs the
// 2FA step-up; AdminUnlock asks for it and retries when it has expired.

struct AdminGoogleConnection: Identifiable {
    var connector: String
    var scope: String
    var expiresAt: Date?
    var createdAt: Date?
    var id: String { connector }
}

struct AdminDevice: Identifiable {
    var id: String
    var token: String
    var kind: String
    var environment: String
    var bundleId: String
    var updatedAt: Date?

    var kindLabel: String { kind == "alert" ? "Notifications" : "Class Live Activities" }
    var symbol: String { kind == "alert" ? "bell.badge" : "clock.badge" }
}

@MainActor
@Observable
final class AdminAccountModel {
    let userId: String
    var user: User?
    var hasPassword = false
    var methods: [String] = []
    var stats: [String: JSONValue] = [:]
    var google: [AdminGoogleConnection] = []
    var blackboard: JSONValue = .null
    var devices: [AdminDevice] = []
    var loading = false
    var loadError: String?
    var busy = false
    /// Set after the account is deleted or merged away.
    var gone = false

    init(userId: String) { self.userId = userId }

    func stat(_ key: String) -> Int { Int(stats[key]?.doubleValue ?? 0) }

    func load() async {
        loading = true
        defer { loading = false }
        do {
            let id = userId
            let res = try await AdminUnlock.shared.run {
                try await APIClient.shared.invoke("adminGetUser", ["userId": .string(id)])
            }
            apply(res)
            loadError = nil
        } catch is AdminUnlock.Cancelled {
            loadError = "Unlock with two-factor authentication to open this account."
        } catch {
            loadError = error.localizedDescription
        }
    }

    private func apply(_ res: JSONValue) {
        if let u = res["user"] {
            user = Self.decodeUser(u)
            hasPassword = u["has_password"]?.boolValue ?? false
            methods = u["twofa_methods"]?.arrayValue?.compactMap(\.stringValue) ?? []
        }
        stats = res["stats"]?.objectValue ?? [:]
        google = (res["connections"]?["google"]?.arrayValue ?? []).map { g in
            AdminGoogleConnection(
                connector: g["connector"]?.stringValue ?? "",
                scope: g["scope"]?.stringValue ?? "",
                expiresAt: g["expires_at"]?.stringValue.flatMap(ISO8601.date(from:)),
                createdAt: g["created_at"]?.stringValue.flatMap(ISO8601.date(from:))
            )
        }
        blackboard = res["connections"]?["blackboard"] ?? .null
        devices = (res["devices"]?.arrayValue ?? []).map { d in
            AdminDevice(
                id: d["id"]?.stringValue ?? "",
                token: d["token"]?.stringValue ?? "",
                kind: d["kind"]?.stringValue ?? "",
                environment: d["environment"]?.stringValue ?? "",
                bundleId: d["bundle_id"]?.stringValue ?? "",
                updatedAt: d["updated_at"]?.stringValue.flatMap(ISO8601.date(from:))
            )
        }
    }

    static func describeTest(_ res: JSONValue) -> String {
        let devices = Int(res["devices"]?.doubleValue ?? 0)
        if devices == 0 { return "Added to their notification center. No iPhone is registered for push." }
        return res["push_delivered"]?.boolValue == true
            ? "Added to their notification center and pushed to \(devices) device(s)."
            : "Added to their notification center, but the push wasn't delivered. Check the Apple Push secrets."
    }

    static func decodeUser(_ value: JSONValue) -> User? {
        guard let data = try? JSONEncoder().encode(value) else { return nil }
        return try? JSONDecoder().decode(User.self, from: data)
    }

    /// Calls a gated admin function for this user (adds `userId`), with a
    /// toast for the outcome. Returns the response, or nil on failure.
    @discardableResult
    func perform(_ name: String, _ payload: [String: JSONValue] = [:], toasts: ToastCenter,
                 success: String?, detail: String? = nil, reload: Bool = true) async -> JSONValue? {
        busy = true
        defer { busy = false }
        var body = payload
        body["userId"] = .string(userId)
        let request = body
        do {
            let res = try await AdminUnlock.shared.run {
                try await APIClient.shared.invoke(name, request)
            }
            if let success { toasts.show(success, detail) }
            if reload { await load() }
            return res
        } catch is AdminUnlock.Cancelled {
            return nil
        } catch {
            toasts.show("Failed", error.localizedDescription, error: true)
            return nil
        }
    }

    /// Changes keys in the user's settings, updating the screen right away.
    func updateSettings(_ settings: [String: JSONValue], toasts: ToastCenter) async {
        for (k, v) in settings { user?.data[k] = v }
        let res = await perform("adminUpdateUserSettings", ["settings": .object(settings)],
                                toasts: toasts, success: nil, reload: false)
        if let u = res?["user"], let updated = Self.decodeUser(u) {
            user?.data = updated.data
        } else {
            await load()
        }
    }
}

struct AdminAccountView: View {
    @Environment(AuthStore.self) private var auth
    var userId: String
    var people: AdminPeopleModel
    /// Leaves the account (and anything pushed above it).
    var onClose: () -> Void
    @State private var model: AdminAccountModel

    init(userId: String, people: AdminPeopleModel, onClose: @escaping () -> Void) {
        self.userId = userId
        self.people = people
        self.onClose = onClose
        _model = State(initialValue: AdminAccountModel(userId: userId))
    }

    var body: some View {
        Group {
            if let user = model.user {
                AdminAccountForm(model: model, people: people, user: user, isSelf: user.id == auth.user?.id)
            } else if model.loading {
                ProgressView()
            } else {
                ContentUnavailableView {
                    Label("Couldn't open account", systemImage: "person.crop.circle.badge.exclamationmark")
                } description: {
                    Text(model.loadError ?? "")
                } actions: {
                    Button("Try again") { Task { await model.load() } }.glassButtonStyle(prominent: true)
                }
            }
        }
        .appBackground()
        .navigationTitle(model.user?.displayName ?? "Account")
        .navigationBarTitleDisplayMode(.inline)
        .task { if model.user == nil { await model.load() } }
        .onChange(of: model.gone) { _, gone in if gone { onClose() } }
    }
}

private struct AdminAccountForm: View {
    var model: AdminAccountModel
    var people: AdminPeopleModel
    var user: User
    var isSelf: Bool

    var body: some View {
        Form {
            Section { AdminUnlockStatus() }.glassRows()
            AdminProfileSection(model: model, user: user)
            AdminStatusSection(model: model, user: user, isSelf: isSelf)
            AdminTwoFactorSection(model: model, user: user)
            AdminPasswordSection(model: model, user: user)
            AdminUserSettingsSection(model: model, user: user)
            AdminConnectionsSection(model: model)
            AdminDevicesSection(model: model)
            AdminDataSection(model: model)
            AdminAccountActionsSection(model: model, people: people, user: user, isSelf: isSelf)
        }
        .refreshable { await model.load() }
        .disabled(model.busy)
    }
}

// MARK: - Profile

private struct AdminProfileSection: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var user: User
    @State private var username = ""
    @State private var name = ""
    @State private var fullName = ""

    private var patch: [String: JSONValue] {
        var p: [String: JSONValue] = [:]
        let u = username.trimmingCharacters(in: .whitespaces).lowercased()
        if !u.isEmpty, u != user.username.lowercased() { p["username"] = .string(u) }
        if name.trimmingCharacters(in: .whitespaces) != (user.name ?? "") { p["name"] = .string(name) }
        if fullName.trimmingCharacters(in: .whitespaces) != (user.fullName ?? "") { p["full_name"] = .string(fullName) }
        return p
    }

    var body: some View {
        Section {
            LabeledContent("Username") {
                TextField("username", text: $username)
                    .multilineTextAlignment(.trailing)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            LabeledContent("Display name") {
                TextField("None", text: $name).multilineTextAlignment(.trailing)
            }
            LabeledContent("Full name") {
                TextField("None", text: $fullName).multilineTextAlignment(.trailing)
            }
            Button("Save profile") {
                let changes = patch
                Task {
                    await model.perform("adminUpdateUser", changes, toasts: toasts, success: "Profile saved")
                }
            }
            .disabled(patch.isEmpty)
        } header: {
            Text("Profile")
        } footer: {
            Text("ID \(user.id)").textSelection(.enabled)
        }
        .glassRows()
        .onAppear(perform: reset)
        .onChange(of: user) { _, _ in reset() }
    }

    private func reset() {
        username = user.username
        name = user.name ?? ""
        fullName = user.fullName ?? ""
    }
}

// MARK: - Status

private struct AdminStatusSection: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var user: User
    var isSelf: Bool
    @State private var showDisable = false
    @State private var reason = ""

    var body: some View {
        Section {
            Picker("Role", selection: Binding(get: { user.role }, set: { setRole($0) })) {
                Text("User").tag("user")
                Text("Admin").tag("admin")
                Text("Demo").tag("demo")
                Text("Disabled").tag("disabled")
            }
            .disabled(isSelf)
            if user.role == "disabled" {
                LabeledContent("Reason", value: (user.disabledReason ?? "").isEmpty ? "None given" : (user.disabledReason ?? ""))
                Button("Change reason") { reason = user.disabledReason ?? ""; showDisable = true }
                    .disabled(isSelf)
            }
            LabeledContent("Verified") {
                if user.isVerified {
                    Text("Yes")
                } else {
                    Button("Mark verified") {
                        Task { await model.perform("adminVerifyUser", toasts: toasts, success: "Marked verified") }
                    }
                }
            }
        } header: {
            Text("Status")
        } footer: {
            if isSelf { Text("You can't change your own role.") }
        }
        .glassRows()
        .alert("Disable \(user.username)?", isPresented: $showDisable) {
            TextField("Reason (optional)", text: $reason)
            Button("Cancel", role: .cancel) {}
            Button("Disable", role: .destructive) {
                let why = reason
                Task {
                    await model.perform("adminUpdateUserRole", ["role": "disabled", "reason": .string(why)],
                                        toasts: toasts, success: "Account disabled", detail: "\(user.username) can no longer log in.")
                }
            }
        } message: {
            Text("They're signed out and see the reason when they try to log in.")
        }
    }

    private func setRole(_ role: String) {
        guard role != user.role else { return }
        if role == "disabled" {
            reason = ""
            showDisable = true
            return
        }
        Task {
            await model.perform("adminUpdateUserRole", ["role": .string(role)],
                                toasts: toasts, success: "Role updated", detail: "\(user.username) is now \(role).")
        }
    }
}

// MARK: - Security

private struct AdminTwoFactorSection: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var user: User
    @State private var removing: String?
    @State private var confirmResetAll = false

    var body: some View {
        Section {
            if model.methods.isEmpty {
                Text("Two-factor authentication is off.").foregroundStyle(.secondary)
            }
            ForEach(model.methods, id: \.self) { m in
                HStack {
                    Label(TwoFactorMethod(rawValue: m)?.label ?? m, systemImage: TwoFactorMethod(rawValue: m)?.symbol ?? "key")
                    Spacer()
                    Button("Remove", role: .destructive) { removing = m }.buttonStyle(.borderless)
                }
            }
            Button("Reset all 2FA", role: .destructive) { confirmResetAll = true }
                .disabled(model.methods.isEmpty)
        } header: {
            Text("Two-factor authentication")
        }
        .glassRows()
        .confirmationDialog("Remove this method?", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
                            titleVisibility: .visible, presenting: removing) { m in
            Button("Remove \(TwoFactorMethod(rawValue: m)?.label ?? m)", role: .destructive) {
                Task { await model.perform("adminRemove2faMethod", ["method": .string(m)], toasts: toasts, success: "2FA method removed") }
            }
        }
        .confirmationDialog("Reset all 2FA for \(user.username)?", isPresented: $confirmResetAll, titleVisibility: .visible) {
            Button("Reset 2FA", role: .destructive) {
                Task {
                    await model.perform("adminReset2fa", toasts: toasts, success: "2FA reset",
                                        detail: "\(user.username) can sign in without a second factor.")
                }
            }
        } message: {
            Text("Removes every second factor so they can sign in with just their password.")
        }
    }
}

private struct AdminPasswordSection: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var user: User
    @State private var confirmTemp = false
    @State private var tempPassword: String?
    @State private var showSet = false
    @State private var newPassword = ""
    @State private var confirmSignOut = false

    var body: some View {
        Section {
            LabeledContent("Password", value: model.hasPassword ? "Set" : "Not set")
            Button { confirmTemp = true } label: {
                SettingLabel(title: "Reset to a temporary password", desc: "Generates a password to give them, and signs them out everywhere.", symbol: "key")
            }
            .foregroundStyle(.primary)
            Button { newPassword = ""; showSet = true } label: {
                SettingLabel(title: "Set a password", desc: "Choose their new password yourself.", symbol: "character.cursor.ibeam")
            }
            .foregroundStyle(.primary)
            Button { confirmSignOut = true } label: {
                SettingLabel(title: "Sign out everywhere", desc: "Ends every session on every device.", symbol: "rectangle.portrait.and.arrow.right")
            }
            .foregroundStyle(.primary)
        } header: {
            Text("Password & sessions")
        }
        .glassRows()
        .confirmationDialog("Reset \(user.username)'s password?", isPresented: $confirmTemp, titleVisibility: .visible) {
            Button("Reset password", role: .destructive) { Task { await resetTemp() } }
        } message: {
            Text("A temporary password replaces their current one, and they're signed out on every device.")
        }
        .alert("Temporary password", isPresented: Binding(get: { tempPassword != nil }, set: { if !$0 { tempPassword = nil } }),
               presenting: tempPassword) { password in
            Button("Copy") { UIPasteboard.general.string = password; toasts.show("Password copied") }
            Button("Done", role: .cancel) {}
        } message: { password in
            Text("Give this to \(user.username) so they can sign in, then have them change it in Settings → Account.\n\n\(password)")
        }
        .alert("Set password", isPresented: $showSet) {
            SecureField("New password (8+ characters)", text: $newPassword)
            Button("Cancel", role: .cancel) {}
            Button("Set and sign them out") { setPassword(signOut: true) }
            Button("Set, keep sessions") { setPassword(signOut: false) }
        } message: {
            Text("Choose a new password for \(user.username).")
        }
        .confirmationDialog("Sign \(user.username) out everywhere?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Sign out everywhere", role: .destructive) {
                Task { await model.perform("adminSignOutUser", toasts: toasts, success: "Signed out everywhere", reload: false) }
            }
        }
    }

    private func resetTemp() async {
        let res = await model.perform("adminResetPassword", toasts: toasts, success: "Password reset",
                                      detail: "\(user.username) was signed out everywhere.")
        tempPassword = res?["password"]?.stringValue
    }

    private func setPassword(signOut: Bool) {
        let password = newPassword
        newPassword = ""
        Task {
            await model.perform("adminSetPassword", ["password": .string(password), "signOut": .bool(signOut)],
                                toasts: toasts, success: "Password set",
                                detail: signOut ? "\(user.username) was signed out everywhere." : nil)
        }
    }
}

// MARK: - Connections & devices

private struct AdminConnectionsSection: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    @State private var disconnecting: String?

    private static let names = [
        "google_calendar": "Google Calendar", "google_classroom": "Google Classroom", "blackboard": "Blackboard",
    ]

    var body: some View {
        Section {
            googleRow("google_calendar")
            googleRow("google_classroom")
            blackboardRow
        } header: {
            Text("Connections")
        }
        .glassRows()
        .confirmationDialog("Disconnect \(Self.names[disconnecting ?? ""] ?? "")?",
                            isPresented: Binding(get: { disconnecting != nil }, set: { if !$0 { disconnecting = nil } }),
                            titleVisibility: .visible, presenting: disconnecting) { connector in
            Button("Disconnect", role: .destructive) {
                Task {
                    await model.perform("adminDisconnectUser", ["connector": .string(connector)],
                                        toasts: toasts, success: "\(Self.names[connector] ?? connector) disconnected")
                }
            }
        } message: { _ in
            Text("Their synced data stays. They can connect again from Settings.")
        }
    }

    @ViewBuilder
    private func googleRow(_ connector: String) -> some View {
        let connection = model.google.first { $0.connector == connector }
        connectionRow(title: Self.names[connector] ?? connector, detail: Self.connectedText(connection), connector: connector)
    }

    private static func connectedText(_ connection: AdminGoogleConnection?) -> String? {
        guard let connection else { return nil }
        guard let created = connection.createdAt else { return "Connected" }
        return "Connected \(created.formatted(date: .abbreviated, time: .omitted))"
    }

    private var blackboardRow: some View {
        let connected = model.blackboard["connected"]?.boolValue == true
        let domain = model.blackboard["domain"]?.stringValue
        return connectionRow(title: "Blackboard", detail: connected ? (domain ?? "Connected") : nil, connector: "blackboard")
    }

    private func connectionRow(title: String, detail: String?, connector: String) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(detail ?? "Not connected").font(.caption).foregroundStyle(detail == nil ? Color.secondary : Color.green)
            }
            Spacer()
            if detail != nil {
                Button("Disconnect", role: .destructive) { disconnecting = connector }.buttonStyle(.borderless)
            }
        }
    }
}

private struct AdminDevicesSection: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel

    var body: some View {
        Section {
            if model.devices.isEmpty {
                Text("No devices registered for push.").foregroundStyle(.secondary)
            }
            ForEach(model.devices) { device in
                AdminDeviceRow(device: device)
                    .swipeActions {
                        Button("Remove", role: .destructive) {
                            Task {
                                await model.perform("adminRemoveDevice", ["deviceId": .string(device.id)],
                                                    toasts: toasts, success: "Device removed")
                            }
                        }
                    }
            }
        } header: {
            Text("Devices")
        } footer: {
            if !model.devices.isEmpty { Text("Swipe a device to stop sending it pushes.") }
        }
        .glassRows()
    }
}

private struct AdminDeviceRow: View {
    var device: AdminDevice
    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(device.kindLabel)
                Text("\(device.token) · \(device.environment)").font(.caption.monospaced()).foregroundStyle(.secondary)
                if let updated = device.updatedAt {
                    Text("\(device.bundleId) · updated \(updated.formatted(date: .abbreviated, time: .shortened))")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
        } icon: {
            Image(systemName: device.symbol)
        }
    }
}

// MARK: - Actions

private struct AdminAccountActionsSection: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var people: AdminPeopleModel
    var user: User
    var isSelf: Bool
    @State private var confirmDelete = false

    var body: some View {
        Section {
            actionButton("Send test notification", "To their notification center and their iPhones.", "bell") {
                let res = await model.perform("adminSendTestNotification", toasts: toasts, success: nil, reload: false)
                if let res { toasts.show("Test notification sent", AdminAccountModel.describeTest(res)) }
            }
            actionButton("Restore streak", "Mark overdue assignments complete to rebuild their streak.", "flame") {
                let res = await model.perform("adminRestoreStreak", toasts: toasts, success: nil)
                if let n = res?["restored"]?.doubleValue {
                    toasts.show("Streak restored", "\(Int(n)) overdue assignment(s) marked complete.")
                }
            }
            actionButton("Reset onboarding", "Run the setup wizard again on their next visit.", "arrow.counterclockwise") {
                _ = await model.perform("adminResetOnboarding", toasts: toasts, success: "Onboarding reset")
            }
            .disabled(!user.onboardingCompleted)
            if !isSelf {
                NavigationLink {
                    AdminMergeView(model: model, people: people, user: user)
                } label: {
                    SettingLabel(title: "Merge into another account", desc: "Move their assignments and classes, then delete this account.", symbol: "arrow.triangle.merge")
                }
                Button(role: .destructive) { confirmDelete = true } label: {
                    SettingLabel(title: "Delete account", desc: "Permanently deletes the account and all of its data.", symbol: "trash")
                }
                .foregroundStyle(.red)
            }
        } header: {
            Text("Actions")
        }
        .glassRows()
        .confirmationDialog("Delete \(user.username)?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete account", role: .destructive) {
                Task {
                    if (await model.perform("adminDeleteUser", toasts: toasts, success: "User deleted",
                                            detail: user.username, reload: false)) != nil {
                        model.gone = true
                    }
                }
            }
        } message: {
            Text("This permanently deletes the account and all of its data. This can't be undone.")
        }
    }

    private func actionButton(_ title: String, _ desc: String, _ symbol: String,
                              _ run: @escaping () async -> Void) -> some View {
        Button { Task { await run() } } label: { SettingLabel(title: title, desc: desc, symbol: symbol) }
            .foregroundStyle(.primary)
    }
}

struct AdminMergeView: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var people: AdminPeopleModel
    var user: User
    @State private var targetId = ""
    @State private var confirm = false

    var body: some View {
        Form {
            Section {
                Text("Move \(user.username)'s assignments and classes into another account, then delete \(user.username).")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            .glassRows()
            Section("Keep which account?") {
                Picker("Target account", selection: $targetId) {
                    Text("Choose…").tag("")
                    ForEach(people.users.filter { $0.id != user.id }) { u in
                        Text(u.fullName.map { "\($0) — \(u.username)" } ?? u.username).tag(u.id)
                    }
                }
            }
            .glassRows()
            Button("Merge accounts", role: .destructive) { confirm = true }
                .disabled(targetId.isEmpty || model.busy)
                .glassRows()
        }
        .appBackground()
        .navigationTitle("Merge account")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Merge accounts?", isPresented: $confirm, titleVisibility: .visible) {
            Button("Merge and delete \(user.username)", role: .destructive) { Task { await merge() } }
        } message: {
            Text("\(user.username) will be deleted after its data is moved. This can't be undone.")
        }
    }

    private func merge() async {
        // The merge call names both accounts, so it doesn't go through
        // model.perform (which adds userId).
        model.busy = true
        defer { model.busy = false }
        let payload: [String: JSONValue] = ["sourceId": .string(user.id), "targetId": .string(targetId)]
        do {
            let res = try await AdminUnlock.shared.run {
                try await APIClient.shared.invoke("adminMergeAccounts", payload)
            }
            let a = Int(res["movedAssignments"]?.doubleValue ?? 0)
            let c = Int(res["movedClasses"]?.doubleValue ?? 0)
            toasts.show("Accounts merged", "Moved \(a) assignment(s) and \(c) class(es).")
            model.gone = true
        } catch is AdminUnlock.Cancelled {
        } catch {
            toasts.show("Merge failed", error.localizedDescription, error: true)
        }
    }
}
