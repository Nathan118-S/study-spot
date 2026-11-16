import SwiftUI
import Observation

@main
struct StudySpotApp: App {
    @State private var auth = AuthStore()
    @State private var theme = ThemeStore()
    @State private var data = DataStore()
    @State private var toasts = ToastCenter()
    @State private var router = Router()
    @State private var timer = StudyTimerModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(auth)
                .environment(theme)
                .environment(data)
                .environment(toasts)
                .environment(router)
                .environment(timer)
                .tint(theme.accent.color)
                .preferredColorScheme(theme.colorScheme)
                .transaction { t in if !theme.animations { t.animation = nil } }
                .onOpenURL { url in Task { await handle(url) } }
        }
    }

    /// Fallback for app-scheme links opened outside an auth session
    /// (e.g. Blackboard finishing in Safari).
    @MainActor
    private func handle(_ url: URL) async {
        guard url.scheme == AppConfig.urlScheme else { return }
        switch url.host {
        case "blackboard":
            guard let code = url.param("code") else { return }
            do {
                try await APIClient.shared.invoke("blackboardCallback", [
                    "code": .string(code), "state": .from(url.param("state")),
                ])
                await data.refreshConnections()
                toasts.show("Blackboard connected")
            } catch {
                toasts.show("Blackboard connection failed", error.localizedDescription, error: true)
            }
        case "oauth":
            if let token = url.param("token") {
                APIClient.shared.setToken(token)
                await auth.didSignIn()
            }
        case "connected":
            await data.refreshConnections()
        // Widget / Live Activity deep links.
        case "assignment":
            router.tab = .dashboard
            router.pendingAssignmentId = url.lastPathComponent
        case "timer":
            router.tab = .dashboard
            router.showTimer = true
        case "tab":
            switch url.lastPathComponent {
            case "streaks": router.tab = .streaks
            case "calendar": router.tab = .calendar
            case "classes": router.tab = .classes
            case "insights": router.tab = .insights
            default: router.tab = .dashboard
            }
        default:
            break
        }
    }
}

enum AppTab: Hashable {
    case dashboard, classes, calendar, streaks, insights
}

@MainActor
@Observable
final class Router {
    var tab: AppTab = .dashboard
    var showSettings = false
    var showAdmin = false
    var showNotifications = false
    /// Set by deep links; the dashboard opens it and clears it.
    var pendingAssignmentId: String?
    var showTimer = false

    /// Maps a web-style path (notification action_url) onto native navigation.
    func open(path: String) {
        showNotifications = false
        switch path {
        case "/classes": tab = .classes
        case "/calendar": tab = .calendar
        case "/analytics": tab = .streaks
        case "/insights": tab = .insights
        case "/settings": presentAfterDismiss { $0.showSettings = true }
        case let p where p.hasPrefix("/admin"): presentAfterDismiss { $0.showAdmin = true }
        default: tab = .dashboard
        }
    }

    /// SwiftUI can't present a sheet while another is still dismissing.
    func presentAfterDismiss(_ change: @escaping (Router) -> Void) {
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(450))
            change(self)
        }
    }
}
