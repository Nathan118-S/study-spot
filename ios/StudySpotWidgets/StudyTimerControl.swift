import AppIntents
import SwiftUI
import WidgetKit

/// iOS 18 control for Control Center, the Lock Screen and the Action Button:
/// a toggle that starts (or resumes) the study timer and pauses it, showing
/// whether it's running. Its intent is a LiveActivityIntent, so it runs in the
/// app's process, which also starts the Live Activity.
@available(iOS 18.0, *)
struct StudyTimerControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: StudyTimerShared.controlKind, provider: Provider()) { isRunning in
            ControlWidgetToggle("Study Timer", isOn: isRunning, action: ToggleStudyTimerIntent()) { running in
                Label(running ? "Focusing" : "Start Focus", systemImage: running ? "timer" : "play.fill")
            }
            .tint(.orange)
        }
        .displayName("Study Timer")
        .description("Start or pause your Study Spot focus timer.")
    }

    struct Provider: ControlValueProvider {
        var previewValue: Bool { false }

        func currentValue() async throws -> Bool {
            StudyTimerShared.isRunning
        }
    }
}
