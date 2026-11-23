import Foundation
import Observation

/// Assignments, classes, connections and notifications shared by every tab.
///
/// Offline mode (see State/OfflineSync.swift): the last loaded data is cached
/// on disk per user and shown at launch before the network answers. Writes
/// that fail because the device is offline (never a 4xx/5xx response) stay
/// applied locally and are queued; the queue replays in order when the
/// connection comes back (NWPathMonitor) and when the app returns to the
/// foreground, then everything reloads. Conflicts are last write wins.
/// Demo accounts never touch the cache or the queue.
@MainActor
@Observable
final class DataStore {
    var assignments: [Assignment] = []
    var classes: [SchoolClass] = []
    var connections = Connections()
    /// Google Calendar links (secret iCal addresses), shown in Settings → Google.
    var icalFeeds: [ICalFeed] = []
    var notifications: [AppNotification] = []
    var isLoading = false
    var hasLoaded = false
    var isSyncing = false
    var syncMessage = ""
    var lastError: String?

    // Offline mode
    /// The last request couldn't reach the server, or the device has no network.
    var isOffline = false
    /// When the data on screen was last loaded from the server.
    var lastUpdated: Date?
    /// The data on screen came from the disk cache and hasn't been refreshed yet.
    var showingCachedData = false
    /// Set when queued changes were refused by the server; the UI shows it and clears it.
    var offlineSyncError: String?
    private var queue = OfflineQueue(userId: "")
    /// Changes made offline that haven't reached the server yet.
    var pendingChanges: Int { queue.ops.count }

    private let api = APIClient.shared
    private var isDemo = false
    private let network = NetworkMonitor()
    @ObservationIgnored private var currentUser: User?
    /// The user whose cache and queue are loaded into memory.
    @ObservationIgnored private var restoredFor: String?
    @ObservationIgnored private var replaying = false
    @ObservationIgnored private var saveTask: Task<Void, Never>?

    var unreadCount: Int { notifications.filter { !$0.read }.count }

    func color(for classId: String?) -> String {
        classes.first { $0.id == classId }?.color ?? "#94a3b8"
    }

    /// Sign-out: also forgets the offline cache and any unsent changes.
    func reset() {
        assignments = []; classes = []; connections = Connections(); notifications = []; icalFeeds = []
        hasLoaded = false; syncMessage = ""; lastError = nil
        saveTask?.cancel()
        queue = OfflineQueue(userId: "")
        currentUser = nil; restoredFor = nil
        isOffline = false; lastUpdated = nil; showingCachedData = false; offlineSyncError = nil
        OfflineStorage.clear()
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
        currentUser = user
        network.start { [weak self] online in self?.networkChanged(online: online) }
        // Show the cached copy at once, then send queued changes before re-fetching.
        if let user { restoreOfflineState(for: user) }
        if !queue.ops.isEmpty { await syncPendingChanges(reload: false) }
        if !hasLoaded { isLoading = true }
        defer { isLoading = false }
        do {
            try await fetchAssignmentsAndClasses()
            hasLoaded = true
            lastError = nil
        } catch {
            noteFailure(error)
            // Offline with a cached copy on screen: the offline banner says enough.
            if !(hasLoaded && Self.isOffline(error)) { lastError = error.localizedDescription }
        }
        if let conn = (try? await api.connections()) { connections = conn }
        scheduleCacheSave()
    }

    func reloadData() async {
        guard !isDemo else { return }
        do { try await fetchAssignmentsAndClasses() } catch { noteFailure(error) }
    }

    /// Both lists from the server, with still-queued offline changes laid on top.
    private func fetchAssignmentsAndClasses() async throws {
        async let a = api.assignments()
        async let c = api.classes()
        let (aa, cc) = try await (a, c)
        assignments = aa
        classes = cc
        overlayPending(["Assignment", "Class"])
        isOffline = false
        lastUpdated = Date()
        showingCachedData = false
        scheduleCacheSave()
    }

    func refreshConnections() async {
        if isDemo { connections = DemoData.connections; return }
        if let conn = (try? await api.connections()) { connections = conn }
    }

    func loadICalFeeds() async {
        if isDemo { icalFeeds = []; return }
        if let feeds = (try? await api.icalFeeds()) { icalFeeds = feeds }
    }

    /// Auto-refresh synced data if the last sync was more than 6 hours ago.
    func autoSyncIfStale(user: User, auth: AuthStore) async {
        guard !user.isDemo, connections.any, !isOffline else { return }
        if let last = user.lastSync, Date().timeIntervalSince(last) < 6 * 3600 { return }
        await runSync(silent: true, auth: auth)
    }

