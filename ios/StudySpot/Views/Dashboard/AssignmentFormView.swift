import SwiftUI

struct AssignmentFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(ToastCenter.self) private var toasts

    var assignment: Assignment?

    @State private var title = ""
    @State private var classId = ""
    @State private var hasDueDate = true
    @State private var dueDate = Calendar.current.date(bySettingHour: 23, minute: 59, second: 0, of: Date()) ?? Date()
    @State private var priority: Priority = .medium
    @State private var type: AssignmentType = .homework
    @State private var points = ""
    @State private var notes = ""
    @State private var saving = false
    @State private var templates: [AssignmentTemplate] = []
    @State private var showTemplates = false
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Form {
                if assignment == nil {
                    Section {
                        Menu {
                            if templates.isEmpty {
                                Text("No templates yet — create one")
                            }
                            ForEach(templates) { t in
                                Button(t.name) { apply(t) }
                            }
                        } label: {
                            Label(templates.isEmpty ? "No templates yet" : "Apply a template…", systemImage: "square.on.square")
                        }
                        Button { showTemplates = true } label: { Label("Manage templates", systemImage: "slider.horizontal.3") }
                    } header: { Text("Use a template") }
                }

                Section("Assignment") {
                    TextField("e.g. Chapter 5 homework", text: $title)
                    Picker("Class / Course", selection: $classId) {
                        Text("Uncategorized").tag("")
                        ForEach(data.classes) { c in
                            HStack { ColorDot(hex: c.color); Text(c.name) }.tag(c.id)
                        }
                    }
                    Toggle("Due date", isOn: $hasDueDate)
                    if hasDueDate {
                        DatePicker("Due", selection: $dueDate)
                    }
                }

                Section {
                    Picker("Priority", selection: $priority) {
                        ForEach(Priority.allCases) { Text($0.label).tag($0) }
                    }
                    Picker("Type", selection: $type) {
                        ForEach(AssignmentType.allCases) { Text($0.label).tag($0) }
                    }
                    HStack {
                        Text("Points (max)")
                        TextField("0", text: $points).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                    }
                }

                Section("Notes") {
                    TextField("Additional notes...", text: $notes, axis: .vertical).lineLimit(3...8)
                }
            }
            .navigationTitle(assignment == nil ? "New Assignment" : "Edit Assignment")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Saving..." : "Save") { Task { await save() } }
                        .disabled(saving || title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .sheet(isPresented: $showTemplates, onDismiss: { Task { await loadTemplates() } }) {
                TemplateManagerView()
            }
            .task {
                guard !loaded else { return }
                loaded = true
                populate()
                await loadTemplates()
            }
        }
    }

    private func populate() {
        if let a = assignment {
            title = a.title
            classId = a.classId ?? ""
            hasDueDate = a.dueDate != nil
            if let d = a.dueDate { dueDate = d }
            priority = a.priority
            type = a.type
            points = a.points > 0 ? a.points.clean : ""
            notes = a.notes
        } else {
            classId = data.classes.first?.id ?? ""
        }
    }

    private func loadTemplates() async {
        guard auth.user?.isDemo != true else { return }
        if let t = (try? await APIClient.shared.templates()) { templates = t }
    }

    private func apply(_ t: AssignmentTemplate) {
        if !t.title.isEmpty { title = t.title }
        type = t.type
        priority = t.priority
        points = t.points > 0 ? t.points.clean : ""
        notes = t.notes
    }

    private func save() async {
        saving = true
        defer { saving = false }
        let cls = data.classes.first { $0.id == classId }
        var payload: [String: JSONValue] = [
            "title": .string(title.trimmingCharacters(in: .whitespaces)),
            "class_id": classId.isEmpty ? .null : .string(classId),
            "class_name": .string(cls?.name ?? "Uncategorized"),
            "due_date": hasDueDate ? .from(dueDate) : .null,
            "priority": .string(priority.rawValue),
            "type": .string(type.rawValue),
            "points": .number(Double(points.replacingOccurrences(of: ",", with: ".")) ?? 0),
            "notes": .string(notes),
        ]
        if auth.user?.isDemo == true {
            dismiss()
            return
        }
        do {
            if let a = assignment {
                let updated = try await APIClient.shared.updateAssignment(a.id, payload)
                data.apply(updated)
            } else {
                payload["source"] = "manual"
                payload["completed"] = false
                let _: Assignment = try await APIClient.shared.create("Assignment", payload)
                await data.reloadData()
            }
            dismiss()
        } catch {
            toasts.show("Couldn't save assignment", error.localizedDescription, error: true)
        }
    }
}
