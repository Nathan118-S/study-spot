import SwiftUI

// Admin → account → Simulate on iPhone: sends real pushes (notifications and
// Live Activities) to the person's registered iPhones. Works on your own
// account too, for testing on your phone.

struct AdminSimOptions {
    struct Item: Identifiable {
        var id: String
        var name: String
        var detail: String?
    }

    var apns = false
    var notificationDevices = 0
    var classDevices = 0
    var timerDevices = 0
    var classes: [Item] = []
    var assignments: [Item] = []

    init() {}

    init(_ json: JSONValue) {
        apns = json["apns"]?.boolValue ?? false
        notificationDevices = Int(json["devices"]?["notifications"]?.doubleValue ?? 0)
        classDevices = Int(json["devices"]?["class_activity"]?.doubleValue ?? 0)
        timerDevices = Int(json["devices"]?["timer_activity"]?.doubleValue ?? 0)
        classes = (json["classes"]?.arrayValue ?? []).map { c in
            Item(id: c["id"]?.stringValue ?? "", name: c["name"]?.stringValue ?? "Class", detail: c["subject"]?.stringValue)
        }
        assignments = (json["assignments"]?.arrayValue ?? []).map { a in
            Item(id: a["id"]?.stringValue ?? "", name: a["title"]?.stringValue ?? "Assignment", detail: a["class_name"]?.stringValue)
        }
    }
}

struct AdminSimResult: Identifiable {
    let id = UUID()
    var title: String
    var summary: String
    var ok: Bool
    var failures: [String] = []
    var date = Date()

    init(title: String, error: String) {
        self.title = title
        summary = error
        ok = false
    }

    init(title: String, response res: JSONValue) {
        self.title = title
        let devices = Int(res["devices"]?.doubleValue ?? 0)
        let delivered = Int(res["delivered"]?.doubleValue ?? 0)
        var parts: [String] = []
        if let ended = res["ended"]?.doubleValue {
            parts.append(ended == 0 ? "No class Live Activities were reported open" : "Ended \(Int(ended)) Live Activit\(ended == 1 ? "y" : "ies")")
        } else if devices == 0 {
            parts.append("No iPhone registered")
        } else {
            parts.append("Delivered to \(delivered) of \(devices) iPhone\(devices == 1 ? "" : "s")")
        }
        if res["inApp"]?.boolValue == true { parts.append("added to the bell") }
        if let s = res["startsAt"]?.stringValue.flatMap(ISO8601.date(from:)) {
            parts.append("class starts \(s.formatted(date: .omitted, time: .shortened))")
        }
        if let e = res["endsAt"]?.stringValue.flatMap(ISO8601.date(from:)) {
            parts.append("ends \(e.formatted(date: .omitted, time: .shortened))")
        }
        summary = parts.joined(separator: " · ")
        let inApp = res["inApp"]?.boolValue == true
        let endCall = res["ended"] != nil
        ok = devices > 0 ? (delivered == devices) : (inApp || endCall)
        failures = (res["results"]?.arrayValue ?? []).filter { $0["ok"]?.boolValue != true }.map { r in
            let device = r["device"]?.stringValue ?? "device"
            let env = r["environment"]?.stringValue.map { " (\($0))" } ?? ""
            let status = r["status"]?.stringValue.map { "\($0) " } ?? ""
            return "\(device)\(env): \(status)\(r["reason"]?.stringValue ?? "failed")"
        }
    }
}

typealias AdminSimSend = (_ title: String, _ function: String, _ payload: [String: JSONValue]) async -> Void

struct AdminSimulateView: View {
    var model: AdminAccountModel
    @State private var options = AdminSimOptions()
    @State private var loading = true
    @State private var busy = false
    @State private var results: [AdminSimResult] = []

    var body: some View {
        Form {
            AdminSimStatusSection(options: options, loading: loading)
            if !results.isEmpty { AdminSimResultsSection(results: results) }
            AdminSimNotificationSection(options: options, busy: busy, send: send)
            AdminSimClassSection(options: options, busy: busy, send: send)
            AdminSimTimerSection(options: options, busy: busy, send: send)
        }
        .appBackground()
        .navigationTitle("Simulate on iPhone")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        do {
            let res = try await model.call("adminSimulateOptions")
            options = AdminSimOptions(res)
        } catch is AdminUnlock.Cancelled {
        } catch {
            results.insert(AdminSimResult(title: "Couldn't load", error: error.localizedDescription), at: 0)
        }
    }

