import SwiftUI

// MARK: - Landing

struct AuthFlowView: View {
    var body: some View {
        NavigationStack { LandingView() }
    }
}

struct LandingView: View {
    @State private var showServer = false

    private let features: [(String, String, String)] = [
        ("calendar", "Google Calendar Sync", "Assignments auto-import from your Google Calendar events using smart keyword matching."),
        ("book", "Google Classroom", "Pull coursework, due dates, and attachments straight from Classroom — and mark work done on both sides."),
        ("building.columns", "Blackboard Learn", "Connect your school's Blackboard instance to import courses and gradebook due-dated items."),
        ("bell", "Smart Reminders", "Set custom reminder times before each due date and a do-not-disturb window for quiet hours."),
        ("timer", "Study Timer", "A built-in Pomodoro timer tracks focus and break sessions against your assignments."),
        ("flame", "Streak Tracking", "Stay motivated with completion streaks per class, with alerts when you break a record."),
        ("checkmark.circle", "Grading & Progress", "Track scores, subtasks, and per-assignment progress with a custom grading scale."),
        ("square.stack.3d.up", "Templates & Analytics", "Reuse assignment templates and visualize your workload and grades over time."),
        ("checkmark.shield", "Secure & Private", "Per-user data isolation plus optional two-factor auth (TOTP, email, or passkeys)."),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 28) {
                VStack(spacing: 14) {
                    Image(systemName: "graduationcap.fill").font(.system(size: 54)).foregroundStyle(.tint)
                    Text("Built for students who juggle everything")
                        .font(.footnote.weight(.semibold)).foregroundStyle(.tint)
                    Text("All your assignments, in one place.")
                        .font(.largeTitle.bold()).multilineTextAlignment(.center)
                    Text("Study Spot pulls every assignment from Google Calendar, Google Classroom, and Blackboard into a single dashboard — with smart reminders, a study timer, streaks, grading, and analytics.")
                        .font(.callout).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .padding(.top, 24)

                VStack(spacing: 10) {
                    NavigationLink { RegisterView() } label: {
                        Text("Create your account").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent).controlSize(.large)
                    NavigationLink { LoginView() } label: {
                        Text("I already have an account").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered).controlSize(.large)
                }

                VStack(spacing: 12) {
                    ForEach(features, id: \.1) { f in
                        HStack(alignment: .top, spacing: 14) {
                            Image(systemName: f.0).font(.title3).foregroundStyle(.tint).frame(width: 32)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(f.1).font(.headline)
                                Text(f.2).font(.subheadline).foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(14)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                    }
                }

                HStack(spacing: 16) {
                    NavigationLink("Privacy Policy") { PrivacyPolicyView() }
                    Button("Server: \(URL(string: AppConfig.serverURL)?.host ?? AppConfig.serverURL)") { showServer = true }
                }
                .font(.footnote)
                .padding(.bottom)
            }
            .padding(.horizontal, 20)
        }
        .sheet(isPresented: $showServer) { ServerSettingsView() }
    }
}

// MARK: - Login

struct LoginView: View {
    @Environment(AuthStore.self) private var auth
    @State private var email = ""
    @State private var password = ""
    @State private var error = ""
    @State private var loading = false
    @State private var showServer = false

    var body: some View {
        Form {
            if let reason = auth.disabledReason {
                Section {
                    Label("Your account has been disabled.", systemImage: "nosign").foregroundStyle(.red)
                    if !reason.isEmpty { Text(reason).font(.footnote) }
                }
            }
            Section {
                TextField("Email", text: $email)
                    .textContentType(.username).keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("Password", text: $password).textContentType(.password)
            } footer: {
                if !error.isEmpty { Text(error).foregroundStyle(.red) }
            }
            Section {
                Button {
                    Task { await submit() }
                } label: {
                    HStack { Spacer(); if loading { ProgressView() } else { Text("Log in").bold() }; Spacer() }
                }
                .disabled(loading || email.isEmpty || password.isEmpty)
                GoogleSignInButton(error: $error)
            }
            Section {
                NavigationLink("Forgot password?") { ForgotPasswordView() }
                NavigationLink("Have a reset link?") { ResetPasswordView() }
                NavigationLink("Create an account") { RegisterView() }
                Button("Server settings") { showServer = true }
            }
        }
        .navigationTitle("Welcome back")
        .sheet(isPresented: $showServer) { ServerSettingsView() }
    }

