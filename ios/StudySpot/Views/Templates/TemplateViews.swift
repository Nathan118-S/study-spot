import SwiftUI

struct TemplateManagerView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    @State private var templates: [AssignmentTemplate] = []
    @State private var editing: AssignmentTemplate?
    @State private var creating = false
    @State private var confirmDelete: AssignmentTemplate?

    var body: some View {
        NavigationStack {
            List {
                if templates.isEmpty {
                    ContentUnavailableView("No templates yet", systemImage: "square.on.square",
                                           description: Text("Create one to prefill new assignments."))
                }
                ForEach(templates) { t in
                    Button { editing = t } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(t.name).font(.body.weight(.medium))
                            if !t.title.isEmpty { Text("Title: \(t.title)").font(.caption).foregroundStyle(.secondary) }
                            HStack {
                                Pill(text: t.type.label)
                                Pill(text: t.priority.rawValue, color: t.priority.color)
                                if t.points > 0 { Pill(text: "\(t.points.clean) pts") }
                            }
                        }
                    }
                    .foregroundStyle(.primary)
                    .swipeActions {
                        Button(role: .destructive) { confirmDelete = t } label: { Label("Delete", systemImage: "trash") }
                    }
                }
            }
            .navigationTitle("Templates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() }.tint(Palette.secondary) }
                ToolbarItem(placement: .primaryAction) {
                    Button { creating = true } label: { Label("New template", systemImage: "plus") }
                }
            }
            .sheet(isPresented: $creating, onDismiss: { Task { await load() } }) { TemplateFormView(template: nil) }
            .sheet(item: $editing, onDismiss: { Task { await load() } }) { TemplateFormView(template: $0) }
            .confirmationDialog("Delete \"\(confirmDelete?.name ?? "")\"?", isPresented: Binding(
                get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } }
            ), titleVisibility: .visible) {
                Button("Delete", role: .destructive) { if let t = confirmDelete { Task { await remove(t) } } }
            } message: {
                Text("This template will be removed. Existing assignments are unaffected.")
            }
            .task { await load() }
        }
    }

    private func load() async {
        guard auth.user?.isDemo != true else { return }
        if let t = (try? await APIClient.shared.templates()) { templates = t }
    }

    private func remove(_ t: AssignmentTemplate) async {
        do {
            try await APIClient.shared.delete("AssignmentTemplate", id: t.id)
            templates.removeAll { $0.id == t.id }
            toasts.show("Template deleted")
        } catch {
            toasts.show("Failed", error.localizedDescription, error: true)
        }
    }
}

struct TemplateFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    var template: AssignmentTemplate?

    @State private var name = ""
    @State private var title = ""
    @State private var priority: Priority = .medium
    @State private var type: AssignmentType = .homework
    @State private var points = ""
    @State private var notes = ""
    @State private var saving = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Template Name") { TextField("e.g. Chapter Reading", text: $name) }
                Section("Default Title") { TextField("Prefilled assignment title", text: $title) }
                Section {
                    Picker("Priority", selection: $priority) { ForEach(Priority.allCases) { Text($0.label).tag($0) } }
                    Picker("Type", selection: $type) { ForEach(AssignmentType.allCases) { Text($0.label).tag($0) } }
                    HStack {
                        Text("Points")
                        TextField("0", text: $points).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                    }
                }
                Section("Notes") { TextField("Default notes", text: $notes, axis: .vertical).lineLimit(3...6) }
            }
            .navigationTitle(template == nil ? "New Template" : "Edit Template")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.tint(Palette.secondary) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Saving..." : "Save") { Task { await save() } }
                        .disabled(saving || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear {
                if let t = template {
                    name = t.name; title = t.title; priority = t.priority; type = t.type
                    points = t.points > 0 ? t.points.clean : ""; notes = t.notes
                }
            }
        }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        let payload: [String: JSONValue] = [
            "name": .string(name.trimmingCharacters(in: .whitespaces)),
            "title": .string(title.trimmingCharacters(in: .whitespaces)),
            "priority": .string(priority.rawValue),
            "type": .string(type.rawValue),
            "points": .number(Double(points) ?? 0),
            "notes": .string(notes),
        ]
        do {
            if let t = template {
                let _: AssignmentTemplate = try await APIClient.shared.update("AssignmentTemplate", id: t.id, payload)
            } else {
                let _: AssignmentTemplate = try await APIClient.shared.create("AssignmentTemplate", payload)
            }
            dismiss()
        } catch {
            toasts.show("Couldn't save template", error.localizedDescription, error: true)
        }
    }
}