    private func send(_ title: String, _ function: String, _ payload: [String: JSONValue]) async {
        busy = true
        defer { busy = false }
        do {
            let res = try await model.call(function, payload)
            results.insert(AdminSimResult(title: title, response: res), at: 0)
        } catch is AdminUnlock.Cancelled {
            return
        } catch {
            results.insert(AdminSimResult(title: title, error: error.localizedDescription), at: 0)
        }
        if results.count > 6 { results.removeLast(results.count - 6) }
        // Dead tokens are dropped server-side, so refresh the device counts.
        if let res = (try? await model.call("adminSimulateOptions")) { options = AdminSimOptions(res) }
    }
}

private struct AdminSimStatusSection: View {
    var options: AdminSimOptions
    var loading: Bool

    var body: some View {
        Section {
            LabeledContent("Apple Push") {
                if loading {
                    ProgressView()
                } else {
                    Text(options.apns ? "Set up" : "Not set up").foregroundStyle(options.apns ? Color.green : Color.orange)
                }
            }
            LabeledContent("Notifications", value: devices(options.notificationDevices))
            LabeledContent("Class Live Activities", value: devices(options.classDevices))
            LabeledContent("Study timer Live Activities", value: devices(options.timerDevices))
        } header: {
            Text("Status")
        } footer: {
            if !loading && !options.apns {
                Text("Add the APNs key in Admin → Integration Secrets → Apple Push to send pushes.")
            } else {
                Text("An iPhone registers when the app is opened signed in to this account. Live Activities need iOS 17.2 or later.")
            }
        }
        .glassRows()
    }

    private func devices(_ n: Int) -> String { n == 0 ? "No iPhones" : "\(n) iPhone\(n == 1 ? "" : "s")" }
}

private struct AdminSimResultsSection: View {
    var results: [AdminSimResult]

    var body: some View {
        Section("Results") {
            ForEach(results) { r in
                VStack(alignment: .leading, spacing: 3) {
                    Label {
                        Text("\(r.title): \(r.summary)")
                    } icon: {
                        Image(systemName: r.ok ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                            .foregroundStyle(r.ok ? Color.green : Color.orange)
                    }
                    .font(.subheadline)
                    ForEach(r.failures, id: \.self) { f in
                        Text(f).font(.caption.monospaced()).foregroundStyle(.secondary)
                    }
                    Text(r.date.formatted(date: .omitted, time: .standard)).font(.caption2).foregroundStyle(.tertiary)
                }
            }
        }
        .glassRows()
    }
}

private struct AdminSimSendButton: View {
    var title: String
    var symbol: String
    var busy: Bool
    var action: () async -> Void

    var body: some View {
        Button {
            Task { await action() }
        } label: {
            HStack {
                Label(title, systemImage: symbol)
                Spacer()
                if busy { ProgressView() }
            }
        }
        .disabled(busy)
    }
}

// MARK: - Notification

private struct AdminSimNotificationSection: View {
    var options: AdminSimOptions
    var busy: Bool
    var send: AdminSimSend
    @State private var kind = "reminder"
    @State private var assignmentId = ""
    @State private var classId = ""
    @State private var streak = 7
    @State private var title = ""
    @State private var subtitle = ""
    @State private var message = ""
    @State private var url = ""
    @State private var inApp = true

