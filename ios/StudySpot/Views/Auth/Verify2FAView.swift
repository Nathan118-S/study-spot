import SwiftUI

/// Second-factor gate shown after sign-in when 2FA is enabled.
struct Verify2FAView: View {
    @Environment(AuthStore.self) private var auth
    @State private var selected: TwoFactorMethod = .totp
    @State private var code = ""
    @State private var error = ""
    @State private var loading = false
    @State private var emailSent = false
    @State private var passkeys = PasskeyService()

    private var methods: [TwoFactorMethod] { auth.user?.enabledTwoFactorMethods ?? [] }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(spacing: 8) {
                        Image(systemName: "lock.shield").font(.system(size: 44)).foregroundStyle(.tint)
                        Text("Verify it's you").font(.title2.bold())
                        Text(auth.user?.email ?? "").font(.subheadline).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                }

                if methods.count > 1 {
                    Picker("Method", selection: $selected) {
                        ForEach(methods) { Label($0.label, systemImage: $0.symbol).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                }

                switch selected {
                case .totp:
                    Section {
                        CodeField(code: $code)
                    } header: {
                        Text("Enter the 6-digit code from your authenticator app")
                    } footer: { errorText }
                    submitButton("Verify") { try await APIClient.shared.invoke("verify2fa", ["code": .string(code)]) }
                        .disabled(code.count != 6)
                case .email:
                    Section {
                        CodeField(code: $code)
                        Button(emailSent ? "Resend code" : "Send code") { Task { await sendEmail() } }
                    } header: {
                        Text("Enter the code we emailed you")
                    } footer: { errorText }
                    submitButton("Verify") { try await APIClient.shared.invoke("email2faVerify", ["code": .string(code)]) }
                        .disabled(code.count != 6)
                case .passkey:
                    Section {
                        Text("Use Face ID, Touch ID, or your device passcode to confirm.")
                    } footer: { errorText }
                    submitButton("Use passkey") { try await passkeys.verify() }
                }

                Section {
                    Button("Sign out", role: .destructive) { auth.signOut() }
                }
            }
            .navigationTitle("Two-factor authentication")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear {
            let pref = auth.user?.twofaMethod.flatMap(TwoFactorMethod.init(rawValue:))
            selected = pref.flatMap { methods.contains($0) ? $0 : nil } ?? methods.first ?? .totp
        }
        .onChange(of: selected) { _, m in
            code = ""; error = ""
            if m == .email && !emailSent { Task { await sendEmail() } }
        }
        .task { if selected == .email && !emailSent { await sendEmail() } }
    }

    @ViewBuilder private var errorText: some View {
        if !error.isEmpty { Text(error).foregroundStyle(.red) }
    }

    private func submitButton(_ title: String, action: @escaping () async throws -> JSONValue) -> some View {
        Button {
            Task {
                error = ""
                loading = true
                defer { loading = false }
                do {
                    let response = try await action()
                    await auth.completeTwoFactor(response)
                } catch {
                    self.error = error.localizedDescription
                }
            }
        } label: {
            HStack { Spacer(); if loading { ProgressView() } else { Text(title).bold() }; Spacer() }
        }
        .disabled(loading)
    }

    private func sendEmail() async {
        do {
            try await APIClient.shared.invoke("email2faSend")
            emailSent = true
        } catch {
            self.error = error.localizedDescription
        }
    }
}
