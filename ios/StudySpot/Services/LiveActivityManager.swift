import ActivityKit
import Foundation

/// Starts, updates and ends the study-timer Live Activity.
@MainActor
enum LiveActivityManager {
    private static var current: Activity<StudyTimerAttributes>? {
        Activity<StudyTimerAttributes>.activities.first { $0.activityState == .active || $0.activityState == .stale }
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
        Task {
            for a in old { await a.end(nil, dismissalPolicy: .immediate) }
            _ = try? Activity.request(attributes: attributes, content: content, pushType: nil)
        }
    }

    /// Ends every timer activity. With a label, the final state stays briefly
    /// on the Lock Screen ("Focus complete") before it's dismissed.
    static func end(finalLabel: String? = nil) {
        let activities = Activity<StudyTimerAttributes>.activities
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
