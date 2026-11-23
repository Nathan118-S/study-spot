import SwiftUI

struct RootView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(ToastCenter.self) private var toasts
    @Environment(ThemeStore.self) private var theme
    @Environment(StudyTimerModel.self) private var timer

    var body: some View {
        ZStack {
            switch auth.phase {
            case .loading:
                SplashView()
            case .unreachable(let message):
                UnreachableView(message: message)
            case .signedOut:
                AuthFlowView()
            case .signedIn:
                if auth.needsTwoFactor {
                    Verify2FAView()
                } else {
                    MainTabView()
                }
            }
        }
        .task {
            ToastWindow.install(toasts: toasts, theme: theme)
            ToastWindow.setStyle(theme.colorScheme)
            await auth.check()
        }
        .onChange(of: theme.colorScheme) { _, scheme in ToastWindow.setStyle(scheme) }
        .onChange(of: auth.phase) { _, phase in
            if phase == .signedOut {
                data.reset()
                timer.reset()
                WidgetBridge.publish(user: nil, data: data, accent: theme.accent)
            }
        }
    }
}

struct SplashView: View {
    var body: some View {
        VStack(spacing: 16) {
            // Same logo and size as the launch screen, so the hand-off is seamless.
            LogoMark(size: 128)
            ProgressView()
        }
    }
}

struct UnreachableView: View {
    @Environment(AuthStore.self) private var auth
    var message: String
    @State private var showServer = false

    var body: some View {
        ContentUnavailableView {
            Label("Can't reach Study Spot", systemImage: "wifi.exclamationmark")
        } description: {
            Text(message)
        } actions: {
            Button("Try again") { Task { await auth.check() } }
                .glassButtonStyle(prominent: true)
            Button("Change server") { showServer = true }
            Button("Sign out", role: .destructive) { auth.signOut() }
        }
        .sheet(isPresented: $showServer) { ServerSettingsView() }
    }
}

struct MainTabView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(Router.self) private var router
    @Environment(ToastCenter.self) private var toasts
    @Environment(StudyTimerModel.self) private var timer
    @Environment(ThemeStore.self) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @State private var showWelcome = false

    var body: some View {
        @Bindable var router = router
        TabView(selection: $router.tab) {
            NavigationStack { DashboardView().appToolbar() }
                .tabItem { Label("Dashboard", systemImage: "square.grid.2x2") }
                .tag(AppTab.dashboard)
            NavigationStack { ClassesView().appToolbar() }
                .tabItem { Label("Classes", systemImage: "book") }
                .tag(AppTab.classes)
            NavigationStack { CalendarScreen().appToolbar() }
                .tabItem { Label("Calendar", systemImage: "calendar") }
                .tag(AppTab.calendar)
            NavigationStack { StreaksView().appToolbar() }
                .tabItem { Label("Streaks", systemImage: "flame") }
                .tag(AppTab.streaks)
            NavigationStack { InsightsView().appToolbar() }
                .tabItem { Label("Insights", systemImage: "chart.line.uptrend.xyaxis") }
                .tag(AppTab.insights)
        }
        .sheet(isPresented: $router.showSettings) { NavigationStack { SettingsView() } }
        .sheet(isPresented: $router.showAdmin) { NavigationStack { AdminView() } }
        .sheet(isPresented: $router.showNotifications) { NavigationStack { NotificationsView() }.presentationDetents([.medium, .large]) }
        .fullScreenCover(isPresented: .constant(auth.needsOnboarding)) { OnboardingView() }
        .overlay { if showWelcome { WelcomeOverlay(isPresented: $showWelcome) } }
        .task {
            if !timer.running { LiveActivityManager.endOrphans() }
            guard let user = auth.user else { return }
            await data.load(for: user)
            publishWidgets()
            await ClassActivityManager.configure(user: auth.user, classes: data.classes, assignments: data.assignments)
            await PushNotifications.configure(user: auth.user)
            await LiveActivityManager.configurePush(user: auth.user)
            await data.loadNotifications()
            maybeGreet()
            await data.autoSyncIfStale(user: user, auth: auth)
        }
        .task(id: timer.running) {
            // Drives the study timer whichever tab is showing.
            while timer.running && !Task.isCancelled {
                timer.tick(assignments: data.assignments, isDemo: auth.user?.isDemo == true, toasts: toasts)
                try? await Task.sleep(for: .milliseconds(250))
            }
        }
        // Keep Home/Lock Screen widgets in step with the app's data.
        .onChange(of: data.assignments) { _, _ in publishWidgets() }
        .onChange(of: data.classes) { _, _ in
            publishWidgets()
            Task { await ClassActivityManager.configure(user: auth.user, classes: data.classes, assignments: data.assignments) }
        }
        .onChange(of: auth.user?.data["class_live_activities"]) { _, _ in
            Task { await ClassActivityManager.configure(user: auth.user, classes: data.classes, assignments: data.assignments) }
        }
        .onChange(of: auth.user?.data["push_reminders"]) { _, _ in
            Task { await PushNotifications.configure(user: auth.user) }
        }
        .onChange(of: auth.user?.data["reminders_enabled"]) { _, _ in
            Task { await PushNotifications.configure(user: auth.user) }
        }
        .task(id: scenePhase) {
            // While the app is open, start a class's Live Activity right at
            // 10 minutes before it (and end finished ones).
            while scenePhase == .active && !Task.isCancelled {
                if ClassActivityManager.isEnabled(for: auth.user) {
                    ClassActivityManager.refresh(classes: data.classes, assignments: data.assignments)
                }
                try? await Task.sleep(for: .seconds(15))
            }
        }
        .onChange(of: data.connections) { _, _ in publishWidgets() }
        .onChange(of: theme.colorTheme) { _, _ in publishWidgets() }
        .followsSavedColorTheme(auth.user, theme: theme)
        .onChange(of: auth.needsOnboarding) { _, needs in if !needs { maybeGreet() } }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task {
                    // Back in the foreground: send changes made offline, then refresh.
                    await data.syncPendingChanges()
                    if data.isOffline || data.showingCachedData { await data.reloadData() }
                    await data.loadNotifications()
                }
            }
        }
        .onChange(of: data.isOffline) { wasOffline, isOffline in
            // Back online after starting from the cache: refresh the account too.
            if wasOffline && !isOffline { Task { await auth.refreshUser() } }
        }
        .onChange(of: data.offlineSyncError) { _, message in
            guard let message else { return }
            toasts.show("A change couldn't be synced", message, error: true)
            data.offlineSyncError = nil
        }
    }

    private func publishWidgets() {
        WidgetBridge.publish(user: auth.user, data: data, accent: theme.accent)
    }

    private func maybeGreet() {
        guard let user = auth.user, !auth.hasGreetedThisLaunch, user.onboardingCompleted, user.welcomeStyle != "off" else { return }
        auth.hasGreetedThisLaunch = true
        showWelcome = true
    }
}