    func runSync(silent: Bool, auth: AuthStore) async {
        guard !isDemo else { return }
        isSyncing = true
        if !silent { syncMessage = "Syncing..." }
        defer { isSyncing = false }
        await syncPendingChanges(reload: false)

        // Only connected services; the others would just fail.
        let services: [(label: String, function: String)] = [
            (connections.calendar, "Calendar", "syncGoogleCalendar"),
            (connections.classroom, "Classroom", "syncGoogleClassroom"),
            (connections.blackboard, "Blackboard", "syncBlackboard"),
            (connections.ical, "Calendar links", "icalSyncFeeds"),
        ].filter { $0.0 }.map { ($0.1, $0.2) }

        let results: [String] = await withTaskGroup(of: (Int, String).self) { group in
            for (i, service) in services.enumerated() {
                group.addTask {
                    do {
                        let r = try await APIClient.shared.invoke(service.function)
                        var text = "\(service.label): \(Int(r["imported"]?.doubleValue ?? 0)) new"
                        let failed = r["errors"]?.arrayValue?.count ?? 0
                        if failed > 0 { text += ", \(failed) couldn't sync" }
                        return (i, text)
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
        if connections.ical { await loadICalFeeds() }
        try? await auth.update(["last_sync": .string(ISO8601.string(from: Date()))])
    }

    // MARK: Assignment mutations (optimistic, like the web app; queued while offline)

    func apply(_ updated: Assignment) {
        if let i = assignments.firstIndex(where: { $0.id == updated.id }) { assignments[i] = updated }
    }

    /// POST /api/entities/Assignment (the form). Offline, the assignment is added
    /// locally with a temporary id and queued. Throws the server's error otherwise.
    @discardableResult
    func createAssignment(_ fields: [String: JSONValue]) async throws -> Assignment? {
        guard !isDemo else { return nil }
        let op = PendingOp(kind: .create, entity: "Assignment", id: OfflineQueue.newTempId(), body: fields)
        let outcome = try await sendOrQueue(op, { try await self.api.create("Assignment", fields) as Assignment })
        switch outcome {
        case .sent(let created):
            assignments.insert(created, at: 0)
            await reloadData()
            return created
        case .queued:
            return assignments.first { $0.id == op.id }
        }
    }

    /// PATCH an assignment (the form and the detail sheet). Returns the server's
    /// copy, or the local copy when queued offline. Throws the server's error otherwise.
    @discardableResult
    func updateAssignment(_ id: String, _ patch: [String: JSONValue]) async throws -> Assignment? {
        guard !isDemo else { return assignments.first { $0.id == id } }
        let op = PendingOp(kind: .update, entity: "Assignment", id: id, body: patch)
        let outcome = try await sendOrQueue(op, { try await self.api.updateAssignment(id, patch) })
        switch outcome {
        case .sent(let updated):
            apply(updated)
            return updated
        case .queued:
            return assignments.first { $0.id == id }
        }
    }

    func toggleComplete(_ a: Assignment, user: User?) async {
        let prev = assignments
        var next = a
        next.completed.toggle()
        apply(next)
        guard !isDemo else { return }
        do {
            let patch: [String: JSONValue] = ["completed": .bool(next.completed)]
            let outcome = try await sendOrQueue(PendingOp(kind: .update, entity: "Assignment", id: a.id, body: patch),
                                                { try await self.api.updateAssignment(a.id, patch) })
            // Offline, only the completion is queued; Classroom isn't told.
            if case .sent = outcome, next.completed, a.source == .googleClassroom, a.externalId != nil,
               user?.syncCompletionToClassroom != false {
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
        do {
            _ = try await sendOrQueue(PendingOp(kind: .delete, entity: "Assignment", id: a.id),
                                      { try await self.api.delete("Assignment", id: a.id) })
        } catch {
            assignments = prev
        }
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
                    group.addTask { try await self.sendAssignmentPatch(id, patch) }
                }
                try await group.waitForAll()
            }
        } catch {
            assignments = prev
            overlayPending(["Assignment"]) // keep whatever did get queued
        }
    }

    private func sendAssignmentPatch(_ id: String, _ patch: [String: JSONValue]) async throws {
        _ = try await sendOrQueue(PendingOp(kind: .update, entity: "Assignment", id: id, body: patch),
                                  { try await self.api.updateAssignment(id, patch) })
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
        try? await sendAssignmentPatch(id, ["due_date": .from(newDate)])
    }

    // MARK: Classes

    /// Creates (`id == nil`) or updates a class (the class form). Queued while
    /// offline; throws the server's error otherwise.
    func saveClass(id: String?, _ fields: [String: JSONValue]) async throws {
        guard !isDemo else { return }
        let outcome: WriteOutcome<SchoolClass>
        if let id {
            let op = PendingOp(kind: .update, entity: "Class", id: id, body: fields)
            outcome = try await sendOrQueue(op, { try await self.api.update("Class", id: id, fields) as SchoolClass })
        } else {
            let op = PendingOp(kind: .create, entity: "Class", id: OfflineQueue.newTempId(), body: fields)
            outcome = try await sendOrQueue(op, { try await self.api.create("Class", fields) as SchoolClass })
        }
        if case .sent = outcome { await reloadData() }
    }

    func deleteClass(_ c: SchoolClass) async {
        let prev = classes
        classes.removeAll { $0.id == c.id }
        guard !isDemo else { return }
        do {
            _ = try await sendOrQueue(PendingOp(kind: .delete, entity: "Class", id: c.id),
                                      { try await self.api.delete("Class", id: c.id) })
        } catch {
            classes = prev
        }
    }

    // MARK: Notifications

    func loadNotifications() async {
        guard !isDemo else { return }
        do {
            notifications = try await api.notifications()
            overlayPending(["Notification"])
            scheduleCacheSave()
        } catch {
            noteFailure(error)
        }
    }

    func markRead(_ n: AppNotification) async {
        guard !n.read, let i = notifications.firstIndex(where: { $0.id == n.id }) else { return }
        notifications[i].read = true
        guard !isDemo else { return }
        await sendReadFlag(n.id)
    }

    func markAllRead() async {
        let unread = notifications.filter { !$0.read }
        for i in notifications.indices { notifications[i].read = true }
        guard !isDemo else { return }
        for n in unread { await sendReadFlag(n.id) }
    }

    private func sendReadFlag(_ id: String) async {
        let patch: [String: JSONValue] = ["read": true]
        _ = try? await sendOrQueue(PendingOp(kind: .update, entity: "Notification", id: id, body: patch),
                                   { try await self.api.update("Notification", id: id, patch) as AppNotification })
    }

    func clearNotifications() async {
        let all = notifications
        notifications = []
        guard !isDemo else { return }
        for n in all {
            _ = try? await sendOrQueue(PendingOp(kind: .delete, entity: "Notification", id: n.id),
                                       { try await self.api.delete("Notification", id: n.id) })
        }
    }

    // MARK: Offline (read cache + write queue)

    private enum WriteOutcome<T> {
        case sent(T)
        case queued
    }

    /// Sends a write, or queues `op` (and applies it locally) when the device is
    /// offline. While earlier changes are still queued, new ones queue behind
    /// them so they reach the server in order. Any other failure (a 4xx/5xx
    /// response) is thrown for the caller to roll back.
    private func sendOrQueue<T>(_ op: PendingOp, _ send: () async throws -> T) async throws -> WriteOutcome<T> {
        if isOffline || !queue.ops.isEmpty || OfflineQueue.isTempId(op.id) {
            enqueue(op)
            if !isOffline { Task { await self.syncPendingChanges() } }
            return .queued
        }
        do {
            let value = try await send()
            isOffline = false
            scheduleCacheSave()
            return .sent(value)
        } catch let e as APIError where e.isOffline {
            isOffline = true
            enqueue(op)
            return .queued
        }
    }

    private func enqueue(_ op: PendingOp) {
        applyLocally(op)
        if queue.userId.isEmpty, let id = currentUser?.id { queue.userId = id }
        queue.enqueue(op)
        persistQueue()
        scheduleCacheSave()
    }

    /// Replays queued offline changes in order (connection back, app in the
    /// foreground, sync), then reloads. A change the server refuses (4xx) is
    /// dropped and reported; offline or 5xx keeps it for next time.
    func syncPendingChanges(reload: Bool = true) async {
        guard !isDemo, !queue.ops.isEmpty, !replaying else { return }
        replaying = true
        var sent = 0
        var rejected: [String] = []
        var idMap: [String: String] = [:]
        replay: while let head = queue.ops.first {
            switch await sendQueued(head) {
            case .done(let createdId):
                sent += 1
                queue.finishHead(head, createdId: createdId)
                if head.kind == .create, let createdId { idMap[head.id] = createdId }
            case .rejected(let message):
                rejected.append(message)
                queue.rejectHead(head)
            case .retryLater:
                break replay
            }
            persistQueue()
        }
        replaying = false
        if !idMap.isEmpty { remapLocalIds(idMap) }
        if let first = rejected.first {
            offlineSyncError = rejected.count == 1 ? first : "\(rejected.count) changes couldn't be synced. \(first)"
        }
        if reload, sent > 0 || !rejected.isEmpty {
            await reloadData()
            await loadNotifications()
        }
    }

    private func sendQueued(_ op: PendingOp) async -> SendResult {
        do {
            let path = "/entities/\(op.entity)"
            let result: SendResult
            switch op.kind {
            case .create:
                let res = try await api.send(path, method: "POST", body: .object(op.body ?? [:]))
                result = .done(createdId: res["id"]?.stringValue)
            case .update:
                try await api.send("\(path)/\(op.id)", method: "PATCH", body: .object(op.body ?? [:]))
                result = .done(createdId: nil)
            case .delete:
                try await api.delete(op.entity, id: op.id)
                result = .done(createdId: nil)
            }
            isOffline = false
            return result
        } catch let e as APIError {
            if e.isOffline {
                isOffline = true
                return .retryLater
            }
            // Already gone on the server: that's what the delete wanted.
            if op.kind == .delete && e.status == 404 { return .done(createdId: nil) }
            // 401 signs out; 408/429 are worth another try.
            if (400..<500).contains(e.status) && ![401, 408, 429].contains(e.status) { return .rejected(e.message) }
            return .retryLater
        } catch {
            return .retryLater
        }
    }

    /// NWPathMonitor: replay and refresh when the connection comes back.
    private func networkChanged(online: Bool) {
        guard !isDemo, currentUser != nil else { return }
        if !online {
            isOffline = true
            return
        }
        let wasOffline = isOffline
        isOffline = false
        Task {
            await syncPendingChanges()
            if wasOffline && hasLoaded {
                await reloadData()
                await loadNotifications()
            }
        }
    }

    private func noteFailure(_ error: Error) {
        if Self.isOffline(error) { isOffline = true }
    }

    private static func isOffline(_ error: Error) -> Bool { (error as? APIError)?.isOffline == true }

    /// Loads the cache (if nothing is on screen yet) and the queue for `user`.
    private func restoreOfflineState(for user: User) {
        guard !user.isDemo, restoredFor != user.id else { return }
        restoredFor = user.id
        let cache = OfflineStorage.readCache()
        if let cache, cache.userId != user.id {
            // Someone else's data: drop it.
            OfflineStorage.clear()
            queue = OfflineQueue(userId: user.id)
            return
        }
        queue = OfflineStorage.readQueue(userId: user.id) ?? OfflineQueue(userId: user.id)
        if let cache, !hasLoaded {
            assignments = cache.assignments
            classes = cache.classes
            notifications = cache.notifications
            connections = cache.connections
            lastUpdated = cache.savedAt
            showingCachedData = true
            hasLoaded = true
        }
        overlayPending(nil)
    }

    /// Writes the cache a moment after things settle.
    private func scheduleCacheSave() {
        guard !isDemo else { return }
        saveTask?.cancel()
        saveTask = Task {
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }
            self.saveCache()
        }
    }

    private func saveCache() {
        guard !isDemo, hasLoaded, let user = currentUser, restoredFor == user.id, let updated = lastUpdated else { return }
        OfflineStorage.writeCache(OfflineCache(
            userId: user.id, savedAt: updated, user: user,
            assignments: assignments, classes: classes, notifications: notifications, connections: connections
        ))
    }

    private func persistQueue() {
        guard !queue.userId.isEmpty else { return }
        OfflineStorage.writeQueue(queue)
    }

    /// Lays the still-queued ops over the in-memory lists (`nil`: every entity).
    private func overlayPending(_ entities: Set<String>?) {
        for op in queue.ops where entities?.contains(op.entity) ?? true { applyLocally(op) }
    }

    private func applyLocally(_ op: PendingOp) {
        switch op.entity {
        case "Assignment": assignments = OfflineJSON.apply(op, to: assignments)
        case "Class": classes = OfflineJSON.apply(op, to: classes, prepend: false)
        case "Notification": notifications = OfflineJSON.apply(op, to: notifications)
        default: break
        }
    }

    /// After queued creates reach the server: swap temporary ids for the real ones on screen.
    private func remapLocalIds(_ idMap: [String: String]) {
        for i in assignments.indices {
            if let real = idMap[assignments[i].id] { assignments[i].id = real }
            if let cid = assignments[i].classId, let real = idMap[cid] { assignments[i].classId = real }
        }
        for i in classes.indices {
            if let real = idMap[classes[i].id] { classes[i].id = real }
        }
    }
}
