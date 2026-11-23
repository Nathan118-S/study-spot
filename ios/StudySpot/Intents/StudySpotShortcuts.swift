import AppIntents

/// App Shortcuts: shown in the Shortcuts app and Spotlight, usable with Siri,
/// and assignable to the Action Button (Settings → Action Button → Shortcut).
/// The intents live in Shared/StudyTimerIntents.swift.
struct StudySpotShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: StartStudyTimerIntent(),
            phrases: [
                "Start a study timer in \(.applicationName)",
                "Start a \(.applicationName) timer",
                "Start focusing with \(.applicationName)",
                "Start studying with \(.applicationName)",
            ],
            shortTitle: "Start Study Timer",
            systemImageName: "timer"
        )
        AppShortcut(
            intent: PauseStudyTimerIntent(),
            phrases: [
                "Pause my \(.applicationName) timer",
                "Resume my \(.applicationName) timer",
                "Pause the study timer in \(.applicationName)",
            ],
            shortTitle: "Pause or Resume Timer",
            systemImageName: "playpause"
        )
        AppShortcut(
            intent: StopStudyTimerIntent(),
            phrases: [
                "Stop my \(.applicationName) timer",
                "Stop the study timer in \(.applicationName)",
            ],
            shortTitle: "Stop Study Timer",
            systemImageName: "stop.circle"
        )
    }
}
