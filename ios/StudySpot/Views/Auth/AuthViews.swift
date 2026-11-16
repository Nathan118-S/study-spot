import SwiftUI

// Users sign in on the Study Spot website: "Log in" and "Create account" open
// it in a private in-app browser session, and the site hands the session back
// to the app (studyspot://oauth/callback#token=…) once any sign-in method
// succeeds: email and password, a new account, or Google. Two-factor
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

    private let features: [Feature] = [
        Feature(symbol: "calendar", title: "Google Calendar Sync", text: "Assignments auto-import from your Google Calendar events using smart keyword matching."),
        Feature(symbol: "book", title: "Google Classroom", text: "Pull coursework, due dates, and attachments straight from Classroom — and mark work done on both sides."),
        Feature(symbol: "building.columns", title: "Blackboard Learn", text: "Connect your school's Blackboard instance to import courses and gradebook due-dated items."),
        Feature(symbol: "bell", title: "Smart Reminders", text: "Set custom reminder times before each due date and a do-not-disturb window for quiet hours."),
        Feature(symbol: "timer", title: "Study Timer", text: "A built-in Pomodoro timer tracks focus and break sessions, right on your Lock Screen."),
        Feature(symbol: "flame", title: "Streak Tracking", text: "Stay motivated with completion streaks per class, with alerts when you break a record."),
        Feature(symbol: "checkmark.circle", title: "Grading & Progress", text: "Track scores, subtasks, and per-assignment progress with a custom grading scale."),
        Feature(symbol: "square.stack.3d.up", title: "Templates & Widgets", text: "Reuse assignment templates and see what's next from your Home and Lock Screen."),
        Feature(symbol: "checkmark.shield", title: "Secure & Private", text: "Per-user data isolation plus optional two-factor auth (TOTP, email, or passkeys)."),
    ]

    struct Feature: Identifiable {
        var symbol: String
        var title: String
        var text: String
        var id: String { title }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 28) {
                VStack(spacing: 12) {
                    Image(systemName: "graduationcap.fill").font(.system(size: 54)).foregroundStyle(.tint)
                    Text("Built for students who juggle everything")
                        .font(.footnote.weight(.semibold)).foregroundStyle(.tint)
                    Text("All your assignments, in one place.")
                        .font(.largeTitle.bold()).multilineTextAlignment(.center)
                    Text("Study Spot pulls every assignment from Google Calendar, Google Classroom, and Blackboard into a single dashboard — with smart reminders, a study timer, streaks, grading, and analytics.")
                        .font(.callout).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .padding(.top, 24)

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
                    signInButton(.register, title: "Create your account", prominent: true)
                    signInButton(.login, title: "Log in", prominent: false)
                    if !error.isEmpty {
                        Text(error).font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center)
                    }
                    Text("You'll sign in on the Study Spot website, then return here automatically.")
                        .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }

                VStack(spacing: 12) {
                    ForEach(features) { f in
                        HStack(alignment: .top, spacing: 14) {
                            Image(systemName: f.symbol).font(.title3).foregroundStyle(.tint).frame(width: 28)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(f.title).font(.headline)
                                Text(f.text).font(.subheadline).foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(16)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                    }
                }

                HStack(spacing: 20) {
                    NavigationLink("Privacy Policy") { PrivacyPolicyView() }
                    Button("Server settings") { showServer = true }
                }
                .font(.footnote)
                .padding(.bottom, 8)
            }
            .padding(.horizontal, 20)
        }
        .scrollBounceBehavior(.basedOnSize)
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
            button.buttonStyle(.borderedProminent)
        } else {
            button.buttonStyle(.bordered)
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
    @State private var web = AppConfig.webURL
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
                    TextField("https://studyspot.example.com", text: $web)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                } header: {
                    Text("Website address")
                } footer: {
                    Text("Where you log in. Usually the same as the server address.")
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
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
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
