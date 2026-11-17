import SwiftUI

// An account's settings and data, for the admin account manager.

extension AdminAccountModel {
    /// Calls a gated admin function for this user (adds `userId`), unlocking
    /// first if needed. Throws `AdminUnlock.Cancelled` if the unlock is dismissed.
    func call(_ name: String, _ payload: [String: JSONValue] = [:]) async throws -> JSONValue {
        var body = payload
        body["userId"] = .string(userId)
        let request = body
        return try await AdminUnlock.shared.run {
            try await APIClient.shared.invoke(name, request)
        }
    }
}

private extension JSONValue {
    func setting(_ key: String, _ value: JSONValue) -> JSONValue {
        var object = objectValue ?? [:]
        object[key] = value
        return .object(object)
    }
}

// MARK: - Settings

struct AdminUserSettingsSection: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var user: User
    @State private var leadText = ""
    @State private var timezone = ""

    private var savedLeads: String {
        let data = user.data["reminder_lead_hours"]
        let hours = data?.arrayValue?.compactMap(\.doubleValue) ?? data?.doubleValue.map { [$0] } ?? [24]
        return hours.map(\.clean).joined(separator: ", ")
    }

    var body: some View {
        Group {
            Section {
                toggle("onboarding_completed", "Onboarding completed", default: false)
                Picker("Welcome message", selection: stringBinding("welcome_style", default: "large")) {
                    Text("Large").tag("large")
                    Text("Small").tag("small")
                    Text("Off").tag("off")
                }
                toggle("leaderboard_enabled", "Leaderboard", default: false)
                toggle("sync_completion_to_classroom", "Sync completion to Classroom", default: true)
                toggle("class_live_activities", "Class Live Activities", default: true)
                timezoneRow
                NavigationLink {
                    AdminGradingScaleEditor(model: model)
                } label: {
                    LabeledContent("Grading scale", value: user.gradingScale.map(\.letter).joined(separator: " "))
                }
            } header: {
                Text("Settings")
            }
            Section {
                toggle("reminders_enabled", "Assignment reminders", default: true)
                toggle("push_reminders", "iPhone notifications", default: true)
                leadRow
                toggle("dnd_enabled", "Do not disturb", default: false)
                DatePicker("Quiet from", selection: timeBinding("dnd_start", default: "22:00"), displayedComponents: .hourAndMinute)
                DatePicker("Quiet until", selection: timeBinding("dnd_end", default: "07:00"), displayedComponents: .hourAndMinute)
            } header: {
                Text("Reminders")
            } footer: {
                Text("Changes are saved right away.")
            }
        }
        .glassRows()
        .onAppear(perform: reset)
        .onChange(of: user) { _, _ in reset() }
    }

    private var timezoneRow: some View {
        HStack {
            Text("Time zone")
            TextField("America/New_York", text: $timezone)
                .multilineTextAlignment(.trailing)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onSubmit(saveTimezone)
            if timezone != (user.data["timezone"]?.stringValue ?? "") {
                Button("Save", action: saveTimezone).buttonStyle(.borderless)
            }
        }
    }

    private var leadRow: some View {
        HStack {
            Text("Hours before")
            TextField("24, 6", text: $leadText)
                .keyboardType(.numbersAndPunctuation)
                .multilineTextAlignment(.trailing)
                .onSubmit(saveLeads)
            if leadText != savedLeads {
                Button("Save", action: saveLeads).buttonStyle(.borderless)
            }
        }
    }

    private func reset() {
        leadText = savedLeads
        timezone = user.data["timezone"]?.stringValue ?? ""
    }

    private func save(_ settings: [String: JSONValue]) {
        Task { await model.updateSettings(settings, toasts: toasts) }
    }

    private func saveTimezone() {
        let tz = timezone.trimmingCharacters(in: .whitespaces)
        guard !tz.isEmpty else { return }
        if TimeZone(identifier: tz) == nil {
            toasts.show("Unknown time zone", "Use an identifier like America/New_York.", error: true)
            return
        }
        save(["timezone": .string(tz)])
    }

    private func saveLeads() {
        let parts = leadText.split(whereSeparator: { $0 == "," || $0 == " " })
        let hours = Array(Set(parts.compactMap { Double($0) }.filter { $0 > 0 })).sorted()
        save(["reminder_lead_hours": .array(hours.map { .number($0) })])
    }

    private func toggle(_ key: String, _ title: String, default def: Bool) -> some View {
        Toggle(title, isOn: Binding(
            get: { model.user?.data[key]?.boolValue ?? def },
            set: { save([key: .bool($0)]) }
        ))
    }

    private func stringBinding(_ key: String, default def: String) -> Binding<String> {
        Binding(
            get: { model.user?.data[key]?.stringValue ?? def },
            set: { save([key: .string($0)]) }
        )
    }

    private func timeBinding(_ key: String, default def: String) -> Binding<Date> {
        Binding(
            get: { ReminderSettings.time(model.user?.data[key]?.stringValue ?? def) },
            set: { save([key: .string(ReminderSettings.hhmm($0))]) }
        )
    }
}

