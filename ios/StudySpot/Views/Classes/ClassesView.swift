import SwiftUI

struct ClassesView: View {
    @Environment(DataStore.self) private var data
    @State private var editing: SchoolClass?
    @State private var creating = false
    @State private var confirmDelete: SchoolClass?

    var body: some View {
        List {
            if data.isLoading && !data.hasLoaded {
                ProgressView().frame(maxWidth: .infinity)
            } else if data.classes.isEmpty {
                ContentUnavailableView("No classes yet", systemImage: "book.closed",
                                       description: Text("Add one or connect Google Classroom in Settings."))
            } else {
                Section {
                    ForEach(data.classes) { c in
                        let count = data.assignments.filter { $0.classId == c.id }.count
                        Button { editing = c } label: {
                            HStack(spacing: 12) {
                                Circle().fill(Color(hex: c.color)).frame(width: 14, height: 14)
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack {
                                        Text(c.name).font(.body.weight(.semibold))
                                        if c.source != .manual {
                                            Pill(text: c.source == .blackboard ? "Blackboard" : "Google")
                                        }
                                    }
                                    if let t = c.teacherName, !t.isEmpty {
                                        Text("Teacher: \(t)").font(.subheadline).foregroundStyle(.secondary)
                                    }
                                    Text("\(count) assignment(s)").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                            }
                        }
                        .foregroundStyle(.primary)
                        .swipeActions {
                            Button(role: .destructive) { confirmDelete = c } label: { Label("Delete", systemImage: "trash") }
                            Button { editing = c } label: { Label("Edit", systemImage: "pencil") }.tint(.blue)
                        }
                    }
                } footer: {
                    Text("Google Classroom and Blackboard classes are imported automatically when connected.")
                }
                .glassRows()
            }
        }
        .navigationTitle("Classes")
        .refreshable { await data.reloadData() }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { creating = true } label: { Label("Add Class", systemImage: "plus") }
            }
        }
        .sheet(isPresented: $creating) { ClassFormView(schoolClass: nil) }
        .sheet(item: $editing) { ClassFormView(schoolClass: $0) }
        .confirmationDialog("Delete \(confirmDelete?.name ?? "class")?", isPresented: Binding(
            get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } }
        ), titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                if let c = confirmDelete { Task { await data.deleteClass(c) } }
            }
        }
    }
}

struct ClassFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(ToastCenter.self) private var toasts

    static let colors = ["#6366f1", "#ec4899", "#f59e0b", "#10b981", "#06b6d4", "#8b5cf6", "#ef4444", "#14b8a6", "#f97316", "#0ea5e9"]

    var schoolClass: SchoolClass?
    @State private var name = ""
    @State private var color = ClassFormView.colors[0]
    @State private var teacher = ""
    @State private var saving = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Class Name") {
                    TextField("e.g. AP Calculus", text: $name)
                }
                Section("Color Tag") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 5), spacing: 12) {
                        ForEach(Self.colors, id: \.self) { c in
                            Circle()
                                .fill(Color(hex: c))
                                .frame(width: 34, height: 34)
                                .overlay(Circle().stroke(Color.primary, lineWidth: color == c ? 3 : 0).padding(-3))
                                .onTapGesture { color = c }
                                .accessibilityLabel("Select color \(c)")
                                .accessibilityAddTraits(color == c ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 6)
                }
                Section("Teacher Name (optional)") {
                    TextField("e.g. Mr. Smith", text: $teacher)
                }
            }
            .navigationTitle(schoolClass == nil ? "Add Class" : "Edit Class")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Saving..." : "Save") { Task { await save() } }
                        .disabled(saving || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear {
                if let c = schoolClass {
                    name = c.name; color = c.color; teacher = c.teacherName ?? ""
                }
            }
        }
    }

    private func save() async {
        guard auth.user?.isDemo != true else { dismiss(); return }
        saving = true
        defer { saving = false }
        let payload: [String: JSONValue] = [
            "name": .string(name.trimmingCharacters(in: .whitespaces)),
            "color": .string(color),
            "teacher_name": .string(teacher.trimmingCharacters(in: .whitespaces)),
        ]
        do {
            if let c = schoolClass {
                let _: SchoolClass = try await APIClient.shared.update("Class", id: c.id, payload)
            } else {
                var p = payload
                p["source"] = "manual"
                let _: SchoolClass = try await APIClient.shared.create("Class", p)
            }
            await data.reloadData()
            dismiss()
        } catch {
            toasts.show("Couldn't save class", error.localizedDescription, error: true)
        }
    }
}
