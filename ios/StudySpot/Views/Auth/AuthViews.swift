import SwiftUI

// Users sign in on the Study Spot website: "Log in" and "Create account" open
// it in a private in-app browser session, and the site hands the session back
// to the app (studyspot://oauth/callback#token=…) once any sign-in method
// succeeds: username and password, a new account, or a passkey. Two-factor
// verification then happens natively in Verify2FAView.

@MainActor
enum WebSignIn {
    enum Page: String { case login, register }

    static func url(_ page: Page) -> URL? {
        URL(string: "\(AppConfig.webURL)/\(page.rawValue)?client=ios")
    }

    /// Returns false if the user cancelled.
    static func run(_ page: Page, auth: AuthStore) async throws -> Bool {
        guard let url = url(page) else {
            throw APIError(status: 0, message: "Invalid website address. Check it in Server settings.")
        }
        do {
            // Ephemeral, so nothing stays signed in inside the browser session.
            let callback = try await WebAuthSession.shared.start(url, ephemeral: true)
            guard let token = callback.param("token"), !token.isEmpty else {
                throw APIError(status: 0, message: "Sign-in didn't finish. Please try again.")
            }
            APIClient.shared.setToken(token)
            await auth.didSignIn()
            return true
        } catch is WebAuthSession.Cancelled {
            return false
        }
    }
}

struct AuthFlowView: View {
    var body: some View {
        NavigationStack { LandingView() }
    }
}

struct LandingView: View {
    @Environment(AuthStore.self) private var auth
    @State private var showServer = false
    @State private var busy: WebSignIn.Page?
    @State private var error = ""

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            // Logo and name. Long-press the logo for server settings
            // (only needed when pointing the app at another server).
            VStack(spacing: 16) {
                LogoMark(size: 112)
                    .onLongPressGesture { showServer = true }
                    .accessibilityAction(named: "Server settings") { showServer = true }
                Text("Study Spot")
                    .font(.largeTitle.bold())
            }

            Spacer()

            VStack(spacing: 12) {
                if let reason = auth.disabledReason {
                    Label {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Your account has been disabled.").font(.subheadline.weight(.semibold))
                            if !reason.isEmpty { Text(reason).font(.footnote) }
                        }
                    } icon: { Image(systemName: "nosign") }
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.red.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
                }
                if !error.isEmpty {
                    Text(error).font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center)
                }
                signInButton(.login, title: "Log in", prominent: true)
                signInButton(.register, title: "Sign up", prominent: false)
            }
            .padding(.bottom, 16)
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: 500)
        .frame(maxWidth: .infinity)
        .toolbar(.hidden, for: .navigationBar)
        .appBackground()
        .sheet(isPresented: $showServer) { ServerSettingsView() }
    }

    @ViewBuilder
    private func signInButton(_ page: WebSignIn.Page, title: String, prominent: Bool) -> some View {
        let button = Button {
            Task { await signIn(page) }
        } label: {
            HStack(spacing: 8) {
                if busy == page { ProgressView() }
                Text(title).fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
        }
        .controlSize(.large)
        .disabled(busy != nil)

        if prominent {
            button.glassButtonStyle(prominent: true)
        } else {
            button.glassButtonStyle()
        }
    }

    private func signIn(_ page: WebSignIn.Page) async {
        error = ""
        busy = page
        defer { busy = nil }
        do {
            _ = try await WebSignIn.run(page, auth: auth)
        } catch {
            self.error = error.localizedDescription
        }
    }
}

// MARK: - Server & website address

struct ServerSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @State private var url = AppConfig.serverURL
    /// Only an explicit override; empty means "same as the server address".
    @State private var web = UserDefaults.standard.string(forKey: AppConfig.webURLKey) ?? ""
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
                    Text("The Study Spot server. The app talks to its /api endpoints.")
                }
                Section {
                    TextField("Same as server address", text: $web)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                } header: {
                    Text("Website address")
                } footer: {
                    Text("Where you log in. Leave empty if the website is served from the server address.")
                }
                Section {
                    Button {
                        Task { await test() }
                    } label: {
                        HStack {
                            Text("Test connection")
                            Spacer()
                            if checking { ProgressView() }
                        }
                    }
                    if !status.isEmpty { Text(status).font(.footnote) }
                    Button("Reset to defaults") {
                        url = AppConfig.defaultServerURL
                        web = ""
                    }
                }
            }
            .navigationTitle("Server")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.tint(Palette.secondary) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let changed = AppConfig.normalize(url) != AppConfig.serverURL
                        AppConfig.serverURL = url
                        AppConfig.webURL = web
                        if changed && auth.user != nil { auth.signOut() }
                        dismiss()
                    }
                    .fontWeight(.semibold)
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
            status = (res as? HTTPURLResponse)?.statusCode == 200 ? "Connected" : "The server responded, but /api/health failed."
        } catch {
            status = error.localizedDescription
        }
    }
}

// MARK: - Privacy

struct PrivacyPolicyView: View {
    var body: some View {
        List {
            Section("What we collect") {
                Text("**Account information:** your name, username, and password, used to create and sign in to your account.")
                Text("**Assignment and class data:** the assignments, classes, scores, notes, and study sessions you create or import from connected services.")
                Text("**Connected service data:** when you link Google Calendar, Google Classroom, or Blackboard, we retrieve course work, due dates, and attachments you have access to, on your behalf.")
                Text("**Device information:** if you allow notifications, a push token for this device, used only to deliver your reminders and class countdowns. No advertising, analytics or tracking.")
            }
            Section("How we use it") {
                Text("To display and organize your assignments, classes, and progress.")
                Text("To send you notifications about upcoming due dates based on your reminder settings.")
                Text("To sync completion status back to Google Classroom when you enable that option.")
                Text("To provide analytics, streaks, and grading features.")
                Text("To maintain, secure, and improve the app.")
            }
            Section("Your choices") {
                Text("Connect or disconnect Google and Blackboard integrations anytime in Settings.")
                Text("Customize or turn off reminders and set a do-not-disturb window.")
                Text("Enable or disable two-factor authentication.")
                Text("Export your data from Settings, or permanently delete your account and everything in it from Settings → Danger Zone.")
            }
        }
        .navigationTitle("Privacy Policy")
    }
}