    private func submit() async {
        error = ""
        loading = true
        defer { loading = false }
        do {
            try await APIClient.shared.login(email: email.trimmingCharacters(in: .whitespaces), password: password)
            await auth.didSignIn()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct GoogleSignInButton: View {
    @Environment(AuthStore.self) private var auth
    @Binding var error: String
    @State private var busy = false

    var body: some View {
        Button {
            Task { await signIn() }
        } label: {
            HStack {
                Spacer()
                if busy { ProgressView() } else {
                    Image(systemName: "g.circle.fill")
                    Text("Continue with Google")
                }
                Spacer()
            }
        }
        .disabled(busy)
    }

    private func signIn() async {
        guard let url = APIClient.shared.googleLoginURL else { return }
        busy = true
        defer { busy = false }
        do {
            let callback = try await WebAuthSession.shared.start(url)
            guard let token = callback.param("token") else {
                error = "Google sign-in failed."
                return
            }
            APIClient.shared.setToken(token)
            await auth.didSignIn()
        } catch is WebAuthSession.Cancelled {
        } catch {
            self.error = error.localizedDescription
        }
    }
}

// MARK: - Register

struct RegisterView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var error = ""
    @State private var loading = false
    @State private var showOtp = false
    @State private var otp = ""

    var body: some View {
        Form {
            if showOtp {
                Section {
                    Text("We sent a code to \(email). Enter it below to verify your email.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    CodeField(code: $otp)
                } footer: {
                    if !error.isEmpty { Text(error).foregroundStyle(.red) }
                }
                Section {
                    Button { Task { await verify() } } label: {
                        HStack { Spacer(); if loading { ProgressView() } else { Text("Verify").bold() }; Spacer() }
                    }
                    .disabled(loading || otp.count != 6)
                    Button("Resend code") { Task { await resend() } }
                    Button("Use a different email") { showOtp = false; otp = "" }
                }
            } else {
                Section {
                    TextField("Name (optional)", text: $name).textContentType(.name)
                    TextField("Email", text: $email)
                        .textContentType(.username).keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    SecureField("Password (8+ characters)", text: $password).textContentType(.newPassword)
                    SecureField("Confirm password", text: $confirm).textContentType(.newPassword)
                } footer: {
                    if !error.isEmpty { Text(error).foregroundStyle(.red) }
                }
                Section {
                    Button { Task { await submit() } } label: {
                        HStack { Spacer(); if loading { ProgressView() } else { Text("Create account").bold() }; Spacer() }
                    }
                    .disabled(loading || email.isEmpty || password.isEmpty)
                    GoogleSignInButton(error: $error)
                }
            }
        }
        .navigationTitle(showOtp ? "Verify your email" : "Create your account")
    }

    private func submit() async {
        error = ""
        guard password == confirm else { error = "Passwords do not match"; return }
        guard password.count >= 8 else { error = "Password must be at least 8 characters"; return }
        loading = true
        defer { loading = false }
        do {
            try await APIClient.shared.register(email: email.trimmingCharacters(in: .whitespaces), password: password)
            showOtp = true
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func verify() async {
        error = ""
        loading = true
        defer { loading = false }
        do {
            try await APIClient.shared.verifyOtp(email: email.trimmingCharacters(in: .whitespaces), code: otp)
            let trimmed = name.trimmingCharacters(in: .whitespaces)
            if !trimmed.isEmpty { _ = try? await APIClient.shared.updateMe(["name": .string(trimmed)]) }
            await auth.didSignIn()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func resend() async {
        do {
            try await APIClient.shared.resendOtp(email: email.trimmingCharacters(in: .whitespaces))
            toasts.show("Code sent", "Check your email for the new code.")
        } catch {
            self.error = error.localizedDescription
        }
    }
}

// MARK: - Password reset

struct ForgotPasswordView: View {
    @State private var email = ""
    @State private var sent = false
    @State private var loading = false
    @State private var error = ""

    var body: some View {
        Form {
            if sent {
                Section {
                    Label("If an account exists for \(email), a reset link is on its way.", systemImage: "envelope.badge")
                    NavigationLink("I have the reset link") { ResetPasswordView() }
                }
            } else {
                Section {
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                } footer: {
                    if !error.isEmpty { Text(error).foregroundStyle(.red) } else {
                        Text("We'll email you a link to reset your password.")
                    }
                }
                Button {
                    Task {
                        loading = true
                        defer { loading = false }
                        do { try await APIClient.shared.forgotPassword(email: email); sent = true }
                        catch { self.error = error.localizedDescription }
                    }
                } label: {
                    HStack { Spacer(); if loading { ProgressView() } else { Text("Send reset link").bold() }; Spacer() }
                }
                .disabled(email.isEmpty || loading)
            }
        }
        .navigationTitle("Forgot password")
    }
}

struct ResetPasswordView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    @State private var link = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var loading = false
    @State private var error = ""

    /// Accepts the full emailed link or just the token.
    private var token: String {
        let t = link.trimmingCharacters(in: .whitespacesAndNewlines)
        return URL(string: t)?.param("token") ?? t
    }

    var body: some View {
        Form {
            Section {
                TextField("Paste the reset link from your email", text: $link, axis: .vertical)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            Section {
                SecureField("New password", text: $password).textContentType(.newPassword)
                SecureField("Confirm new password", text: $confirm).textContentType(.newPassword)
            } footer: {
                if !error.isEmpty { Text(error).foregroundStyle(.red) }
            }
            Button {
                Task { await submit() }
            } label: {
                HStack { Spacer(); if loading { ProgressView() } else { Text("Reset password").bold() }; Spacer() }
            }
            .disabled(loading || token.isEmpty || password.isEmpty)
        }
        .navigationTitle("Reset password")
    }

    private func submit() async {
        error = ""
        guard password == confirm else { error = "Passwords do not match"; return }
        guard password.count >= 8 else { error = "Password must be at least 8 characters"; return }
        loading = true
        defer { loading = false }
        do {
            try await APIClient.shared.resetPassword(token: token, newPassword: password)
            toasts.show("Password updated", "You can now log in with your new password.")
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

// MARK: - Server address

struct ServerSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @State private var url = AppConfig.serverURL
    @State private var status = ""
    @State private var checking = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("https://studyspot.example.com", text: $url)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                } header: {
                    Text("Server address")
                } footer: {
                    Text("The address of your Study Spot server. The app talks to its /api endpoints.")
                }
                Section {
                    Button {
                        Task { await test() }
                    } label: {
                        HStack { Text("Test connection"); Spacer(); if checking { ProgressView() } }
                    }
                    if !status.isEmpty { Text(status).font(.footnote) }
                    Button("Reset to default") { url = AppConfig.defaultServerURL }
                }
            }
            .navigationTitle("Server")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let changed = AppConfig.normalize(url) != AppConfig.serverURL
                        AppConfig.serverURL = url
                        if changed { auth.signOut() }
                        dismiss()
                    }
                }
            }
        }
    }

    private func test() async {
        checking = true
        defer { checking = false }
        let base = AppConfig.normalize(url)
        guard let u = URL(string: (base.hasSuffix("/api") ? base : base + "/api") + "/health") else {
            status = "Invalid address"
            return
        }
        do {
            let (_, res) = try await URLSession.shared.data(from: u)
            status = (res as? HTTPURLResponse)?.statusCode == 200 ? "✅ Connected" : "Server responded, but /api/health failed"
        } catch {
            status = "❌ \(error.localizedDescription)"
        }
    }
}

// MARK: - Privacy

struct PrivacyPolicyView: View {
    var body: some View {
        List {
            Section("What we collect") {
                Text("**Account information:** your name and email address, used to create and sign in to your account.")
                Text("**Assignment and class data:** the assignments, classes, scores, notes, and study sessions you create or import from connected services.")
                Text("**Connected service data:** when you link Google Calendar, Google Classroom, or Blackboard, we retrieve course work, due dates, and attachments you have access to, on your behalf.")
                Text("**Usage data:** basic analytics about feature usage to improve the app.")
            }
            Section("How we use it") {
                Text("To display and organize your assignments, classes, and progress.")
                Text("To send you email reminders about upcoming due dates based on your reminder settings.")
                Text("To sync completion status back to Google Classroom when you enable that option.")
                Text("To provide analytics, streaks, and grading features.")
                Text("To maintain, secure, and improve the app.")
            }
            Section("Your choices") {
                Text("Connect or disconnect Google and Blackboard integrations anytime in Settings.")
                Text("Customize or turn off email reminders and set a do-not-disturb window.")
                Text("Enable or disable two-factor authentication.")
                Text("Export your data, or delete your account and all associated assignments and classes, from Settings.")
            }
        }
        .navigationTitle("Privacy Policy")
    }
}
