import SwiftUI
import Observation

enum TimerPhase: String, CaseIterable, Identifiable {
    case focus, short, long
    var id: String { rawValue }
    var label: String {
        switch self { case .focus: "Focus"; case .short: "Short Break"; case .long: "Long Break" }
    }
    var symbol: String { self == .focus ? "brain.head.profile" : "cup.and.saucer" }
    var tint: Color {
        switch self { case .focus: .accentColor; case .short: .green; case .long: .cyan }
    }
}

/// Pomodoro timer driven by the wall clock so it survives backgrounding.
/// Completed focus phases are logged as StudySession records.
@MainActor
@Observable
final class StudyTimerModel {
    static let presets = [15, 25, 45, 50]

    var phase: TimerPhase = .focus
    var focusMinutes = 25
    var endsAt: Date?
    var secondsLeft: Double = 25 * 60
    var cycles = 0
    var assignmentId: String?
    var logging = false

    var running: Bool { endsAt != nil }

    func minutes(for p: TimerPhase) -> Int {
        switch p { case .focus: focusMinutes; case .short: 5; case .long: 15 }
    }

    var total: Double { Double(minutes(for: phase) * 60) }
    var progress: Double { total > 0 ? 1 - secondsLeft / total : 0 }

    var display: String {
        let s = Int(secondsLeft.rounded(.up))
        return String(format: "%02d:%02d", s / 60, s % 60)
    }

    func tick(assignments: [Assignment], isDemo: Bool, toasts: ToastCenter) {
        guard let endsAt else { return }
        secondsLeft = max(0, endsAt.timeIntervalSinceNow)
        if secondsLeft <= 0 {
            self.endsAt = nil
            Task { await completePhase(assignments: assignments, isDemo: isDemo, toasts: toasts) }
        }
    }

    func toggle(assignments: [Assignment]) {
        if running {
            if let endsAt { secondsLeft = max(0, endsAt.timeIntervalSinceNow) }
            endsAt = nil
            LocalNotifier.cancelTimer()
            updateLiveActivity(assignments: assignments)
        } else {
            let end = Date().addingTimeInterval(secondsLeft)
            endsAt = end
            Task { await LocalNotifier.requestPermission() }
            LocalNotifier.scheduleTimerEnd(
                at: end,
                title: phase == .focus ? "Focus session complete" : "Break over",
                body: phase == .focus ? "Nice work! Time for a break." : "Ready for the next focus session?"
            )
            updateLiveActivity(assignments: assignments)
        }
    }

    /// Mirrors the timer onto the Lock Screen / Dynamic Island.
    func updateLiveActivity(assignments: [Assignment]) {
        let a = assignments.first { $0.id == assignmentId }
        LiveActivityManager.sync(
            phase: phase.rawValue, phaseLabel: phase.label, endsAt: endsAt,
            remaining: secondsLeft, total: total,
            assignmentTitle: a?.title, className: a?.className
        )
    }

    func reset() {
        endsAt = nil
        LocalNotifier.cancelTimer()
        LiveActivityManager.end()
        secondsLeft = total
    }

    func switchPhase(_ p: TimerPhase) {
        endsAt = nil
        LocalNotifier.cancelTimer()
        LiveActivityManager.end()
        phase = p
        secondsLeft = Double(minutes(for: p) * 60)
    }

    func setFocus(_ m: Int) {
        focusMinutes = max(1, min(180, m))
        if phase == .focus && !running { secondsLeft = Double(focusMinutes * 60) }
    }