struct AdminGradingScaleEditor: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    @State private var rows: [GradeEntry] = []

    var body: some View {
        Form {
            Section {
                ForEach(rows.indices, id: \.self) { i in
                    HStack {
                        TextField("Letter", text: Binding(
                            get: { rows[safe: i]?.letter ?? "" },
                            set: { if rows.indices.contains(i) { rows[i].letter = String($0.prefix(3)) } }
                        ))
                        .frame(width: 70)
                        Text("≥").foregroundStyle(.secondary)
                        TextField("0", value: Binding(
                            get: { rows[safe: i]?.min ?? 0 },
                            set: { if rows.indices.contains(i) { rows[i].min = $0 } }
                        ), format: .number)
                        .keyboardType(.decimalPad)
                        .frame(width: 70)
                        Text("%").foregroundStyle(.secondary)
                    }
                }
                .onDelete { rows.remove(atOffsets: $0) }
                Button { rows.append(GradeEntry(letter: "", min: 0)) } label: { Label("Add row", systemImage: "plus") }
            } footer: {
                Text("The minimum percentage for each letter grade.")
            }
            .glassRows()
            Section {
                Button("Reset to default") { rows = Grading.defaultScale }
            }
            .glassRows()
        }
        .appBackground()
        .navigationTitle("Grading scale")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { Task { await save() } }.disabled(model.busy)
            }
        }
        .onAppear { if rows.isEmpty { rows = model.user?.gradingScale ?? Grading.defaultScale } }
    }

    private func save() async {
        let sorted = rows.filter { !$0.letter.trimmingCharacters(in: .whitespaces).isEmpty }.sorted { $0.min > $1.min }
        let scale: [JSONValue] = sorted.map { ["letter": .string($0.letter), "min": .number($0.min)] }
        await model.updateSettings(["grading_scale": .array(scale)], toasts: toasts)
        dismiss()
    }
}

// MARK: - Data

enum AdminRecordKind: String, CaseIterable, Identifiable {
    case assignments
    case classes
    case templates = "assignment_templates"
    case studySessions = "study_sessions"
    case notifications

    var id: String { rawValue }

    var title: String {
        switch self {
        case .assignments: "Assignments"
        case .classes: "Classes"
        case .templates: "Templates"
        case .studySessions: "Study sessions"
        case .notifications: "Notifications"
        }
    }

    var symbol: String {
        switch self {
        case .assignments: "checklist"
        case .classes: "book"
        case .templates: "doc.on.doc"
        case .studySessions: "timer"
        case .notifications: "bell"
        }
    }