/// Bell + account menu shown on every main screen.
struct AppToolbarModifier: ViewModifier {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(Router.self) private var router

    func body(content: Content) -> some View {
        content.appBackground()
            // "Offline — changes will sync" / "N changes waiting to sync" / "Last updated …".
            .safeAreaInset(edge: .top, spacing: 0) { OfflineBanner() }
            .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { router.showNotifications = true } label: {
                    Image(systemName: data.unreadCount > 0 ? "bell.badge" : "bell")
                        .foregroundStyle(Palette.secondary)
                }
                .accessibilityLabel("Notifications")
                Menu {
                    if let user = auth.user {
                        Section(user.username) {
                            if user.isDemo { Label("Demo account", systemImage: "eye") }
                        }
                    }
                    Button { router.showSettings = true } label: { Label("Settings", systemImage: "gearshape") }
                    if auth.user?.isAdmin == true {
                        Button { router.showAdmin = true } label: { Label("Admin", systemImage: "checkmark.shield") }
                    }
                    Divider()
                    Button(role: .destructive) { auth.signOut() } label: { Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right") }
                } label: {
                    Image(systemName: "person.crop.circle")
                        .foregroundStyle(Palette.secondary)
                }
                .accessibilityLabel("Account")
            }
        }
    }
}

extension View {
    func appToolbar() -> some View { modifier(AppToolbarModifier()) }
}

struct WelcomeOverlay: View {
    @Environment(AuthStore.self) private var auth
    @Binding var isPresented: Bool
    @State private var appeared = false

    var body: some View {
        let name = auth.user?.displayName ?? ""
        let large = auth.user?.welcomeStyle != "small"
        Group {
            if large {
                ZStack {
                    Rectangle().fill(.ultraThinMaterial).ignoresSafeArea()
                    VStack(spacing: 10) {
                        Text("👋").font(.system(size: 56))
                            .rotationEffect(.degrees(appeared ? 0 : -12))
                            .scaleEffect(appeared ? 1 : 0.6)
                        (Text("Hello").foregroundStyle(LinearGradient(colors: [.accentColor, .orange], startPoint: .leading, endPoint: .trailing))
                         + Text(name.isEmpty ? "!" : ", \(name)!"))
                            .font(.largeTitle.bold())
                            .multilineTextAlignment(.center)
                        Text("Welcome back to Study Spot").font(.subheadline).foregroundStyle(.secondary)
                    }
                    .padding(32)
                    .offset(y: appeared ? 0 : 16)
                    .opacity(appeared ? 1 : 0)
                }
            } else {
                VStack {
                    HStack(spacing: 12) {
                        Text("👋").font(.title)
                        VStack(alignment: .leading) {
                            Text("Hello\(name.isEmpty ? "!" : ", \(name)!")").font(.headline)
                            Text("Welcome back to Study Spot").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .padding()
                    .glassCard(cornerRadius: 20)
                    .shadow(radius: 10)
                    .offset(y: appeared ? 0 : -40)
                    .opacity(appeared ? 1 : 0)
                    Spacer()
                }
                .padding(.top, 8)
            }
        }
        .onTapGesture { isPresented = false }
        .task {
            withAnimation(.spring(duration: 0.5)) { appeared = true }
            try? await Task.sleep(for: .seconds(2.8))
            withAnimation { isPresented = false }
        }
    }
}