    private func completePhase(assignments: [Assignment], isDemo: Bool, toasts: ToastCenter) async {
        let finished = phase
        let mins = minutes(for: finished)
        let next: TimerPhase = finished == .focus ? (cycles % 4 == 3 ? .long : .short) : .focus
        LiveActivityManager.end(finalLabel: finished == .focus ? "Focus complete" : "Break over")
        if finished == .focus {
            let a = assignments.first { $0.id == assignmentId }
            if !isDemo {
                logging = true
                do {
                    let now = Date()
                    let _: JSONValue = try await APIClient.shared.create("StudySession", [
                        "assignment_id": .from(a?.id),
                        "assignment_title": .from(a?.title),
                        "class_id": .from(a?.classId),
                        "class_name": .from(a?.className),
                        "duration_minutes": .number(Double(mins)),
                        "session_type": "focus",
                        "started_at": .from(now.addingTimeInterval(Double(-mins * 60))),
                        "completed_at": .from(now),
                    ])
                    toasts.show("Focus session logged", "\(mins) min" + (a.map { " · \($0.title)" } ?? ""))
                } catch {
                    toasts.show("Could not log session", error.localizedDescription, error: true)
                }
                logging = false
            } else {
                toasts.show("Focus session complete", "\(mins) min")
            }
            cycles += 1
        } else {
            toasts.show("Break over", "Ready for the next focus session?")
        }
        phase = next
        secondsLeft = Double(minutes(for: next) * 60)
    }
}

struct StudyTimerView: View {
    @Environment(StudyTimerModel.self) private var model
    var assignments: [Assignment]

    @State private var customOpen = false
    @State private var customText = ""

    var body: some View {
        @Bindable var model = model
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label("Study Timer", systemImage: "timer").font(.headline)
                Pill(text: model.phase.label, color: model.phase.tint)
                Spacer()
                Text("\(model.cycles) session\(model.cycles == 1 ? "" : "s") today").font(.caption).foregroundStyle(.secondary)
            }

            HStack(spacing: 18) {
                ZStack {
                    Circle().stroke(Color(.tertiarySystemFill), lineWidth: 8)
                    Circle()
                        .trim(from: 0, to: model.progress)
                        .stroke(model.phase.tint, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .animation(.linear(duration: 0.25), value: model.progress)
                    VStack(spacing: 2) {
                        Image(systemName: model.phase.symbol).font(.caption).foregroundStyle(model.phase.tint)
                        Text(model.display).font(.title2.bold().monospacedDigit())
                    }
                }
                .frame(width: 112, height: 112)

                VStack(alignment: .leading, spacing: 10) {
                    Picker("Phase", selection: Binding(get: { model.phase }, set: { model.switchPhase($0) })) {
                        ForEach(TimerPhase.allCases) { Text($0 == .focus ? "Focus" : ($0 == .short ? "Short" : "Long")).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    if model.phase == .focus {
                        HStack(spacing: 6) {
                            ForEach(StudyTimerModel.presets, id: \.self) { m in
                                Button("\(m)m") { model.setFocus(m); customOpen = false }
                                    .buttonStyle(.bordered)
                                    .tint(model.focusMinutes == m ? .accentColor : .secondary)
                            }
                            Button("Other") { customOpen.toggle() }
                                .buttonStyle(.bordered)
                                .tint(StudyTimerModel.presets.contains(model.focusMinutes) ? .secondary : .accentColor)
                        }
                        .controlSize(.mini)
                        if customOpen {
                            HStack {
                                TextField("min", text: $customText).keyboardType(.numberPad).frame(width: 60)
                                    .textFieldStyle(.roundedBorder)
                                Button("Set") {
                                    model.setFocus(Int(customText) ?? 25)
                                    customOpen = false
                                }
                                .buttonStyle(.borderedProminent).controlSize(.small)
                            }
                        } else if !StudyTimerModel.presets.contains(model.focusMinutes) {
                            Text("Custom: \(model.focusMinutes) min").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Picker("Studying for…", selection: $model.assignmentId) {
                Text("General study (no assignment)").tag(String?.none)
                ForEach(assignments.filter { !$0.completed }.prefix(100)) { a in
                    Text(a.title).tag(String?.some(a.id))
                }
            }

            HStack {
                Button {
                    model.toggle(assignments: assignments)
                } label: {
                    Label(model.running ? "Pause" : "Start", systemImage: model.running ? "pause.fill" : "play.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(model.logging)
                Button { model.reset() } label: { Label("Reset", systemImage: "arrow.counterclockwise") }
                    .buttonStyle(.bordered)
                    .disabled(model.running || model.logging)
            }
        }
        .padding(.vertical, 6)
    }
}
