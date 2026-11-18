import ActivityKit
import SwiftUI
import WidgetKit

/// "Class starts soon": shown 10 minutes before a class, on the Lock Screen
/// and in the Dynamic Island, with a circular countdown to the start and
/// anything due that day. The system animates the countdown from
/// `startsAt`, so it keeps running while the app is closed.
struct ClassStartLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ClassStartAttributes.self) { context in
            ClassStartLockScreenView(context: context)
                .activityBackgroundTint(nil)
                .widgetURL(DeepLink.dashboard)
        } dynamicIsland: { context in
            let tint = Color(hex: context.attributes.colorHex)
            let state = context.state
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    CountdownRing(state: state, tint: tint, size: 52, showsTime: true)
                        .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("Starts").font(.caption2).foregroundStyle(.secondary)
                        Text(state.startDate, style: .time).font(.headline)
                    }
                    .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        Text(context.attributes.className).font(.headline).lineLimit(1)
                        ClassDetailLine(attributes: context.attributes)
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if state.dueCount > 0 {
                        DueTodayList(state: state, compact: true)
                            .padding(.horizontal, 4)
                    }
                }
            } compactLeading: {
                CountdownRing(state: state, tint: tint, size: 22, showsTime: false)
            } compactTrailing: {
                Text(timerInterval: state.countdownStart...state.startDate, countsDown: true)
                    .monospacedDigit()
                    .font(.caption.weight(.semibold))
                    .frame(maxWidth: 44)
                    .foregroundStyle(tint)
            } minimal: {
                CountdownRing(state: state, tint: tint, size: 22, showsTime: false)
            }
            .widgetURL(DeepLink.dashboard)
            .keylineTint(tint)
        }
    }
}

/// Circular countdown over the 10 minutes before class.
struct CountdownRing: View {
    var state: ClassStartAttributes.ContentState
    var tint: Color
    var size: CGFloat
    var showsTime: Bool

    var body: some View {
        ZStack {
            ProgressView(timerInterval: state.countdownStart...state.startDate, countsDown: true) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
            .progressViewStyle(.circular)
            .tint(tint)
            if showsTime {
                Text(timerInterval: state.countdownStart...state.startDate, countsDown: true)
                    .font(.system(size: size * 0.24, weight: .bold, design: .rounded).monospacedDigit())
                    .multilineTextAlignment(.center)
                    .frame(width: size * 0.78)
            } else {
                Image(systemName: "graduationcap.fill")
                    .font(.system(size: size * 0.42))
                    .foregroundStyle(tint)
            }
        }
        .frame(width: size, height: size)
    }
}

struct ClassDetailLine: View {
    var attributes: ClassStartAttributes
    var body: some View {
        let parts = [attributes.subject, attributes.room.isEmpty ? "" : "Room \(attributes.room)"].filter { !$0.isEmpty }
        if !parts.isEmpty {
            Text(parts.joined(separator: " · ")).lineLimit(1)
        }
    }
}

struct DueTodayList: View {
    var state: ClassStartAttributes.ContentState
    var compact: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 2 : 4) {
            Label("Due today", systemImage: "checklist")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.orange)
            ForEach(state.dueToday.prefix(compact ? 2 : 3), id: \.self) { item in
                HStack(spacing: 6) {
                    Circle().frame(width: 4, height: 4).foregroundStyle(.secondary)
                    Text(item.title).lineLimit(1)
                    if !item.className.isEmpty && !compact {
                        Text(item.className).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                .font(.caption)
            }
            let shown = min(state.dueToday.count, compact ? 2 : 3)
            if state.dueCount > shown {
                Text("+\(state.dueCount - shown) more").font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct ClassStartLockScreenView: View {
    let context: ActivityViewContext<ClassStartAttributes>

    var body: some View {
        let state = context.state
        let tint = Color(hex: context.attributes.colorHex)
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 14) {
                CountdownRing(state: state, tint: tint, size: 64, showsTime: true)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Class starts soon")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(tint)
                    Text(context.attributes.className)
                        .font(.title3.bold())
                        .lineLimit(1)
                    ClassDetailLine(attributes: context.attributes)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text("Starts").font(.caption2).foregroundStyle(.secondary)
                    Text(state.startDate, style: .time).font(.headline)
                }
            }
            if state.dueCount > 0 {
                Divider()
                DueTodayList(state: state, compact: false)
            }
        }
        .padding(16)
    }
}

#Preview("Lock Screen", as: .content, using: ClassStartAttributes(classId: "c1", className: "AP Calculus", subject: "Math", room: "204", colorHex: "#6366f1")) {
    ClassStartLiveActivity()
} contentStates: {
    ClassStartAttributes.ContentState(
        startsAt: Date().addingTimeInterval(4 * 60).timeIntervalSince1970,
        dueToday: [.init(title: "Problem Set 4", className: "AP Calculus"), .init(title: "Essay Draft", className: "English Literature")],
        dueCount: 3
    )
    ClassStartAttributes.ContentState(startsAt: Date().addingTimeInterval(150).timeIntervalSince1970, dueToday: [], dueCount: 0)
}

#Preview("Island expanded", as: .dynamicIsland(.expanded), using: ClassStartAttributes(classId: "c1", className: "AP Calculus", subject: "Math", room: "204", colorHex: "#6366f1")) {
    ClassStartLiveActivity()
} contentStates: {
    ClassStartAttributes.ContentState(startsAt: Date().addingTimeInterval(4 * 60).timeIntervalSince1970,
                                      dueToday: [.init(title: "Problem Set 4", className: "AP Calculus")], dueCount: 1)
}

#Preview("Island compact", as: .dynamicIsland(.compact), using: ClassStartAttributes(classId: "c1", className: "AP Calculus", subject: "Math", room: "204", colorHex: "#6366f1")) {
    ClassStartLiveActivity()
} contentStates: {
    ClassStartAttributes.ContentState(startsAt: Date().addingTimeInterval(4 * 60).timeIntervalSince1970, dueToday: [], dueCount: 0)
}
