import SwiftUI
import Observation

/// Admin step-up: opening or changing anyone's account needs a recent
/// two-factor confirmation. The server issues a 15-minute elevation token
/// (kept in memory by APIClient and sent as X-Admin-Elevation).
@MainActor
@Observable
final class AdminUnlock {
    static let shared = AdminUnlock()

    struct Cancelled: LocalizedError {
        var errorDescription: String? { "Unlock cancelled" }
    }

    private(set) var expiresAt: Date?
    /// Drives the unlock sheet (see `adminUnlockSheet()`).
    var showSheet = false
    @ObservationIgnored private var waiters: [CheckedContinuation<Void, Error>] = []

    var isUnlocked: Bool {
        guard let expiresAt, expiresAt > Date() else { return false }
        return APIClient.shared.adminElevation != nil
    }

    /// Shows the unlock sheet if needed; throws `Cancelled` if dismissed.
    func ensureUnlocked() async throws {
        if !isUnlocked { try await requestUnlock() }
    }

    func requestUnlock() async throws {
        try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
            waiters.append(c)
            showSheet = true
        }
    }

    /// Runs a gated call, unlocking first; if the server says the unlock
    /// expired, asks again and retries once.
    func run<T>(_ operation: () async throws -> T) async throws -> T {
        try await ensureUnlocked()
        do {
            return try await operation()
        } catch let e as APIError where e.elevationRequired {
            lock()
            try await requestUnlock()
            return try await operation()
        }
    }

    func unlocked(token: String, expiresAt: Date) {
        APIClient.shared.setAdminElevation(token)
        self.expiresAt = expiresAt
        finish(nil)
    }

    func cancel() { finish(Cancelled()) }

    /// Called when the sheet is dismissed (including by swiping it away).
    func sheetDismissed() {
        if !waiters.isEmpty { finish(Cancelled()) }
    }

    func lock() {
        APIClient.shared.setAdminElevation(nil)
        expiresAt = nil
    }

    private func finish(_ error: Error?) {
        showSheet = false
        let pending = waiters
        waiters = []
        for c in pending {
            if let error { c.resume(throwing: error) } else { c.resume() }
        }
    }
}

extension View {
    /// Hosts the admin unlock sheet. Attach once, at the root of the admin
    /// navigation stack (admin screens push rather than present sheets, so
    /// this can always present).
    func adminUnlockSheet() -> some View { modifier(AdminUnlockHost()) }
}

private struct AdminUnlockHost: ViewModifier {
    func body(content: Content) -> some View {
        let unlock = AdminUnlock.shared
        content.sheet(
            isPresented: Binding(get: { unlock.showSheet }, set: { unlock.showSheet = $0 }),
            onDismiss: { unlock.sheetDismissed() }
        ) {
            NavigationStack { AdminUnlockView() }
                .presentationDetents([.medium, .large])
        }
    }
}

struct AdminUnlockView: View {
    @State private var methods: [String] = []
    @State private var minutes = 15
    @State private var passkeyOptions: JSONValue?
    @State private var code = ""
    @State private var loading = true
    @State private var busy = false
    @State private var error = ""
    @State private var setupError = ""
    @State private var passkeys = PasskeyService()

    var body: some View {
        Form {
            Section {
                VStack(spacing: 8) {
                    Image(systemName: "lock.shield").font(.system(size: 40)).foregroundStyle(.tint)
                    Text("Confirm it's you").font(.title3.bold())
                    Text("Managing user accounts needs two-factor authentication. You'll stay unlocked for \(minutes) minutes on this device.")
                        .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }
            if loading {
                ProgressView().frame(maxWidth: .infinity)
            } else if !setupError.isEmpty {
                Section {
                    Label(setupError, systemImage: "exclamationmark.triangle").foregroundStyle(.orange)
                } footer: {
                    Text("Turn on two-factor authentication in Settings → Security, then try again.")
                }
            } else {
                methodSections
            }
        }
        .appBackground()
        .navigationTitle("Unlock admin")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { AdminUnlock.shared.cancel() }.tint(Palette.secondary)
            }
        }
        .task { await start() }
    }

    @ViewBuilder private var methodSections: some View {
        if methods.contains("totp") {
            Section {
                CodeField(code: $code)
                Button {
                    Task { await unlock(["code": .string(code)]) }
                } label: {
                    HStack { Spacer(); if busy { ProgressView() } else { Text("Unlock").bold() }; Spacer() }
                }
                .disabled(busy || code.count != 6)
            } header: {
                Text("Code from your authenticator app")
            }
        }
        if methods.contains("passkey"), passkeyOptions != nil {
            Section {
                Button {
                    Task { await usePasskey() }
                } label: {
                    Label("Use passkey", systemImage: "person.badge.key")
                }
                .disabled(busy)
            }
        }
        if !error.isEmpty {
            Section { Text(error).foregroundStyle(.red).font(.footnote) }
        }
    }

    private func start() async {
        loading = true
        defer { loading = false }
        do {
            let res = try await APIClient.shared.invoke("adminElevateStart")
            methods = res["methods"]?.arrayValue?.compactMap(\.stringValue) ?? []
            minutes = Int(res["minutes"]?.doubleValue ?? 15)
            passkeyOptions = res["passkey"]
        } catch {
            setupError = error.localizedDescription
        }
    }

    private func usePasskey() async {
        guard let options = passkeyOptions else { return }
        error = ""
        do {
            let payload = try await passkeys.assertion(options: options)
            await unlock(payload)
        } catch {
            self.error = error.localizedDescription
            // A challenge is single-use; fetch a fresh one for a retry.
            if let res = (try? await APIClient.shared.invoke("adminElevateStart")) { passkeyOptions = res["passkey"] }
        }
    }

    private func unlock(_ payload: [String: JSONValue]) async {
        error = ""
        busy = true
        defer { busy = false }
        do {
            let res = try await APIClient.shared.invoke("adminElevate", payload)
            guard let token = res["elevation"]?.stringValue, !token.isEmpty else {
                throw APIError(status: 0, message: "The server didn't confirm the unlock.")
            }
            let expires = res["expires_at"]?.stringValue.flatMap(ISO8601.date(from:))
                ?? Date().addingTimeInterval(Double(minutes) * 60)
            AdminUnlock.shared.unlocked(token: token, expiresAt: expires)
        } catch {
            self.error = error.localizedDescription
            code = ""
        }
    }
}

/// "Unlocked · N min left" with a Lock button (or Unlock when locked).
struct AdminUnlockStatus: View {
    var body: some View {
        let unlock = AdminUnlock.shared
        TimelineView(.periodic(from: .now, by: 15)) { context in
            HStack {
                if unlock.isUnlocked, let expires = unlock.expiresAt {
                    let left = max(1, Int((expires.timeIntervalSince(context.date) / 60).rounded(.up)))
                    Label("Unlocked · \(left) min left", systemImage: "lock.open").foregroundStyle(.green)
                    Spacer()
                    Button("Lock") { unlock.lock() }.buttonStyle(.borderless)
                } else {
                    Label("Locked", systemImage: "lock").foregroundStyle(.secondary)
                    Spacer()
                    Button("Unlock") { Task { try? await unlock.ensureUnlocked() } }.buttonStyle(.borderless)
                }
            }
            .font(.subheadline)
        }
    }
}