    /// The columns the server lets an admin change.
    var fields: [AdminField] {
        let types = AssignmentType.allCases.map(\.rawValue)
        let priorities = Priority.allCases.map(\.rawValue)
        switch self {
        case .assignments:
            return [
                AdminField("title", "Title", .text),
                AdminField("class_id", "Class", .classRef),
                AdminField("class_name", "Class name", .text),
                AdminField("due_date", "Due date", .date),
                AdminField("priority", "Priority", .choice(priorities)),
                AdminField("type", "Type", .choice(types)),
                AdminField("completed", "Completed", .bool),
                AdminField("progress", "Progress (%)", .number),
                AdminField("points", "Points", .number),
                AdminField("score", "Score", .number),
                AdminField("notes", "Notes", .multiline),
                AdminField("description", "Description", .multiline),
                AdminField("subtasks", "Subtasks", .subtasks),
            ]
        case .classes:
            return [
                AdminField("name", "Name", .text),
                AdminField("color", "Color", .color),
                AdminField("teacher_name", "Teacher", .text),
                AdminField("best_streak", "Best streak", .number),
            ]
        case .templates:
            return [
                AdminField("name", "Name", .text),
                AdminField("title", "Assignment title", .text),
                AdminField("type", "Type", .choice(types)),
                AdminField("priority", "Priority", .choice(priorities)),
                AdminField("points", "Points", .number),
                AdminField("notes", "Notes", .multiline),
            ]
        case .studySessions:
            return [
                AdminField("duration_minutes", "Minutes", .number),
                AdminField("session_type", "Type", .choice(["focus", "break"])),
                AdminField("note", "Note", .multiline),
            ]
        case .notifications:
            return [
                AdminField("title", "Title", .text),
                AdminField("content", "Message", .multiline),
                AdminField("read", "Read", .bool),
            ]
        }
    }

    @MainActor
    func summary(_ model: AdminAccountModel) -> String {
        switch self {
        case .assignments:
            "\(model.stat("assignments")) · \(model.stat("completed")) done · \(model.stat("overdue")) overdue"
        case .classes: "\(model.stat("classes"))"
        case .templates: "\(model.stat("templates"))"
        case .studySessions: "\(model.stat("study_sessions")) · \(model.stat("study_minutes")) min focus"
        case .notifications: "\(model.stat("notifications")) · \(model.stat("unread_notifications")) unread"
        }
    }
}

struct AdminField: Identifiable {
    enum Kind {
        case text, multiline, number, bool, date, color, classRef, subtasks
        case choice([String])
    }

    var key: String
    var label: String
    var kind: Kind
    var id: String { key }

    init(_ key: String, _ label: String, _ kind: Kind) {
        self.key = key
        self.label = label
        self.kind = kind
    }
}

struct AdminRecord: Identifiable {
    var id: String
    var values: [String: JSONValue]

    init(_ json: JSONValue) {
        values = json.objectValue ?? [:]
        id = values["id"]?.stringValue ?? UUID().uuidString
    }

    func string(_ key: String) -> String? {
        guard let s = values[key]?.stringValue, !s.isEmpty else { return nil }
        return s
    }

    func date(_ key: String) -> Date? { values[key]?.stringValue.flatMap(ISO8601.date(from:)) }

    func title(_ kind: AdminRecordKind) -> String {
        switch kind {
        case .assignments, .notifications: return string("title") ?? "Untitled"
        case .classes, .templates: return string("name") ?? "Untitled"
        case .studySessions:
            let minutes = values["duration_minutes"]?.doubleValue ?? 0
            return "\(minutes.clean) min \(string("session_type") ?? "focus")"
        }
    }

    func subtitle(_ kind: AdminRecordKind) -> String {
        var parts: [String] = []
        switch kind {
        case .assignments:
            if let c = string("class_name") { parts.append(c) }
            parts.append(DateText.short(date("due_date")))
            if values["completed"]?.boolValue == true { parts.append("Done") }
        case .classes:
            if let t = string("teacher_name") { parts.append(t) }
            parts.append("Best streak \(Int(values["best_streak"]?.doubleValue ?? 0))")
        case .templates:
            if let t = string("title") { parts.append(t) }
            if let t = string("type") { parts.append(t.capitalized) }
        case .studySessions:
            if let d = date("created_at") { parts.append(d.formatted(date: .abbreviated, time: .shortened)) }
            if let c = string("class_name") { parts.append(c) }
            if let n = string("note") { parts.append(n) }
        case .notifications:
            if let c = string("content") { parts.append(c) }
            if let d = date("created_at") { parts.append(d.formatted(date: .abbreviated, time: .shortened)) }
        }
        return parts.joined(separator: " · ")
    }
}

