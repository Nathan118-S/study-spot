import ActivityKit
import Foundation

/// Starts, updates and ends the study-timer Live Activity.
@MainActor
enum LiveActivityManager {
    /// The activity this app instance started (others may still be ending).
    private static var currentId: String?
    private static var cleanedUpThisLaunch = false

    // Push-to-start (iOS 17.2+): lets the server start a timer activity
    // (used by Admin → Simulate on iPhone).
    private(set) static var pushToStartToken: String?
    private static var observingPushToStart = false
    /// False while signed out or in the demo account, so a token iOS hands
    /// over then isn't registered.
    private static var registersPushToStart = false
    private static var bundleId: String { Bundle.main.bundleIdentifier ?? "" }
    private static var apsEnvironment: String {
        #if DEBUG
        "development"
        #else
        "production"
        #endif
    }

    private static var current: Activity<StudyTimerAttributes>? {
        guard let currentId else { return nil }
        return Activity<StudyTimerAttributes>.activities.first {
            $0.id == currentId && ($0.activityState == .active || $0.activityState == .stale)
        }
    }

    /// Ends activities left over from a previous launch (the timer itself
    /// isn't persisted, so nothing would ever end them).
    /// Runs once per launch: a later call could end an activity the server
    /// started by push while the app is open.
    static func endOrphans() {
        guard !cleanedUpThisLaunch, currentId == nil else { return }
        cleanedUpThisLaunch = true
        let leftovers = Activity<StudyTimerAttributes>.activities
        guard !leftovers.isEmpty else { return }
        Task { for a in leftovers { await a.end(nil, dismissalPolicy: .immediate) } }
    }

    static func sync(phase: String, phaseLabel: String, endsAt: Date?, remaining: Double, total: Double,
                     assignmentTitle: String?, className: String?) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let state = StudyTimerAttributes.ContentState(
            phase: phase, phaseLabel: phaseLabel, endsAt: endsAt, remaining: remaining, total: total
        )
        // Mark stale a minute after the phase should have ended, in case the
        // app isn't opened to finish it.
        let content = ActivityContent(state: state, staleDate: endsAt?.addingTimeInterval(60))

        if let activity = current, activity.attributes.assignmentTitle == assignmentTitle {
            Task { await activity.update(content) }
            return
        }
        // Different assignment (or none running): replace the activity.
        let accent = SharedStore.load()?.accentHex ?? "#f19d37"
        let attributes = StudyTimerAttributes(assignmentTitle: assignmentTitle, className: className, accentHex: accent)
        let old = Activity<StudyTimerAttributes>.activities
        // Request synchronously so a quick second sync updates this activity
        // instead of requesting another one.
        currentId = (try? Activity.request(attributes: attributes, content: content, pushType: nil))?.id
        Task {
            for a in old { await a.end(nil, dismissalPolicy: .immediate) }
        }
    }

    /// Registers this device's timer push-to-start token with the server
    /// (signed in, not demo). Call after sign-in.
    static func configurePush(user: User?) async {
        guard let user, !user.isDemo else {
            registersPushToStart = false
            return
        }
        registersPushToStart = true
        if let token = pushToStartToken { await registerPushToStart(token) }
        guard !observingPushToStart else { return }
        observingPushToStart = true
        if #available(iOS 17.2, *) {
            Task {
                for await data in Activity<StudyTimerAttributes>.pushToStartTokenUpdates {
                    let token = data.map { String(format: "%02x", $0) }.joined()
                    pushToStartToken = token
                    if registersPushToStart { await registerPushToStart(token) }
                }
            }
        }
    }

    private static func registerPushToStart(_ token: String) async {
        _ = try? await APIClient.shared.send("/devices/push-to-start", body: [
            "token": .string(token), "environment": .string(apsEnvironment), "bundleId": .string(bundleId),
            "activity": "timer",
        ])
    }

    /// Stops the server starting timer activities here (call before signing out).
    static func unregisterPush(bearer: String?) async {
        registersPushToStart = false
        guard let token = pushToStartToken, let bearer else { return }
        _ = try? await APIClient.shared.send("/devices/push-to-start", method: "DELETE",
                                             body: ["token": .string(token)], bearer: bearer)
    }

    /// Ends every timer activity. With a label, the final state stays briefly
    /// on the Lock Screen ("Focus complete") before it's dismissed.
    static func end(finalLabel: String? = nil) {
        let activities = Activity<StudyTimerAttributes>.activities
        currentId = nil
        guard !activities.isEmpty else { return }
        Task {
            for a in activities {
                if let finalLabel {
                    var s = a.content.state
                    s.phaseLabel = finalLabel
                    s.endsAt = nil
                    s.remaining = 0
                    s.finished = true
                    await a.end(ActivityContent(state: s, staleDate: nil), dismissalPolicy: .after(Date().addingTimeInterval(15 * 60)))
                } else {
                    await a.end(nil, dismissalPolicy: .immediate)
                }
            }
        }
    }
}
