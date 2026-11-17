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
                                    let details = [c.subject, c.room.map { $0.isEmpty ? "" : "Room \($0)" }, c.teacherName]
                                        .compactMap { $0 }.filter { !$0.isEmpty }
                                    if !details.isEmpty {
                                        Text(details.joined(separator: " · ")).font(.subheadline).foregroundStyle(.secondary)
                                    }
                                    if let next = c.schedule.compactMap({ $0.nextStart(after: Date()) }).min() {
                                        Label(next.formatted(.dateTime.weekday(.abbreviated).hour().minute()), systemImage: "clock")
                                            .font(.caption).foregroundStyle(.secondary)
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
                            Button { editing = c } label: { Label("Edit", systemImage: "pencil") }.tint(Palette.secondary)
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
    @State private var subject = ""
    @State private var room = ""
    @State private var meetings: [EditableMeeting] = []
    @State private var saving = false

    /// A meeting time being edited (times as Dates for the pickers).
    struct EditableMeeting: Identifiable {
        let id = UUID()
        var day: Int
        var start: Date
        var end: Date
    }

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
                Section("Details (optional)") {
                    TextField("Subject, e.g. Math", text: $subject)
                    TextField("Room, e.g. 204", text: $room)
                    TextField("Teacher, e.g. Mr. Smith", text: $teacher)
                }
                Section {
                    ForEach($meetings) { $m in
                        VStack(alignment: .leading, spacing: 8) {
                            Picker("Day", selection: $m.day) {
                                ForEach(0..<7, id: \.self) { Text(ClassMeeting.weekdayNames[$0]).tag($0) }
                            }
                            HStack {
                                DatePicker("Starts", selection: $m.start, displayedComponents: .hourAndMinute)
                                    .labelsHidden()
                                Text("to").foregroundStyle(.secondary)
                                DatePicker("Ends", selection: $m.end, displayedComponents: .hourAndMinute)
                                    .labelsHidden()
                                Spacer(minLength: 0)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .onDelete { meetings.remove(atOffsets: $0) }
                    Button {
                        // Same time as the last meeting, on the next day.
                        let last = meetings.last
                        meetings.append(EditableMeeting(
                            day: last.map { ($0.day + 1) % 7 } ?? 1,
                            start: last?.start ?? ReminderSettings.time("09:00"),
                            end: last?.end ?? ReminderSettings.time("09:50")
                        ))
                    } label: {
                        Label("Add meeting time", systemImage: "plus.circle.fill")
                    }
                } header: {
                    Text("Meeting times")
                } footer: {
                    Text("Study Spot shows a Live Activity with a countdown 5 minutes before each class.")
                }
            }
            .navigationTitle(schoolClass == nil ? "Add Class" : "Edit Class")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.tint(Palette.secondary) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Saving..." : "Save") { Task { await save() } }
                        .disabled(saving || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear {
                if let c = schoolClass {
                    name = c.name; color = c.color; teacher = c.teacherName ?? ""
                    subject = c.subject ?? ""; room = c.room ?? ""
                    meetings = c.schedule.map {
                        EditableMeeting(day: $0.day, start: ReminderSettings.time($0.start), end: ReminderSettings.time($0.end))
                    }
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
            "subject": .string(subject.trimmingCharacters(in: .whitespaces)),
            "room": .string(room.trimmingCharacters(in: .whitespaces)),
            "schedule": .array(meetings.sorted { ($0.day, ReminderSettings.hhmm($0.start)) < ($1.day, ReminderSettings.hhmm($1.start)) }.map {
                ["day": .number(Double($0.day)), "start": .string(ReminderSettings.hhmm($0.start)), "end": .string(ReminderSettings.hhmm($0.end))]
            }),
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
