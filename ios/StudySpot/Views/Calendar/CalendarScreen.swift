import SwiftUI

/// Month grid of assignments on their due dates. Drag an item onto another
/// day to reschedule it (keeps the original time of day).
struct CalendarScreen: View {
    @Environment(DataStore.self) private var data
    @State private var cursor = Date()
    @State private var selectedDay = Calendar.current.startOfDay(for: Date())
    @State private var dropTarget: Date?
    @State private var detail: Assignment?
    @State private var editing: Assignment?

    private let cal = Calendar.current
    private let weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]

    private var days: [Date] {
        var sundayCal = cal
        sundayCal.firstWeekday = 1
        guard let month = sundayCal.dateInterval(of: .month, for: cursor),
              let firstWeek = sundayCal.dateInterval(of: .weekOfMonth, for: month.start),
              let lastWeek = sundayCal.dateInterval(of: .weekOfMonth, for: month.end.addingTimeInterval(-1)) else { return [] }
        var out: [Date] = []
        var d = firstWeek.start
        while d < lastWeek.end {
            out.append(d)
            d = sundayCal.date(byAdding: .day, value: 1, to: d)!
        }
        return out
    }

    private var byDay: [Date: [Assignment]] {
        Dictionary(grouping: data.assignments.filter { $0.dueDate != nil }) { cal.startOfDay(for: $0.dueDate!) }
    }

    var body: some View {
        let grouped = byDay
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Button { shiftMonth(-1) } label: { Image(systemName: "chevron.left") }
                    Spacer()
                    Text(cursor.formatted(.dateTime.month(.wide).year())).font(.headline)
                    Spacer()
                    Button { shiftMonth(1) } label: { Image(systemName: "chevron.right") }
                }
                .buttonStyle(.bordered)

                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 7), spacing: 2) {
                    ForEach(weekdays, id: \.self) { Text($0).font(.caption2.weight(.semibold)).foregroundStyle(.secondary) }
                    ForEach(days, id: \.self) { day in
                        dayCell(day, items: grouped[day] ?? [])
                    }
                }

                Text("Tip: long-press an assignment and drag it to another day to reschedule.")
                    .font(.caption).foregroundStyle(.secondary)

                VStack(alignment: .leading, spacing: 8) {
                    Text(selectedDay.formatted(.dateTime.weekday(.wide).month(.wide).day()))
                        .font(.headline)
                    let items = (grouped[selectedDay] ?? []).sorted { ($0.dueDate ?? .distantPast) < ($1.dueDate ?? .distantPast) }
                    if items.isEmpty {
                        Text("Nothing due.").foregroundStyle(.secondary).font(.subheadline)
                    }
                    ForEach(items) { a in
                        Button { detail = a } label: {
                            HStack(spacing: 10) {
                                Circle().fill(a.priority.color).frame(width: 8, height: 8)
                                VStack(alignment: .leading) {
                                    Text(a.title).strikethrough(a.completed).lineLimit(1)
                                    Text("\(a.className ?? "Uncategorized") · \(a.dueDate?.formatted(date: .omitted, time: .shortened) ?? "")")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                            }
                            .padding(10)
                            .background(Color(hex: data.color(for: a.classId)).opacity(0.13), in: RoundedRectangle(cornerRadius: 8))
                        }
                        .foregroundStyle(.primary)
                        .draggable(a.id)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Calendar")
        .refreshable { await data.reloadData() }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Today") {
                    cursor = Date()
                    selectedDay = cal.startOfDay(for: Date())
                }
            }
        }
        .sheet(item: $detail) { a in
            AssignmentDetailView(assignmentId: a.id) { toEdit in
                detail = nil
                Task { try? await Task.sleep(for: .milliseconds(350)); editing = toEdit }
            }
        }
        .sheet(item: $editing) { AssignmentFormView(assignment: $0) }
    }

    private func dayCell(_ day: Date, items: [Assignment]) -> some View {
        let inMonth = cal.isDate(day, equalTo: cursor, toGranularity: .month)
        let isToday = cal.isDateInToday(day)
        let isSelected = day == selectedDay
        return VStack(alignment: .leading, spacing: 2) {
            Text(day.formatted(.dateTime.day()))
                .font(.caption2.weight(isToday ? .bold : .regular))
                .foregroundStyle(isToday ? Color.accentColor : .secondary)
            ForEach(items.prefix(3)) { a in
                HStack(spacing: 2) {
                    Circle().fill(a.priority.color).frame(width: 4, height: 4)
                    Text(a.title).font(.system(size: 9)).lineLimit(1)
                }
                .padding(.horizontal, 2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(hex: data.color(for: a.classId)).opacity(0.18), in: RoundedRectangle(cornerRadius: 3))
                .draggable(a.id) {
                    Text(a.title).font(.caption).padding(6).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 6))
                }
            }
            if items.count > 3 {
                Text("+\(items.count - 3) more").font(.system(size: 9)).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(3)
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(dropTarget == day ? Color.accentColor.opacity(0.18) : Color(.secondarySystemGroupedBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 6)
                .stroke(Color.accentColor, lineWidth: isToday || isSelected || dropTarget == day ? (isSelected ? 2 : 1) : 0)
        )
        .opacity(inMonth ? 1 : 0.4)
        .contentShape(Rectangle())
        .onTapGesture { selectedDay = day }
        .dropDestination(for: String.self) { ids, _ in
            guard let id = ids.first else { return false }
            Task { await data.reschedule(id, to: day) }
            selectedDay = day
            return true
        } isTargeted: { targeted in
            dropTarget = targeted ? day : (dropTarget == day ? nil : dropTarget)
        }
    }

    private func shiftMonth(_ delta: Int) {
        if let d = cal.date(byAdding: .month, value: delta, to: cursor) { cursor = d }
    }
}
