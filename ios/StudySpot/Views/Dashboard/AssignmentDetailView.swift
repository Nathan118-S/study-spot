import SwiftUI

struct AssignmentDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data

    var assignmentId: String
    var onEdit: (Assignment) -> Void

    @State private var notes = ""
    @State private var score = ""
    @State private var progress: Double = 0
    @State private var newSubtask = ""
    @State private var saving = false
    @State private var confirmDelete = false
    @FocusState private var focus: Field?

    enum Field { case notes, score, subtask }

    private var assignment: Assignment? { data.assignments.first { $0.id == assignmentId } }
    private var isDemo: Bool { auth.user?.isDemo == true }

    var body: some View {
        NavigationStack {
            if let a = assignment {
                content(a)
            } else {
                ContentUnavailableView("Assignment not found", systemImage: "questionmark.folder")
            }
        }
        .onAppear {
            guard let a = assignment else { return }
            notes = a.notes
            score = a.score.map(\.clean) ?? ""
            progress = a.progress
        }
    }

    private func content(_ a: Assignment) -> some View {
        let scale = auth.user?.gradingScale ?? Grading.defaultScale
        let letter = Grading.letter(score: a.score, points: a.points, scale: scale)
        return Form {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 6) {
                        ColorDot(hex: data.color(for: a.classId), size: 9)
                        Text(a.className ?? "Uncategorized").font(.subheadline).foregroundStyle(.secondary)
                        if a.source != .manual { Pill(text: a.source.label) }
                    }
                    Text(a.title).font(.title2.bold()).strikethrough(a.completed)
                    if !a.description.isEmpty {
                        Text(a.description).font(.subheadline).foregroundStyle(.secondary)
                    }
                    HStack(spacing: 6) {
                        Pill(text: "\(a.priority.label) priority", color: a.priority.color)
                        Pill(text: a.type.label)
                        if a.points > 0 { Pill(text: "\(a.points.clean) pts") }
                        if a.isOverdue { Pill(text: "Overdue", color: .red, symbol: "exclamationmark.triangle.fill", filled: true) }
                    }
                    Label(DateText.long(a.dueDate), systemImage: "calendar.badge.clock")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }

            if a.source == .googleClassroom, a.externalId != nil, !isDemo {
                ClassroomAttachmentsSection(assignmentId: a.id)
            }

            Section("Notes") {
                TextField("Add notes about this assignment…", text: $notes, axis: .vertical)
                    .lineLimit(3...10)
                    .focused($focus, equals: .notes)
                    .onSubmit { persist(["notes": .string(notes)]) }
            }

            Section {
                HStack {
                    TextField("Add a subtask…", text: $newSubtask)
                        .focused($focus, equals: .subtask)
                        .onSubmit { addSubtask(a) }
                    Button { addSubtask(a) } label: { Image(systemName: "plus.circle.fill") }
                        .disabled(newSubtask.trimmingCharacters(in: .whitespaces).isEmpty || saving)
                }
                ForEach(Array(a.subtasks.enumerated()), id: \.offset) { i, s in
                    HStack {
                        Button { toggleSubtask(a, i) } label: {
                            Image(systemName: s.done ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(s.done ? Color.accentColor : .secondary)
                        }
                        .buttonStyle(.plain)
                        Text(s.text).strikethrough(s.done).foregroundStyle(s.done ? .secondary : .primary)
                    }
                }
                .onDelete { idx in
                    var list = a.subtasks
                    list.remove(atOffsets: idx)
                    persistSubtasks(a, list)
                }
            } header: {
                HStack {
                    Text("Subtasks")
                    Spacer()
                    if !a.subtasks.isEmpty { Text("\(a.subtasks.filter(\.done).count)/\(a.subtasks.count) done") }
                }
            }

            Section {
                HStack {
                    Text("Progress")
                    Spacer()
                    Text("\(Int(progress))%").fontWeight(.semibold).foregroundStyle(progress >= 100 ? .green : .primary)
                }
                if a.subtasks.isEmpty {
                    Slider(value: $progress, in: 0...100, step: 5, onEditingChanged: { editing in
                        if !editing { persist(["progress": .number(progress)]) }
                    })
                } else {
                    Text("Progress is calculated from completed subtasks.").font(.caption).foregroundStyle(.secondary)
                    ProgressBar(value: progress)
                }
            }

            if a.points > 0 {
                Section {
                    HStack {
                        TextField("Score", text: $score)
                            .keyboardType(.decimalPad)
                            .focused($focus, equals: .score)
                            .frame(maxWidth: 90)
                        Text("/ \(a.points.clean) pts" + (a.score.map { " · \(Int(($0 / a.points * 100).rounded()))%" } ?? ""))
                            .foregroundStyle(.secondary)
                        Spacer()
                        if let letter {
                            Text(letter).font(.title3.bold())
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .foregroundStyle(GradeStyle.color(letter))
                                .background(GradeStyle.color(letter).opacity(0.15), in: RoundedRectangle(cornerRadius: 8))
                        }
                    }
                } header: { Text("Grade") }
            }

            Section {
                Toggle("Mark as complete", isOn: Binding(get: { a.completed }, set: { _ in toggleComplete(a) }))
                    .disabled(saving)
            }

            Section {
                Button { onEdit(a) } label: { Label("Edit assignment", systemImage: "pencil") }
                Button(role: .destructive) { confirmDelete = true } label: { Label("Delete assignment", systemImage: "trash") }
            }
        }
        .navigationTitle("Assignment")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("Done") { commitFields(a); dismiss() } }
            ToolbarItem(placement: .keyboard) {
                HStack { Spacer(); Button("Done") { commitFields(a); focus = nil } }
            }
        }
        .onChange(of: focus) { old, _ in
            if old == .notes || old == .score { commitFields(a) }
        }
        .confirmationDialog("Delete this assignment?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                Task { await data.delete(a) }
                dismiss()
            }
        }
    }

    // MARK: Persistence

    private func commitFields(_ a: Assignment) {
        var patch: [String: JSONValue] = [:]
        if notes != a.notes { patch["notes"] = .string(notes) }
        let trimmed = score.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        let newScore: Double? = trimmed.isEmpty ? nil : Double(trimmed)
        if (trimmed.isEmpty || newScore != nil) && newScore != a.score {
            patch["score"] = .from(newScore)
        }
        if !patch.isEmpty { persist(patch) }
    }

    private func persist(_ patch: [String: JSONValue]) {
        guard var a = assignment else { return }
        if let n = patch["notes"]?.stringValue { a.notes = n }
        if let p = patch["progress"]?.doubleValue { a.progress = p }
        if patch.keys.contains("score") { a.score = patch["score"]?.doubleValue }
        if let c = patch["completed"]?.boolValue { a.completed = c }
        if let subs = patch["subtasks"]?.arrayValue {
            a.subtasks = subs.compactMap { s in
                guard let t = s["text"]?.stringValue else { return nil }
                return Subtask(text: t, done: s["done"]?.boolValue ?? false)
            }
        }
        data.apply(a)
        guard !isDemo else { return }
        saving = true
        Task {
            defer { saving = false }
            if let updated = (try? await APIClient.shared.updateAssignment(a.id, patch)) { data.apply(updated) }
        }
    }

    private func computeProgress(_ list: [Subtask]) -> Double {
        list.isEmpty ? 0 : (Double(list.filter(\.done).count) / Double(list.count) * 100).rounded()
    }

    private func subtasksJSON(_ list: [Subtask]) -> JSONValue {
        .array(list.map { ["text": .string($0.text), "done": .bool($0.done)] })
    }

    private func persistSubtasks(_ a: Assignment, _ list: [Subtask]) {
        let p = computeProgress(list)
        progress = p
        persist(["subtasks": subtasksJSON(list), "progress": .number(p)])
    }

    private func addSubtask(_ a: Assignment) {
        let text = newSubtask.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        newSubtask = ""
        persistSubtasks(a, a.subtasks + [Subtask(text: text, done: false)])
    }

    private func toggleSubtask(_ a: Assignment, _ i: Int) {
        var list = a.subtasks
        list[i].done.toggle()
        persistSubtasks(a, list)
    }

    private func toggleComplete(_ a: Assignment) {
        let completed = !a.completed
        var patch: [String: JSONValue] = ["completed": .bool(completed)]
        if !a.subtasks.isEmpty {
            let list = a.subtasks.map { Subtask(text: $0.text, done: completed) }
            let p = computeProgress(list)
            patch["subtasks"] = subtasksJSON(list)
            patch["progress"] = .number(p)
            progress = p
        } else {
            let p = completed ? 100 : (a.progress >= 100 ? 0 : a.progress)
            patch["progress"] = .number(p)
            progress = p
        }
        persist(patch)
        if completed, a.source == .googleClassroom, a.externalId != nil, auth.user?.syncCompletionToClassroom != false, !isDemo {
            Task { _ = try? await APIClient.shared.invoke("syncCompletionToClassroom", ["assignment_id": .string(a.id)]) }
        }
    }
}

