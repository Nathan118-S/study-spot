import SwiftUI

enum DashboardTab: String, CaseIterable, Identifiable {
    case unfinished = "Unfinished", completed = "Completed", graded = "Graded"
    var id: String { rawValue }
}

enum DueFilter: String, CaseIterable, Identifiable {
    case all = "All dates", overdue = "Overdue", today = "Due today", week = "This week"
    var id: String { rawValue }
}

enum SortOrder: String, CaseIterable, Identifiable {
    case soonest = "Soonest due", priority = "Highest priority", className = "Class name"
    var id: String { rawValue }
}

struct DashboardView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(Router.self) private var router

    @State private var tab: DashboardTab = .unfinished
    @State private var classFilter: String = "all"
    @State private var dueFilter: DueFilter = .all
    @State private var priorityFilter: Priority?
    @State private var typeFilter: AssignmentType?
    @State private var sort: SortOrder = .soonest
    @State private var selectMode = false
    @State private var selected: Set<String> = []
    @State private var showTimer = false
    @State private var editing: Assignment?
    @State private var creating = false
    @State private var detail: Assignment?

    private var filtersActive: Bool {
        classFilter != "all" || dueFilter != .all || priorityFilter != nil || typeFilter != nil
    }

    private var filtered: [Assignment] {
        var list = data.assignments
        switch tab {
        case .unfinished: list = list.filter { !$0.completed && !$0.isGraded }
        case .completed: list = list.filter { $0.completed && !$0.isGraded }
        case .graded: break
        }
        if classFilter != "all" { list = list.filter { $0.classId == classFilter } }
        if let p = priorityFilter { list = list.filter { $0.priority == p } }
        if let t = typeFilter { list = list.filter { $0.type == t } }
        let cal = Calendar.current
        switch dueFilter {
        case .all: break
        case .overdue: list = list.filter(\.isOverdue)
        case .today: list = list.filter { $0.dueDate.map(cal.isDateInToday) ?? false }
        case .week:
            var mondayCal = cal
            mondayCal.firstWeekday = 2
            list = list.filter { a in a.dueDate.map { mondayCal.isDate($0, equalTo: Date(), toGranularity: .weekOfYear) } ?? false }
        }
        switch sort {
        case .soonest: list.sort { ($0.dueDate ?? .distantFuture) < ($1.dueDate ?? .distantFuture) }
        case .priority: list.sort { $0.priority.rank > $1.priority.rank }
        case .className: list.sort { ($0.className ?? "").localizedCompare($1.className ?? "") == .orderedAscending }
        }
        return list
    }

    var body: some View {
        let items = filtered
        List {
            Section {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    StatCard(label: "Total", value: "\(data.assignments.count)")
                    StatCard(label: "Overdue", value: "\(data.assignments.filter(\.isOverdue).count)", tint: .red)
                    StatCard(label: "Due Today", value: "\(data.assignments.filter { $0.dueDate.map(Calendar.current.isDateInToday) ?? false }.count)", tint: .orange)
                    StatCard(label: "Completed", value: "\(data.assignments.filter(\.completed).count)", tint: .green)
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)

                if !data.syncMessage.isEmpty {
                    Text(data.syncMessage).font(.footnote).foregroundStyle(.secondary)
                        .listRowInsets(EdgeInsets(top: 4, leading: 4, bottom: 0, trailing: 4))
                        .listRowBackground(Color.clear)
                }
            }

            if showTimer {
                Section { StudyTimerView(assignments: data.assignments) }.glassRows()
            }

            Section {
                Picker("View", selection: $tab) {
                    ForEach(DashboardTab.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .onChange(of: tab) { _, _ in selected = [] }
            }

            if tab == .graded {
                GradedListView(assignments: data.assignments, scale: auth.user?.gradingScale ?? Grading.defaultScale) { detail = $0 }
            } else {
                if selectMode && !items.isEmpty {
                    let all = items.allSatisfy { selected.contains($0.id) }
                    Button {
                        selected = all ? [] : Set(items.map(\.id))
                    } label: {
                        Label(all ? "All selected" : "Select all on this tab", systemImage: all ? "checkmark.circle.fill" : "circle")
                    }
                }
                Section {
                    if data.isLoading && !data.hasLoaded {
                        ProgressView().frame(maxWidth: .infinity).padding()
                    } else if items.isEmpty {
                        ContentUnavailableView("No assignments match your filters.", systemImage: "tray")
                    } else {
                        ForEach(items) { a in
                            AssignmentRow(
                                assignment: a,
                                color: data.color(for: a.classId),
                                selectMode: selectMode,
                                isSelected: selected.contains(a.id),
                                onToggleComplete: { Task { await data.toggleComplete(a, user: auth.user) } },
                                onToggleSelect: { toggleSelect(a.id) }
                            )
                            .contentShape(Rectangle())
                            .onTapGesture { if selectMode { toggleSelect(a.id) } else { detail = a } }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) { Task { await data.delete(a) } } label: { Label("Delete", systemImage: "trash") }
                                Button { editing = a } label: { Label("Edit", systemImage: "pencil") }.tint(Palette.secondary)
                            }
                            .swipeActions(edge: .leading) {
                                Button { Task { await data.toggleComplete(a, user: auth.user) } } label: {
                                    Label(a.completed ? "Undo" : "Done", systemImage: a.completed ? "arrow.uturn.backward" : "checkmark")
                                }
                                .tint(.green)
                            }
                        }
                    }
                }
                .glassRows()
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Dashboard")
        .refreshable {
            if data.connections.any { await data.runSync(silent: false, auth: auth) } else { await data.reloadData() }
        }
        .toolbar {
            ToolbarItemGroup(placement: .topBarLeading) {
                Button { withAnimation { showTimer.toggle() } } label: {
                    Image(systemName: showTimer ? "timer.circle.fill" : "timer")
                        .foregroundStyle(Palette.secondary)
                }
                .accessibilityLabel("Study timer")
                if data.connections.any {
                    Button { Task { await data.runSync(silent: false, auth: auth) } } label: {
                        if data.isSyncing { ProgressView() } else { Image(systemName: "arrow.triangle.2.circlepath").foregroundStyle(Palette.secondary) }
                    }
                    .disabled(data.isSyncing)
                    .accessibilityLabel("Sync now")
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                if tab != .graded { filterMenu }
                Button { creating = true } label: { Image(systemName: "plus") }
                    .accessibilityLabel("Add assignment")
            }
        }
        .safeAreaInset(edge: .bottom) {
            if selectMode && !selected.isEmpty { bulkBar }
        }
        // Deep links from widgets and the Live Activity.
        .task(id: router.pendingAssignmentId) {
            guard let id = router.pendingAssignmentId else { return }
            if let a = data.assignments.first(where: { $0.id == id }) {
                detail = a
                router.pendingAssignmentId = nil
            }
        }
        .onChange(of: data.assignments) { _, list in
            if let id = router.pendingAssignmentId, let a = list.first(where: { $0.id == id }) {
                detail = a
                router.pendingAssignmentId = nil
            }
        }
        .task(id: router.showTimer) {
            if router.showTimer {
                withAnimation { showTimer = true }
                router.showTimer = false
            }
        }
        .sheet(isPresented: $creating) { AssignmentFormView(assignment: nil) }
        .sheet(item: $editing) { AssignmentFormView(assignment: $0) }
        .sheet(item: $detail) { a in
            AssignmentDetailView(assignmentId: a.id) { toEdit in
                detail = nil
                Task { try? await Task.sleep(for: .milliseconds(350)); editing = toEdit }
            }
        }
    }

    private var filterMenu: some View {
        Menu {
            Picker("Class", selection: $classFilter) {
                Text("All classes").tag("all")
                ForEach(data.classes) { Text($0.name).tag($0.id) }
            }
            Picker("Due", selection: $dueFilter) {
                ForEach(DueFilter.allCases) { Text($0.rawValue).tag($0) }
            }
            Picker("Priority", selection: $priorityFilter) {
                Text("All priorities").tag(Priority?.none)
                ForEach(Priority.allCases.reversed()) { Text($0.label).tag(Priority?.some($0)) }
            }
            Picker("Type", selection: $typeFilter) {
                Text("All types").tag(AssignmentType?.none)
                ForEach(AssignmentType.allCases) { Text($0.label).tag(AssignmentType?.some($0)) }
            }
            Divider()
            Picker("Sort", selection: $sort) {
                ForEach(SortOrder.allCases) { Text($0.rawValue).tag($0) }
            }
            Divider()
            Button {
                selectMode.toggle(); selected = []
            } label: {
                Label(selectMode ? "Done selecting" : "Select", systemImage: "checkmark.circle")
            }
            if filtersActive {
                Button("Clear filters", role: .destructive) {
                    classFilter = "all"; dueFilter = .all; priorityFilter = nil; typeFilter = nil
                }
            }
        } label: {
            Image(systemName: filtersActive ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                .foregroundStyle(Palette.secondary)
        }
        .accessibilityLabel("Filter and sort")
    }

    private var bulkBar: some View {
        HStack(spacing: 10) {
            Text("\(selected.count) selected").font(.subheadline.weight(.medium))
            Spacer()
            Menu("Priority") {
                ForEach(Priority.allCases.reversed()) { p in
                    Button(p.label) { bulk(["priority": .string(p.rawValue)]) }
                }
            }
            Button("Complete") { bulk(["completed": true]) }
            Button("Incomplete") { bulk(["completed": false]) }
            Button { selected = [] } label: { Image(systemName: "xmark") }
        }
        .glassButtonStyle()
        .controlSize(.small)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .glassCapsule()
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }

    private func bulk(_ patch: [String: JSONValue]) {
        let ids = selected
        selected = []
        Task { await data.bulkApply(ids: ids, patch: patch) }
    }

    private func toggleSelect(_ id: String) {
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
    }
}

struct AssignmentRow: View {
    var assignment: Assignment
    var color: String
    var selectMode = false
    var isSelected = false
    var onToggleComplete: () -> Void
    var onToggleSelect: () -> Void = {}

    var body: some View {
        let a = assignment
        HStack(spacing: 12) {
            if selectMode {
                Button(action: onToggleSelect) {
                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                        .font(.title3).foregroundStyle(isSelected ? Color.accentColor : .secondary)
                }
                .buttonStyle(.plain)
            }
            RoundedRectangle(cornerRadius: 2).fill(Color(hex: color)).frame(width: 4)
            Button(action: onToggleComplete) {
                Image(systemName: a.completed ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(a.completed ? Color.accentColor : .secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(a.completed ? "Mark incomplete" : "Mark complete")

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(a.title)
                        .font(.body.weight(.medium))
                        .strikethrough(a.completed)
                        .foregroundStyle(a.completed ? .secondary : .primary)
                        .lineLimit(2)
                    if a.source != .manual { Pill(text: a.source.label) }
                    if a.isOverdue { Pill(text: "Overdue", color: .red, symbol: "exclamationmark.triangle.fill", filled: true) }
                }
                HStack(spacing: 4) {
                    ColorDot(hex: color, size: 7)
                    Text(a.className ?? "Uncategorized")
                    Text("·")
                    Text(a.type.label)
                    Text("·")
                    Text(DateText.short(a.dueDate)).foregroundStyle(a.isOverdue ? .red : .secondary)
                    Spacer(minLength: 0)
                    if a.progress > 0 {
                        Text(a.progress >= 100 ? "✓" : "\(Int(a.progress))%").fontWeight(.medium)
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            }
        }
        .padding(.vertical, 2)
        .listRowBackground(
            ZStack {
                Rectangle().fill(.thinMaterial)
                if a.isOverdue { Color.red.opacity(0.08) }
            }
        )
    }
}

struct GradedListView: View {
    var assignments: [Assignment]
    var scale: [GradeEntry]
    var onSelect: (Assignment) -> Void

    var body: some View {
        let graded = assignments.filter(\.isGraded).sorted {
            ($0.score ?? 0) / $0.points > ($1.score ?? 0) / $1.points
        }
        Section {
            if graded.isEmpty {
                ContentUnavailableView("No graded assignments yet", systemImage: "rosette",
                                       description: Text("Open an assignment with points and enter a score to see its letter grade here."))
            } else {
                ForEach(graded) { a in
                    let letter = Grading.letter(score: a.score, points: a.points, scale: scale)
                    Button { onSelect(a) } label: {
                        HStack(spacing: 12) {
                            Text(letter ?? "–")
                                .font(.title3.bold())
                                .frame(width: 44, height: 36)
                                .foregroundStyle(GradeStyle.color(letter))
                                .background(GradeStyle.color(letter).opacity(0.15), in: RoundedRectangle(cornerRadius: 8))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(a.title).font(.body.weight(.medium)).strikethrough(a.completed).lineLimit(1)
                                Text("\(a.className ?? "Uncategorized") · \(a.dueDate?.formatted(.dateTime.month(.abbreviated).day()) ?? "No due date")")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("\((a.score ?? 0).clean)/\(a.points.clean)").font(.subheadline.weight(.semibold))
                                Text("\(Grading.percent(a) ?? 0)%").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .foregroundStyle(.primary)
                }
            }
        }
        .glassRows()
    }
}
