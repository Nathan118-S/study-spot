import ActivityKit
import Foundation

/// Starts, updates and ends the study-timer Live Activity.
@MainActor
enum LiveActivityManager {
    /// The activity this app instance started (others may still be ending).
    private static var currentId: String?

    private static var current: Activity<StudyTimerAttributes>? {
        guard let currentId else { return nil }
        return Activity<StudyTimerAttributes>.activities.first {
            $0.id == currentId && ($0.activityState == .active || $0.activityState == .stale)
        }
    }

    /// Ends activities left over from a previous launch (the timer itself
    /// isn't persisted, so nothing would ever end them).
    static func endOrphans() {
        guard currentId == nil else { return }
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
