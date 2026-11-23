import AppIntents
import Foundation
import WidgetKit

// Compiled into both the app and the widget extension: the extension needs
// the intent types for the Control Center control, while `perform()` always
// runs in the app's process. Every intent here is a `LiveActivityIntent`, so
// the system launches the app (in the background if needed) to run it, and
// the app is allowed to start the timer's Live Activity from there.

/// Timer state shared through the App Group, so the Control Center control
/// can show whether the timer is running without the app being open.
enum StudyTimerShared {
    /// `kind` of the iOS 18 Control Center / Action Button control.
    static let controlKind = "StudySpotTimerControl"
    static let defaultFocusMinutes = 25

    private static let focusMinutesKey = "timer_focus_minutes_v1"
    private static let endsAtKey = "timer_ends_at_v1"

    /// The focus length the user last picked in the app (25 min until then).
    static var usualFocusMinutes: Int {
        get {
            let m = SharedStore.defaults?.integer(forKey: focusMinutesKey) ?? 0
            return m > 0 ? m : defaultFocusMinutes
        }
        set { SharedStore.defaults?.set(newValue, forKey: focusMinutesKey) }
    }

    /// When the running phase ends; nil while paused or stopped.
    static var endsAt: Date? {
        get {
            guard let t = SharedStore.defaults?.object(forKey: endsAtKey) as? Double else { return nil }
            return Date(timeIntervalSince1970: t)
        }
        set {
            if let newValue {
                SharedStore.defaults?.set(newValue.timeIntervalSince1970, forKey: endsAtKey)
            } else {
                SharedStore.defaults?.removeObject(forKey: endsAtKey)
            }
        }
    }

    static var isRunning: Bool { endsAt.map { $0 > Date() } ?? false }

    /// Saves the running state and refreshes the control. Called by the app
    /// whenever the timer starts, pauses, resets or finishes a phase.
    static func publish(endsAt: Date?) {
        let changed = self.endsAt != endsAt
        self.endsAt = endsAt
        guard changed else { return }
        if #available(iOS 18.0, *) {
            ControlCenter.shared.reloadControls(ofKind: controlKind)
        }
    }
}

/// What the intents ask the app's timer to do. The app sets
/// `StudyTimerIntentBridge.handler` at launch (StudySpotApp.init).
@MainActor
protocol StudyTimerIntentHandling: AnyObject {
    var intentIsRunning: Bool { get }
    /// Starts focusing. `minutes` nil resumes a paused session, or starts
    /// the usual focus length.
    func intentStart(minutes: Int?)
    func intentPause()
    func intentResume()
    func intentStop()
}

@MainActor
enum StudyTimerIntentBridge {
    static var handler: (any StudyTimerIntentHandling)?

    static func requireHandler() throws -> any StudyTimerIntentHandling {
        if let snapshot = SharedStore.load(), !snapshot.signedIn { throw StudyTimerIntentError.signedOut }
        guard let handler else { throw StudyTimerIntentError.appUnavailable }
        return handler
    }
}

enum StudyTimerIntentError: Error, CustomLocalizedStringResourceConvertible {
    case signedOut, appUnavailable

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .signedOut: "Open Study Spot and sign in to use the study timer."
        case .appUnavailable: "Open Study Spot to use the study timer."
        }
    }
}

// MARK: - Intents (Shortcuts, Siri, Spotlight, Action Button)

struct StartStudyTimerIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Start Study Timer"
    static let description = IntentDescription("Starts a Study Spot focus session, with a Live Activity on the Lock Screen.")

    @Parameter(title: "Minutes", description: "Focus length, 1–180 minutes. Leave empty to use your usual length.")
    var minutes: Int?

    static var parameterSummary: some ParameterSummary {
        Summary("Start a study timer for \(\.$minutes) minutes")
    }

    init() {}

    init(minutes: Int?) {
        self.minutes = minutes
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let handler = try StudyTimerIntentBridge.requireHandler()
        if handler.intentIsRunning && minutes == nil {
            return .result(dialog: "Your study timer is already running.")
        }
        let length = minutes.map { max(1, min(180, $0)) }
        handler.intentStart(minutes: length)
        if let length {
            return .result(dialog: "Started a \(length)-minute study timer.")
        }
        return .result(dialog: "Study timer started.")
    }
}

struct PauseStudyTimerIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Pause or Resume Study Timer"
    static let description = IntentDescription("Pauses the Study Spot timer, or resumes it if it's paused.")

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let handler = try StudyTimerIntentBridge.requireHandler()
        if handler.intentIsRunning {
            handler.intentPause()
            return .result(dialog: "Study timer paused.")
        }
        handler.intentResume()
        return .result(dialog: "Study timer resumed.")
    }
}

struct StopStudyTimerIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Stop Study Timer"
    static let description = IntentDescription("Stops and resets the Study Spot timer.")

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let handler = try StudyTimerIntentBridge.requireHandler()
        handler.intentStop()
        return .result(dialog: "Study timer stopped.")
    }
}

/// Backs the iOS 18 Control Center / Action Button toggle: on starts (or
/// resumes) focusing, off pauses.
@available(iOS 18.0, *)
struct ToggleStudyTimerIntent: SetValueIntent, LiveActivityIntent {
    static let title: LocalizedStringResource = "Study Timer"
    static let description = IntentDescription("Starts or pauses the Study Spot focus timer.")
    static let isDiscoverable = false

    @Parameter(title: "Running")
    var value: Bool

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        let handler = try StudyTimerIntentBridge.requireHandler()
        if value {
            if !handler.intentIsRunning { handler.intentStart(minutes: nil) }
        } else if handler.intentIsRunning {
            handler.intentPause()
        }
        return .result()
    }
}