struct AdminDataSection: View {
    var model: AdminAccountModel

    var body: some View {
        Section {
            ForEach(AdminRecordKind.allCases) { kind in
                NavigationLink {
                    AdminRecordListView(model: model, kind: kind)
                } label: {
                    LabeledContent {
                        Text(kind.summary(model))
                    } label: {
                        Label(kind.title, systemImage: kind.symbol)
                    }
                }
            }
        } header: {
            Text("Data")
        }
        .glassRows()
    }
}

struct AdminRecordListView: View {
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var kind: AdminRecordKind
    @State private var records: [AdminRecord] = []
    @State private var loading = true
    @State private var search = ""
    @State private var confirmClear = false

    private var filtered: [AdminRecord] {
        guard !search.isEmpty else { return records }
        return records.filter {
            $0.title(kind).localizedCaseInsensitiveContains(search) || $0.subtitle(kind).localizedCaseInsensitiveContains(search)
        }
    }

    var body: some View {
        List {
            if loading && records.isEmpty {
                ProgressView().frame(maxWidth: .infinity)
            } else if records.isEmpty {
                Text("Nothing here.").foregroundStyle(.secondary)
            }
            ForEach(filtered) { record in
                NavigationLink {
                    AdminRecordEditor(model: model, kind: kind, record: record,
                                      onSaved: { replace($0) }, onDeleted: { remove(record.id) })
                } label: {
                    AdminRecordRow(kind: kind, record: record)
                }
                .swipeActions {
                    Button("Delete", role: .destructive) { Task { await delete(record) } }
                }
            }
            .glassRows()
        }
        .appBackground()
        .navigationTitle(kind.title)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $search)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Delete all", role: .destructive) { confirmClear = true }
                    .disabled(records.isEmpty)
            }
        }
        .confirmationDialog("Delete all \(kind.title.lowercased())?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Delete all \(records.count)", role: .destructive) { Task { await clear() } }
        } message: {
            Text("This permanently deletes every one of this user's \(kind.title.lowercased()). This can't be undone.")
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        do {
            let res = try await model.call("adminListRecords", ["type": .string(kind.rawValue)])
            records = (res["records"]?.arrayValue ?? []).map(AdminRecord.init)
        } catch is AdminUnlock.Cancelled {
        } catch {
            toasts.show("Failed to load", error.localizedDescription, error: true)
        }
    }

    private func replace(_ record: AdminRecord) {
        if let i = records.firstIndex(where: { $0.id == record.id }) { records[i] = record }
    }

    private func remove(_ id: String) {
        records.removeAll { $0.id == id }
    }

    private func delete(_ record: AdminRecord) async {
        do {
            _ = try await model.call("adminDeleteRecord", ["type": .string(kind.rawValue), "id": .string(record.id)])
            remove(record.id)
            await model.load()
        } catch is AdminUnlock.Cancelled {
        } catch {
            toasts.show("Delete failed", error.localizedDescription, error: true)
        }
    }

    private func clear() async {
        do {
            let res = try await model.call("adminClearRecords", ["type": .string(kind.rawValue)])
            records = []
            toasts.show("Deleted", "\(Int(res["deleted"]?.doubleValue ?? 0)) \(kind.title.lowercased()) removed.")
            await model.load()
        } catch is AdminUnlock.Cancelled {
        } catch {
            toasts.show("Delete failed", error.localizedDescription, error: true)
        }
    }
}

private struct AdminRecordRow: View {
    var kind: AdminRecordKind
    var record: AdminRecord

    var body: some View {
        HStack(spacing: 10) {
            if kind == .classes {
                ColorDot(hex: record.string("color") ?? "#6366f1", size: 10)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(record.title(kind))
                    .fontWeight(kind == .notifications && record.values["read"]?.boolValue != true ? .semibold : .regular)
                    .lineLimit(1)
                let subtitle = record.subtitle(kind)
                if !subtitle.isEmpty {
                    Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
            }
        }
    }
}

// MARK: - Record editor

struct AdminRecordEditor: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    var model: AdminAccountModel
    var kind: AdminRecordKind
    var record: AdminRecord
    var onSaved: (AdminRecord) -> Void
    var onDeleted: () -> Void

