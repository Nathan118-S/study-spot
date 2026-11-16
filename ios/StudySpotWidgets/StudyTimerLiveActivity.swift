import ActivityKit
import SwiftUI
import WidgetKit

/// Study timer on the Lock Screen and in the Dynamic Island. The countdown
/// is rendered by the system from `endsAt`, so it keeps ticking while the app
/// is suspended.
struct StudyTimerLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: StudyTimerAttributes.self) { context in
            LockScreenTimerView(context: context)
                .activityBackgroundTint(nil)
                .widgetURL(DeepLink.timer)
        } dynamicIsland: { context in
            let accent = Color(hex: context.attributes.accentHex)
            let state = context.state
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label {
                        Text(state.phaseLabel).font(.caption.weight(.semibold))
                    } icon: {
                        Image(systemName: TimerStyle.symbol(state))
                    }
                    .foregroundStyle(TimerStyle.color(state, accent: accent))
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    TimerCountdown(state: state)
                        .font(.title2.bold().monospacedDigit())
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 96, alignment: .trailing)
                        .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.attributes.assignmentTitle ?? "General study")
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    TimerBar(state: state, tint: TimerStyle.color(state, accent: accent))
                        .padding(.horizontal, 4)
                }
            } compactLeading: {
                Image(systemName: TimerStyle.symbol(state))
                    .foregroundStyle(TimerStyle.color(state, accent: accent))
            } compactTrailing: {
                TimerCountdown(state: state)
                    .font(.caption.bold().monospacedDigit())
                    .frame(maxWidth: 44)
                    .foregroundStyle(TimerStyle.color(state, accent: accent))
            } minimal: {
                TimerRing(state: state, tint: TimerStyle.color(state, accent: accent))
            }
            .widgetURL(DeepLink.timer)
            .keylineTint(accent)
        }
    }
}

enum TimerStyle {
    static func symbol(_ s: StudyTimerAttributes.ContentState) -> String {
        if s.finished { return "checkmark.circle.fill" }
        if !s.isRunning { return "pause.circle.fill" }
        return s.phase == "focus" ? "brain.head.profile" : "cup.and.saucer.fill"
    }

    static func color(_ s: StudyTimerAttributes.ContentState, accent: Color) -> Color {
        if s.finished { return .green }
        switch s.phase {
        case "short": return .green
        case "long": return .cyan
        default: return accent
        }
    }
}

struct TimerCountdown: View {
    var state: StudyTimerAttributes.ContentState
    var body: some View {
        if let end = state.endsAt, state.isRunning {
            Text(timerInterval: state.phaseStart...end, countsDown: true)
        } else if state.finished {
            Text("Done")
        } else {
            let s = Int(state.remaining.rounded(.up))
            Text(String(format: "%d:%02d", s / 60, s % 60))
        }
    }
}

struct TimerBar: View {
    var state: StudyTimerAttributes.ContentState
    var tint: Color
    var body: some View {
        if let end = state.endsAt, state.isRunning {
            ProgressView(timerInterval: state.phaseStart...end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                .tint(tint)
        } else {
            ProgressView(value: state.finished ? 1 : state.progress).tint(tint)
        }
    }
}

struct TimerRing: View {
    var state: StudyTimerAttributes.ContentState
    var tint: Color
    var body: some View {
        Group {
            if let end = state.endsAt, state.isRunning {
                ProgressView(timerInterval: state.phaseStart...end, countsDown: true) { EmptyView() } currentValueLabel: {
                    Image(systemName: TimerStyle.symbol(state)).font(.system(size: 9))
                }
            } else {
                ProgressView(value: state.finished ? 1 : state.progress) {
                    EmptyView()
                } currentValueLabel: {
                    Image(systemName: TimerStyle.symbol(state)).font(.system(size: 9))
                }
            }
        }
        .progressViewStyle(.circular)
        .tint(tint)
    }
}

struct LockScreenTimerView: View {
    let context: ActivityViewContext<StudyTimerAttributes>

    var body: some View {
        let state = context.state
        let accent = Color(hex: context.attributes.accentHex)
        let tint = TimerStyle.color(state, accent: accent)
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: TimerStyle.symbol(state))
                    .font(.title3)
                    .foregroundStyle(tint)
                    .frame(width: 44, height: 44)
                    .background(tint.opacity(0.18), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(state.phaseLabel).font(.headline)
                        if !state.isRunning && !state.finished {
                            Text("Paused").font(.caption2.weight(.semibold))
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(.secondary.opacity(0.2), in: Capsule())
                        }
                    }
                    Text(context.attributes.assignmentTitle ?? "General study")
                        .font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                TimerCountdown(state: state)
                    .font(.system(size: 34, weight: .semibold, design: .rounded).monospacedDigit())
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 120, alignment: .trailing)
                    .foregroundStyle(tint)
            }
            TimerBar(state: state, tint: tint)
        }
        .padding(16)
    }
}

#Preview("Lock Screen", as: .content, using: StudyTimerAttributes(assignmentTitle: "Essay Draft", className: "English Literature", accentHex: "#f19d37")) {
    StudyTimerLiveActivity()
} contentStates: {
    StudyTimerAttributes.ContentState(phase: "focus", phaseLabel: "Focus", endsAt: Date().addingTimeInterval(15 * 60), remaining: 900, total: 1500)
    StudyTimerAttributes.ContentState(phase: "focus", phaseLabel: "Focus", endsAt: nil, remaining: 612, total: 1500)
}

#Preview("Island expanded", as: .dynamicIsland(.expanded), using: StudyTimerAttributes(assignmentTitle: "Essay Draft", className: nil, accentHex: "#f19d37")) {
    StudyTimerLiveActivity()
} contentStates: {
    StudyTimerAttributes.ContentState(phase: "focus", phaseLabel: "Focus", endsAt: Date().addingTimeInterval(15 * 60), remaining: 900, total: 1500)
}

#Preview("Island compact", as: .dynamicIsland(.compact), using: StudyTimerAttributes(assignmentTitle: "Essay Draft", className: nil, accentHex: "#f19d37")) {
    StudyTimerLiveActivity()
} contentStates: {
    StudyTimerAttributes.ContentState(phase: "focus", phaseLabel: "Focus", endsAt: Date().addingTimeInterval(15 * 60), remaining: 900, total: 1500)
}