struct ClassroomAttachmentsSection: View {
    var assignmentId: String
    @State private var attachments: [ClassroomAttachment]?
    @State private var error = ""

    var body: some View {
        Group {
            if attachments == nil && error.isEmpty {
                Section { HStack { ProgressView(); Text("Loading attachments…").foregroundStyle(.secondary) } }
            } else if !error.isEmpty {
                Section("Attached files") { Text(error).font(.caption).foregroundStyle(.secondary) }
            } else if let attachments, !attachments.isEmpty {
                Section("Attached files") {
                    ForEach(attachments, id: \.url) { att in
                        if let url = URL(string: att.url) {
                            Link(destination: url) {
                                Label {
                                    HStack { Text(att.title).lineLimit(1); Spacer(); Image(systemName: "arrow.up.right.square") }
                                } icon: {
                                    Image(systemName: icon(att.kind))
                                }
                            }
                        }
                    }
                }
            }
        }
        .task(id: assignmentId) {
            do {
                let res = try await APIClient.shared.invoke("getClassroomAttachments", ["assignment_id": .string(assignmentId)])
                let list = res["attachments"]?.arrayValue ?? []
                attachments = list.compactMap { v in
                    guard let url = v["url"]?.stringValue else { return nil }
                    return ClassroomAttachment(title: v["title"]?.stringValue ?? url, url: url, kind: v["kind"]?.stringValue ?? "link")
                }
            } catch {
                self.error = error.localizedDescription.isEmpty ? "Unable to load attachments" : error.localizedDescription
            }
        }
    }

    private func icon(_ kind: String) -> String {
        switch kind {
        case "drive": "doc.text"
        case "video": "play.rectangle"
        case "form": "list.clipboard"
        default: "link"
        }
    }
}