    private var pushReady: Bool { options.apns && options.notificationDevices > 0 }
    private var canSend: Bool {
        (pushReady || inApp) && (kind != "custom" || !title.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    private var payload: [String: JSONValue] {
        var p: [String: JSONValue] = ["kind": .string(kind), "inApp": .bool(inApp)]
        switch kind {
        case "reminder":
            if !assignmentId.isEmpty { p["assignmentId"] = .string(assignmentId) }
        case "streak":
            if !classId.isEmpty { p["classId"] = .string(classId) }
            p["streak"] = .number(Double(streak))
        default:
            p["title"] = .string(title)
            p["subtitle"] = .string(subtitle)
            p["body"] = .string(message)
            if !url.isEmpty { p["url"] = .string(url) }
        }
        return p
    }

    var body: some View {
        Section {
            Picker("Type", selection: $kind) {
                Text("Assignment reminder").tag("reminder")
                Text("Streak record").tag("streak")
                Text("Custom").tag("custom")
            }
            fields
            Toggle("Also add to their notification bell", isOn: $inApp)
            AdminSimSendButton(title: "Send notification", symbol: "paperplane", busy: busy) {
                await send("Notification", "adminSimulateNotification", payload)
            }
            .disabled(!canSend)
        } header: {
            Text("Notification")
        } footer: {
            if !options.apns {
                Text("Apple Push isn't set up, so only the notification bell can be tested.")
            } else if options.notificationDevices == 0 {
                Text("No iPhone has turned on notifications for this account, so only the notification bell can be tested.")
            } else {
                Text("Arrives like a real reminder. Tapping it opens the app.")
            }
        }
        .glassRows()
    }

    @ViewBuilder private var fields: some View {
        switch kind {
        case "reminder":
            Picker("Assignment", selection: $assignmentId) {
                Text("Sample assignment").tag("")
                ForEach(options.assignments) { a in Text(a.name).tag(a.id) }
            }
        case "streak":
            Picker("Class", selection: $classId) {
                Text("Sample class").tag("")
                ForEach(options.classes) { c in Text(c.name).tag(c.id) }
            }
            Stepper("Streak: \(streak)", value: $streak, in: 2...999)
        default:
            TextField("Title", text: $title)
            TextField("Subtitle (optional)", text: $subtitle)
            TextField("Message", text: $message, axis: .vertical).lineLimit(2...5)
            TextField("Link (optional), e.g. studyspot://tab/streaks", text: $url)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
        }
    }
}

// MARK: - Live Activities

private struct AdminSimClassSection: View {
    var options: AdminSimOptions
    var busy: Bool
    var send: AdminSimSend
    @State private var classId = ""
    @State private var minutes = 10

    var body: some View {
        Section {
            Picker("Class", selection: $classId) {
                Text("Sample class").tag("")
                ForEach(options.classes) { c in Text(c.name).tag(c.id) }
            }
            Stepper("Starts in \(minutes) min", value: $minutes, in: 1...60)
            AdminSimSendButton(title: "Start Live Activity", symbol: "play.circle", busy: busy) {
                var p: [String: JSONValue] = ["kind": "class_start", "action": "start", "minutes": .number(Double(minutes))]
                if !classId.isEmpty { p["classId"] = .string(classId) }
                await send("Class Live Activity", "adminSimulateLiveActivity", p)
            }
            .disabled(!options.apns || options.classDevices == 0)
            AdminSimSendButton(title: "End class Live Activities", symbol: "stop.circle", busy: busy) {
                await send("End class Live Activities", "adminSimulateLiveActivity", ["kind": "class_start", "action": "end"])
            }
            .disabled(!options.apns)
        } header: {
            Text("Class Live Activity")
        } footer: {
            if !options.apns {
                Text("Needs Apple Push to be set up.")
            } else if options.classDevices == 0 {
                Text("No iPhone has registered for class Live Activities yet (iOS 17.2+, app opened signed in, class Live Activities on).")
            } else {
                Text("Shows the class countdown on the Lock Screen and in the Dynamic Island. End only reaches activities their iPhone reported back, so the sample class ends on its own.")
            }
        }
        .glassRows()
    }
}

private struct AdminSimTimerSection: View {
    var options: AdminSimOptions
    var busy: Bool
    var send: AdminSimSend
    @State private var phase = "focus"
    @State private var minutes = 25
    @State private var assignmentId = ""

    var body: some View {
        Section {
            Picker("Phase", selection: $phase) {
                Text("Focus").tag("focus")
                Text("Short break").tag("short")
                Text("Long break").tag("long")
            }
            .onChange(of: phase) { _, p in minutes = p == "focus" ? 25 : (p == "short" ? 5 : 15) }
            Stepper("\(minutes) min", value: $minutes, in: 1...120)
            Picker("Assignment", selection: $assignmentId) {
                Text("None").tag("")
                ForEach(options.assignments) { a in Text(a.name).tag(a.id) }
            }
            AdminSimSendButton(title: "Start timer Live Activity", symbol: "timer", busy: busy) {
                var p: [String: JSONValue] = [
                    "kind": "study_timer", "action": "start", "phase": .string(phase), "minutes": .number(Double(minutes)),
                ]
                if !assignmentId.isEmpty { p["assignmentId"] = .string(assignmentId) }
                await send("Study timer", "adminSimulateLiveActivity", p)
            }
            .disabled(!options.apns || options.timerDevices == 0)
        } header: {
            Text("Study timer Live Activity")
        } footer: {
            if !options.apns {
                Text("Needs Apple Push to be set up.")
            } else if options.timerDevices == 0 {
                Text("No iPhone has registered for timer Live Activities yet (iOS 17.2+ and the latest app opened signed in).")
            } else {
                Text("Shows a running timer on the Lock Screen and in the Dynamic Island. It isn't connected to the app's own timer.")
            }
        }
        .glassRows()
    }
}
