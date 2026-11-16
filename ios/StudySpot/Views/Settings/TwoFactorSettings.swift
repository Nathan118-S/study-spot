import SwiftUI
import CoreImage.CIFilterBuiltins

struct TwoFactorSettings: View {
    @Environment(AuthStore.self) private var auth

    enum Sheet: Identifiable {
        case chooser, setup(TwoFactorMethod), remove(TwoFactorMethod)
        var id: String {
            switch self {
            case .chooser: "chooser"
            case .setup(let m): "setup-\(m.rawValue)"
            case .remove(let m): "remove-\(m.rawValue)"
            }
        }
    }

    @State private var sheet: Sheet?

    var body: some View {
        let methods = auth.user?.enabledTwoFactorMethods ?? []
        let available = TwoFactorMethod.allCases.filter { !methods.contains($0) }
        Section {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "checkmark.shield").foregroundStyle(.tint).font(.title3)
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Two-factor authentication").font(.body.weight(.medium))
                        if methods.isEmpty { Pill(text: "Disabled") }
                        else { Pill(text: "Enabled" + (methods.count > 1 ? " · \(methods.count) methods" : ""), color: .green, filled: true) }
                    }
                    Text("Add a second step at sign-in using an authenticator app, email code, or a passkey.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            if methods.isEmpty {
                Button { sheet = .chooser } label: { Label("Enable", systemImage: "key") }
                    .disabled(auth.user?.isDemo == true)
            }
        }
        // Attached to one section (not a Group) so only one sheet is installed.
        .sheet(item: $sheet) { s in
            NavigationStack {
                switch s {
                case .chooser:
                    MethodChooser(enabled: methods) { m in
                        sheet = nil
                        Task { try? await Task.sleep(for: .milliseconds(400)); sheet = .setup(m) }
                    }
                case .setup(.totp): TotpSetupView { done() }
                case .setup(.email): EmailSetupView { done() }
                case .setup(.passkey): PasskeySetupView { done() }
                case .remove(let m): RemoveMethodView(method: m) { done() }
                }
            }
            .presentationDetents([.medium, .large])
        }
        if !methods.isEmpty {
            Section("Methods") {
                ForEach(methods) { m in
                    HStack {
                        Label(m.label, systemImage: m.symbol)
                        Spacer()
                        Button("Remove", role: .destructive) { sheet = .remove(m) }.buttonStyle(.borderless)
                    }
                }
                if !available.isEmpty {
                    Button { sheet = .chooser } label: { Label("Add another method", systemImage: "plus") }
                }
            }
        }
    }

    private func done() {
        sheet = nil
        Task { await auth.refreshUser() }
    }
}

struct MethodChooser: View {
    @Environment(\.dismiss) private var dismiss
    var enabled: [TwoFactorMethod]
    var onPick: (TwoFactorMethod) -> Void

    var body: some View {
        let options = TwoFactorMethod.allCases.filter { !enabled.contains($0) }
        List {
            Section {
                if options.isEmpty { Text("All methods are already enabled.").foregroundStyle(.secondary) }
                ForEach(options) { o in
                    Button { onPick(o) } label: {
                        Label {
                            VStack(alignment: .leading) {
                                Text(o.label)
                                Text(o.detail).font(.caption).foregroundStyle(.secondary)
                            }
                        } icon: { Image(systemName: o.symbol) }
                    }
                    .foregroundStyle(.primary)
                }
            } footer: {
                Text(enabled.isEmpty ? "Pick how you want to verify at sign-in. You can add more later." : "Add a backup way to verify at sign-in.")
            }
        }
        .navigationTitle(enabled.isEmpty ? "Choose a second factor" : "Add another method")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
    }
}

