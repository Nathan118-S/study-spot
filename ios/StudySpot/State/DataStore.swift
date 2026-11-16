import Foundation
import Observation

/// Assignments, classes, connections and notifications shared by every tab.
@MainActor
@Observable
final class DataStore {
    var assignments: [Assignment] = []
    var classes: [SchoolClass] = []
    var connections = Connections()
    var notifications: [AppNotification] = []
    var isLoading = false
    var hasLoaded = false
    var isSyncing = false
    var syncMessage = ""
    var lastError: String?

    private let api = APIClient.shared
    private var isDemo = false

    var unreadCount: Int { notifications.filter { !$0.read }.count }

    func color(for classId: String?) -> String {
        classes.first { $0.id == classId }?.color ?? "#94a3b8"
    }

    func reset() {
        assignments = []; classes = []; connections = Connections(); notifications = []
        hasLoaded = false; syncMessage = ""; lastError = nil
    }

    // MARK: Loading

    func load(for user: User?) async {
        isDemo = user?.isDemo == true
        if isDemo {
            assignments = DemoData.assignments
            classes = DemoData.classes
            connections = DemoData.connections
            hasLoaded = true
            return
        }
        if !hasLoaded { isLoading = true }
        defer { isLoading = false }
        do {
            async let a = api.assignments()
            async let c = api.classes()
            let (aa, cc) = try await (a, c)
            assignments = aa
            classes = cc
            hasLoaded = true
            lastError = nil
        } catch {
            lastError = error.localizedDescription
        }
        if let conn = (try? await api.connections()) { connections = conn }
    }

    func reloadData() async {
        guard !isDemo else { return }
        if let a = (try? await api.assignments()) { assignments = a }
        if let c = (try? await api.classes()) { classes = c }
    }

    func refreshConnections() async {
        if isDemo { connections = DemoData.connections; return }
        if let conn = (try? await api.connections()) { connections = conn }
    }

    /// Auto-refresh synced data if the last sync was more than 6 hours ago.
    func autoSyncIfStale(user: User, auth: AuthStore) async {
        guard !user.isDemo, connections.any else { return }
        if let last = user.lastSync, Date().timeIntervalSince(last) < 6 * 3600 { return }
        await runSync(silent: true, auth: auth)
    }

    func runSync(silent: Bool, auth: AuthStore) async {
        guard !isDemo else { return }
        isSyncing = true
        if !silent { syncMessage = "Syncing..." }
        defer { isSyncing = false }

        // Only connected services; the others would just fail.
        let services: [(label: String, function: String)] = [
            (connections.calendar, "Calendar", "syncGoogleCalendar"),
            (connections.classroom, "Classroom", "syncGoogleClassroom"),
            (connections.blackboard, "Blackboard", "syncBlackboard"),
        ].filter { $0.0 }.map { ($0.1, $0.2) }

        let results: [String] = await withTaskGroup(of: (Int, String).self) { group in
            for (i, service) in services.enumerated() {
                group.addTask {
                    do {
                        let r = try await APIClient.shared.invoke(service.function)
                        return (i, "\(service.label): \(Int(r["imported"]?.doubleValue ?? 0)) new")
                    } catch {
                        return (i, "\(service.label): \(error.localizedDescription)")
                    }
                }
            }
            var out = Array(repeating: "", count: services.count)
            for await (i, text) in group { out[i] = text }
            return out
        }
        if !silent { syncMessage = results.joined(separator: " · ") }
        guard !services.isEmpty else { await reloadData(); return }
        await reloadData()
        try? await auth.update(["last_sync": .string(ISO8601.string(from: Date()))])
    }

    // MARK: Assignment mutations (optimistic, like the web app)

    func apply(_ updated: Assignment) {
        if let i = assignments.firstIndex(where: { $0.id == updated.id }) { assignments[i] = updated }
    }

    func toggleComplete(_ a: Assignment, user: User?) async {
        let prev = assignments
        var next = a
        next.completed.toggle()
        apply(next)
        guard !isDemo else { return }
        do {
            try await api.updateAssignment(a.id, ["completed": .bool(next.completed)])
            if next.completed, a.source == .googleClassroom, a.externalId != nil, user?.syncCompletionToClassroom != false {
                Task { try? await APIClient.shared.invoke("syncCompletionToClassroom", ["assignment_id": .string(a.id)]) }
            }
        } catch {
            assignments = prev
        }
    }

    func delete(_ a: Assignment) async {
        let prev = assignments
        assignments.removeAll { $0.id == a.id }
        guard !isDemo else { return }
        do { try await api.delete("Assignment", id: a.id) } catch { assignments = prev }
    }

    func bulkApply(ids: Set<String>, patch: [String: JSONValue]) async {
        guard !ids.isEmpty else { return }
        let prev = assignments
        for i in assignments.indices where ids.contains(assignments[i].id) {
            if let p = patch["priority"]?.stringValue, let pr = Priority(rawValue: p) { assignments[i].priority = pr }
            if let c = patch["completed"]?.boolValue { assignments[i].completed = c }
        }
        guard !isDemo else { return }
        do {
            try await withThrowingTaskGroup(of: Void.self) { group in
                for id in ids {
                    group.addTask { _ = try await APIClient.shared.updateAssignment(id, patch) }
                }
                try await group.waitForAll()
            }
        } catch {
            assignments = prev
        }
    }

    func reschedule(_ id: String, to day: Date) async {
        guard let a = assignments.first(where: { $0.id == id }), let old = a.dueDate else { return }
        let cal = Calendar.current
        let t = cal.dateComponents([.hour, .minute, .second], from: old)
        guard let newDate = cal.date(bySettingHour: t.hour ?? 0, minute: t.minute ?? 0, second: t.second ?? 0, of: day) else { return }
        var next = a
        next.dueDate = newDate
        apply(next)
        guard !isDemo else { return }
        _ = try? await api.updateAssignment(id, ["due_date": .from(newDate)])
    }

    func deleteClass(_ c: SchoolClass) async {
        let prev = classes
        classes.removeAll { $0.id == c.id }
        guard !isDemo else { return }
        do { try await api.delete("Class", id: c.id) } catch { classes = prev }
    }

    // MARK: Notifications

    func loadNotifications() async {
        guard !isDemo else { return }
        if let list = (try? await api.notifications()) { notifications = list }
    }

    func markRead(_ n: AppNotification) async {
        guard !n.read, let i = notifications.firstIndex(where: { $0.id == n.id }) else { return }
        notifications[i].read = true
        let _: AppNotification? = try? await api.update("Notification", id: n.id, ["read": true])
    }

    func markAllRead() async {
        let unread = notifications.filter { !$0.read }
        for i in notifications.indices { notifications[i].read = true }
        for n in unread {
            let _: AppNotification? = try? await api.update("Notification", id: n.id, ["read": true])
        }
    }

    func clearNotifications() async {
        let all = notifications
        notifications = []
        for n in all { try? await api.delete("Notification", id: n.id) }
    }
}