    @State private var values: [String: JSONValue] = [:]
    @State private var classes: [AdminRecord] = []
    @State private var loaded = false
    @State private var saving = false
    @State private var confirmDelete = false

    private var changes: [String: JSONValue] {
        var out: [String: JSONValue] = [:]
        for field in kind.fields {
            let keys = field.key == "class_id" ? ["class_id", "class_name"] : [field.key]
            for key in keys where (values[key] ?? .null) != (record.values[key] ?? .null) {
                out[key] = values[key] ?? .null
            }
        }
        return out
    }

    var body: some View {
        Form {
            Section {
                ForEach(kind.fields.filter { !isSubtasks($0) }) { field in
                    AdminFieldEditor(field: field, values: $values, classes: classes)
                }
            }
            .glassRows()
            if kind == .assignments {
                Section("Subtasks") {
                    AdminSubtasksEditor(values: $values)
                }
                .glassRows()
            }
            Section {
                LabeledContent("ID", value: record.id).font(.caption)
                if let created = record.date("created_at") {
                    LabeledContent("Created", value: created.formatted(date: .abbreviated, time: .shortened)).font(.caption)
                }
                if let updated = record.date("updated_at") {
                    LabeledContent("Updated", value: updated.formatted(date: .abbreviated, time: .shortened)).font(.caption)
                }
            }
            .glassRows()
            Section {
                Button("Delete", role: .destructive) { confirmDelete = true }
            }
            .glassRows()
        }
        .appBackground()
        .navigationTitle(record.title(kind))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    Task { await save() }
                } label: {
                    if saving { ProgressView() } else { Text("Save").bold() }
                }
                .disabled(saving || changes.isEmpty)
            }
        }
        .confirmationDialog("Delete this record?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { Task { await delete() } }
        } message: {
            Text("This can't be undone.")
        }
        .onAppear {
            guard !loaded else { return }
            values = record.values
            loaded = true
        }
        .task { await loadClasses() }
    }

    private func isSubtasks(_ field: AdminField) -> Bool {
        if case .subtasks = field.kind { return true }
        return false
    }

    private func loadClasses() async {
        guard kind == .assignments, classes.isEmpty else { return }
        if let res = (try? await model.call("adminListRecords", ["type": "classes"])) {
            classes = (res["records"]?.arrayValue ?? []).map(AdminRecord.init)
        }
    }

    private func save() async {
        let payload = changes
        guard !payload.isEmpty else { return }
        saving = true
        defer { saving = false }
        do {
            let res = try await model.call("adminUpdateRecord", [
                "type": .string(kind.rawValue), "id": .string(record.id), "changes": .object(payload),
            ])
            if let updated = res["record"] { onSaved(AdminRecord(updated)) }
            toasts.show("Saved")
            dismiss()
            await model.load()
        } catch is AdminUnlock.Cancelled {
        } catch {
            toasts.show("Couldn't save", error.localizedDescription, error: true)
        }
    }

    private func delete() async {
        do {
            _ = try await model.call("adminDeleteRecord", ["type": .string(kind.rawValue), "id": .string(record.id)])
            onDeleted()
            toasts.show("Deleted")
            dismiss()
            await model.load()
        } catch is AdminUnlock.Cancelled {
        } catch {
            toasts.show("Delete failed", error.localizedDescription, error: true)
        }
    }
}

private struct AdminFieldEditor: View {
    var field: AdminField
    @Binding var values: [String: JSONValue]
    var classes: [AdminRecord]

    private var text: Binding<String> {
        let key = field.key
        return Binding(get: { values[key]?.stringValue ?? "" }, set: { values[key] = .string($0) })
    }

    private var number: Binding<String> {
        let key = field.key
        return Binding(
            get: { values[key]?.stringValue ?? "" },
            set: { s in
                let trimmed = s.trimmingCharacters(in: .whitespaces)
                if trimmed.isEmpty { values[key] = .null }
                else if let d = Double(trimmed) { values[key] = .number(d) }
                else { values[key] = .string(trimmed) }
            }
        )
    }