struct TotpSetupView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    var onDone: () -> Void

    @State private var secret = ""
    @State private var otpauth = ""
    @State private var code = ""
    @State private var busy = false
    @State private var error = ""

    var body: some View {
        Form {
            Section {
                if secret.isEmpty {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    if let img = qrImage(otpauth) {
                        Image(uiImage: img).interpolation(.none).resizable().scaledToFit()
                            .frame(width: 180, height: 180).frame(maxWidth: .infinity)
                    }
                    if let url = URL(string: otpauth) {
                        Link(destination: url) { Label("Open in authenticator app", systemImage: "arrow.up.forward.app") }
                    }
                    HStack {
                        Text(secret).font(.footnote.monospaced()).textSelection(.enabled)
                        Spacer()
                        Button { UIPasteboard.general.string = secret; toasts.show("Key copied") } label: { Image(systemName: "doc.on.doc") }
                            .accessibilityLabel("Copy key")
                    }
                }
            } header: {
                Text("Account: Study Spot")
            } footer: {
                Text("Scan the code or add the key to your authenticator app, then enter the 6-digit code it generates.")
            }
            Section {
                CodeField(code: $code)
            } footer: {
                if !error.isEmpty { Text(error).foregroundStyle(.red) }
            }
        }
        .navigationTitle("Authenticator app")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                Button("Enable") { Task { await confirm() } }.disabled(busy || code.count != 6)
            }
        }
        .task {
            do {
                let res = try await APIClient.shared.invoke("setup2fa")
                secret = res["secret"]?.stringValue ?? ""
                otpauth = res["otpauth_url"]?.stringValue ?? ""
            } catch {
                toasts.show("Failed to start setup", error.localizedDescription, error: true)
                dismiss()
            }
        }
    }

    private func confirm() async {
        busy = true
        defer { busy = false }
        do {
            try await APIClient.shared.invoke("enable2fa", ["code": .string(code), "secret": .string(secret)])
            toasts.show("Authenticator app enabled")
            onDone()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func qrImage(_ string: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)),
              let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

struct EmailSetupView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    var onDone: () -> Void

    @State private var code = ""
    @State private var busy = false
    @State private var sent = false
    @State private var error = ""

    var body: some View {
        Form {
            Section {
                CodeField(code: $code)
                Button(sent ? "Resend code" : "Send code") { Task { await send() } }
            } header: {
                Text("We sent a 6-digit code to \(auth.user?.email ?? "your email"). Enter it below to enable.")
            } footer: {
                if !error.isEmpty { Text(error).foregroundStyle(.red) }
            }
        }
        .navigationTitle("Email verification")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                Button("Enable") { Task { await confirm() } }.disabled(busy || code.count != 6)
            }
        }
        .task { await send() }
    }

    private func send() async {
        error = ""
        do { try await APIClient.shared.invoke("email2faSend"); sent = true }
        catch { self.error = error.localizedDescription }
    }

    private func confirm() async {
        busy = true
        defer { busy = false }
        do {
            try await APIClient.shared.invoke("email2faVerify", ["code": .string(code), "enable": true])
            toasts.show("Email verification enabled")
            onDone()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct PasskeySetupView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    var onDone: () -> Void
    @State private var busy = false
    @State private var error = ""
    @State private var service = PasskeyService()

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.badge.key.fill").font(.system(size: 48)).foregroundStyle(.tint)
            Text("Use Face ID, Touch ID, or your device passcode to create a passkey for sign-in.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary)
            if busy { ProgressView("Follow your device prompts…") }
            if !error.isEmpty { Text(error).foregroundStyle(.red).font(.footnote).multilineTextAlignment(.center) }
            Button { Task { await register() } } label: { Text(error.isEmpty ? "Create passkey" : "Retry").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).disabled(busy)
        }
        .padding()
        .navigationTitle("Set up a passkey")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
    }

    private func register() async {
        error = ""
        busy = true
        defer { busy = false }
        do {
            try await service.register()
            toasts.show("Passkey enabled")
            onDone()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct RemoveMethodView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    var method: TwoFactorMethod
    var onDone: () -> Void

    @State private var code = ""
    @State private var busy = false
    @State private var sent = false
    @State private var error = ""
    @State private var passkeys = PasskeyService()

    var body: some View {
        Form {
            Section {
                switch method {
                case .passkey:
                    Text("Verify with your passkey to confirm removal.")
                case .email:
                    CodeField(code: $code)
                    Button(sent ? "Resend code" : "Send code") { Task { await sendEmail() } }
                case .totp:
                    CodeField(code: $code)
                }
            } header: {
                switch method {
                case .email: Text("Enter the code sent to your email to confirm removal.")
                case .totp: Text("Enter a current 6-digit code from your authenticator app.")
                case .passkey: EmptyView()
                }
            } footer: {
                if !error.isEmpty { Text(error).foregroundStyle(.red) }
            }
            Section {
                Button(role: .destructive) { Task { await confirm() } } label: {
                    HStack { Text("Remove"); Spacer(); if busy { ProgressView() } }
                }
                .disabled(busy || (method != .passkey && code.count != 6))
            }
        }
        .navigationTitle("Remove \(method.label)?")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        .task { if method == .email { await sendEmail() } }
    }

    private func sendEmail() async {
        do { try await APIClient.shared.invoke("email2faSend"); sent = true }
        catch { self.error = error.localizedDescription }
    }

    private func confirm() async {
        error = ""
        busy = true
        defer { busy = false }
        do {
            switch method {
            case .totp: try await APIClient.shared.invoke("verify2fa", ["code": .string(code)])
            case .email: try await APIClient.shared.invoke("email2faVerify", ["code": .string(code)])
            case .passkey: try await passkeys.verify()
            }
            try await APIClient.shared.invoke("remove2faMethod", ["method": .string(method.rawValue)])
            toasts.show("\(method.label) removed")
            onDone()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
