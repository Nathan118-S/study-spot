import UIKit
import UserNotifications

/// Assignment reminder push notifications.
///
/// The server sends reminders as APNs alert pushes (thread "assignment-reminders")
/// when an APNs key is set in Admin → Integration Secrets and the app is built
/// with the Push Notifications capability. This registers the device's token
/// with the server; without the capability registration simply fails and
/// reminders still show in the in-app notification center.
@MainActor
enum PushNotifications {
    private static let tokenKey = "push.alertDeviceToken"

    /// This device's APNs token (hex), once iOS provides one.
    private(set) static var deviceToken: String? = UserDefaults.standard.string(forKey: tokenKey)
    /// Whether the current user wants pushes on this device; a token that
    /// arrives while this is false isn't sent to the server.
    private static var wantsRegistration = false

    private static var bundleId: String { Bundle.main.bundleIdentifier ?? "" }
    private static var apsEnvironment: String {
        #if DEBUG
        "development"
        #else
        "production"
        #endif
    }

    static func isEnabled(for user: User?) -> Bool {
        guard let user, !user.isDemo else { return false }
        return user.data["reminders_enabled"]?.boolValue != false
            && user.data["push_reminders"]?.boolValue != false
    }

    /// Call after sign-in and whenever the reminder settings change.
    static func configure(user: User?) async {
        guard isEnabled(for: user) else {
            await unregister(bearer: APIClient.shared.token)
            return
        }
        // Skip the permission prompt if the server can't send pushes yet.
        if let cfg = (try? await APIClient.shared.request("/devices/config", as: JSONValue.self)),
           cfg["alertPush"]?.boolValue == false {
            return
        }
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        guard granted else {
            await unregister(bearer: APIClient.shared.token)
            return
        }
        wantsRegistration = true
        // iOS answers in AppDelegate → didRegister(token:).
        UIApplication.shared.registerForRemoteNotifications()
    }

    /// Called from the app delegate with the APNs device token.
    static func didRegister(token data: Data) async {
        let token = data.map { String(format: "%02x", $0) }.joined()
        deviceToken = token
        UserDefaults.standard.set(token, forKey: tokenKey)
        guard wantsRegistration, APIClient.shared.token != nil else { return }
        _ = try? await APIClient.shared.send("/devices/alerts", body: [
            "token": .string(token), "environment": .string(apsEnvironment), "bundleId": .string(bundleId),
        ])
    }

    /// Stops this device receiving reminder pushes (call before signing out,
    /// with the outgoing session's token).
    static func unregister(bearer: String?) async {
        wantsRegistration = false
        guard let token = deviceToken, let bearer else { return }
        _ = try? await APIClient.shared.send("/devices/alerts", method: "DELETE",
                                             body: ["token": .string(token)], bearer: bearer)
    }

    /// The app link a notification should open, if any.
    nonisolated static func url(from userInfo: [AnyHashable: Any]) -> URL? {
        if let s = userInfo["url"] as? String, let url = URL(string: s), url.scheme == AppConfig.urlScheme {
            return url
        }
        if let id = userInfo["assignmentId"] as? String, !id.isEmpty {
            return URL(string: "\(AppConfig.urlScheme)://assignment/\(id)")
        }
        return nil
    }
}

/// Hands app links from notification taps to the SwiftUI app. A tap can
/// launch the app before its scene exists, so links wait until a handler is set.
@MainActor
enum DeepLinkInbox {
    private static var pending: [URL] = []
    private static var handler: ((URL) -> Void)?

    static func deliver(_ url: URL) {
        if let handler { handler(url) } else { pending.append(url) }
    }

    static func setHandler(_ newHandler: @escaping (URL) -> Void) {
        handler = newHandler
        let queued = pending
        pending = []
        for url in queued { newHandler(url) }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in await PushNotifications.didRegister(token: deviceToken) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // No Push Notifications capability, or the simulator: reminders still
        // arrive in the in-app notification center.
    }

    // MARK: UNUserNotificationCenterDelegate

    /// Show notifications while the app is open, except the study timer's
    /// own alert (the timer screen already shows the phase change).
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            willPresent notification: UNNotification,
                                            withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        if notification.request.identifier == LocalNotifier.timerId {
            completionHandler([])
        } else {
            completionHandler([.banner, .list, .sound])
        }
    }

    /// Tapping a reminder opens its assignment.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            didReceive response: UNNotificationResponse,
                                            withCompletionHandler completionHandler: @escaping () -> Void) {
        let url = PushNotifications.url(from: response.notification.request.content.userInfo)
        if let url {
            Task { @MainActor in DeepLinkInbox.deliver(url) }
        }
        completionHandler()
    }
}