    private var flag: Binding<Bool> {
        let key = field.key
        return Binding(get: { values[key]?.boolValue ?? false }, set: { values[key] = .bool($0) })
    }

    var body: some View {
        switch field.kind {
        case .text:
            LabeledContent(field.label) {
                TextField(field.label, text: text).multilineTextAlignment(.trailing)
            }
        case .multiline:
            VStack(alignment: .leading, spacing: 4) {
                Text(field.label).font(.caption).foregroundStyle(.secondary)
                TextField(field.label, text: text, axis: .vertical).lineLimit(2...8)
            }
        case .number:
            LabeledContent(field.label) {
                TextField("None", text: number).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
            }
        case .bool:
            Toggle(field.label, isOn: flag)
        case .choice(let options):
            Picker(field.label, selection: text) {
                ForEach(options, id: \.self) { Text($0.capitalized).tag($0) }
            }
        case .color:
            LabeledContent(field.label) {
                HStack {
                    ColorDot(hex: values[field.key]?.stringValue ?? "#6366f1", size: 14)
                    TextField("#6366f1", text: text)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .multilineTextAlignment(.trailing)
                }
            }
        case .date:
            AdminDateFieldEditor(field: field, values: $values)
        case .classRef:
            Picker(field.label, selection: classBinding) {
                Text("None").tag("")
                ForEach(classes) { c in Text(c.string("name") ?? "Class").tag(c.id) }
            }
        case .subtasks:
            EmptyView()
        }
    }

    /// Picking a class sets both class_id and class_name.
    private var classBinding: Binding<String> {
        Binding(
            get: { values["class_id"]?.stringValue ?? "" },
            set: { id in
                if id.isEmpty {
                    values["class_id"] = .null
                } else {
                    values["class_id"] = .string(id)
                    values["class_name"] = .from(classes.first { $0.id == id }?.string("name"))
                }
            }
        )
    }
}

private struct AdminDateFieldEditor: View {
    var field: AdminField
    @Binding var values: [String: JSONValue]

    var body: some View {
        let key = field.key
        let has = Binding<Bool>(
            get: { (values[key] ?? .null) != .null },
            set: { on in values[key] = on ? .string(ISO8601.string(from: Date())) : .null }
        )
        let date = Binding<Date>(
            get: { values[key]?.stringValue.flatMap(ISO8601.date(from:)) ?? Date() },
            set: { values[key] = .string(ISO8601.string(from: $0)) }
        )
        Toggle(field.label, isOn: has)
        if has.wrappedValue {
            DatePicker(field.label, selection: date).labelsHidden()
        }
    }
}

private struct AdminSubtasksEditor: View {
    @Binding var values: [String: JSONValue]

    private var items: [JSONValue] { values["subtasks"]?.arrayValue ?? [] }

    var body: some View {
        ForEach(items.indices, id: \.self) { i in
            HStack {
                Button {
                    update(i, "done", .bool(!(items[safe: i]?["done"]?.boolValue ?? false)))
                } label: {
                    Image(systemName: items[safe: i]?["done"]?.boolValue == true ? "checkmark.circle.fill" : "circle")
                }
                .buttonStyle(.borderless)
                TextField("Subtask", text: Binding(
                    get: { items[safe: i]?["text"]?.stringValue ?? "" },
                    set: { update(i, "text", .string($0)) }
                ))
            }
        }
        .onDelete { offsets in
            var list = items
            list.remove(atOffsets: offsets)
            values["subtasks"] = .array(list)
        }
        Button {
            let blank: JSONValue = ["text": "", "done": false]
            values["subtasks"] = .array(items + [blank])
        } label: {
            Label("Add subtask", systemImage: "plus")
        }
    }

    private func update(_ i: Int, _ key: String, _ value: JSONValue) {
        var list = items
        guard list.indices.contains(i) else { return }
        list[i] = list[i].setting(key, value)
        values["subtasks"] = .array(list)
    }
}
