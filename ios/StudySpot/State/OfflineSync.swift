import Foundation
import Network

// Offline mode: a read cache of the last loaded data and a queue of writes
// made while offline, replayed in order once the connection is back
// (DataStore). Mirrors android/.../data/OfflineQueue.kt and OfflineStorage.kt.
//
// Conflict policy: last write wins. A replayed patch overwrites whatever the
// server has for those fields, with no merging or version checks — simple and
// predictable for a single-user planner.

// MARK: - Queue

/// One write made while offline, stored as the plain entity call it sends
/// (`/api/entities/<entity>`), so toggles, reschedules, bulk edits and
/// "mark read" all replay the same way.
struct PendingOp: Codable, Equatable, Sendable {
    enum Kind: String, Codable, Sendable { case create, update, delete }

    var seq: Int = 0
    var kind: Kind
    /// "Assignment", "Class", "Notification", …
    var entity: String
    /// The row's id; for `.create` a temporary local id (`OfflineQueue.newTempId()`).
    var id: String
    /// Create fields or update patch (server column names).
    var body: [String: JSONValue]?

    /// True if this op mentions `tempId` as its id or anywhere in its body (e.g. class_id).
    func references(_ tempId: String) -> Bool {
        id == tempId || (body.map { Self.mentions(.object($0), tempId) } ?? false)
    }

    /// This op with every occurrence of `from` (id and body string values) replaced by `to`.
    func remapped(from: String, to: String) -> PendingOp {
        guard references(from) else { return self }
        var op = self
        if op.id == from { op.id = to }
        if let b = op.body, case .object(let o) = Self.replace(.object(b), from, to) { op.body = o }
        return op
    }

    private static func mentions(_ v: JSONValue, _ s: String) -> Bool {
        switch v {
        case .string(let x): return x == s
        case .array(let a): return a.contains { mentions($0, s) }
        case .object(let o): return o.values.contains { mentions($0, s) }
        default: return false
        }
    }

    private static func replace(_ v: JSONValue, _ from: String, _ to: String) -> JSONValue {
        switch v {
        case .string(let x): return x == from ? .string(to) : v
        case .array(let a): return .array(a.map { replace($0, from, to) })
        case .object(let o): return .object(o.mapValues { replace($0, from, to) })
        default: return v
        }
    }
}

enum SendResult: Sendable {
    /// Accepted; for a create, the server's id for the row.
    case done(createdId: String?)
    /// Refused for good (4xx): drop the op and show the message.
    case rejected(String)
    /// Offline or a server error (5xx): keep the op and try later.
    case retryLater
}

/// The ordered, persisted list of offline writes for one user.
///
/// - Replay is strictly in order, one op at a time (DataStore.syncPendingChanges).
/// - When a create replays, the server's id replaces the temporary id in every
///   later op (`finishHead`).
/// - Deleting something created offline that was never sent just removes it
///   from the queue.
/// - A refused create also drops the later ops that need its id.
struct OfflineQueue: Codable, Sendable {
    var userId: String
    private(set) var ops: [PendingOp] = []
    private var nextSeq = 1

    init(userId: String) { self.userId = userId }

    static let tempPrefix = "local-"
    static func newTempId() -> String { tempPrefix + UUID().uuidString.lowercased() }
    static func isTempId(_ id: String?) -> Bool { id?.hasPrefix(tempPrefix) == true }

    mutating func enqueue(_ op: PendingOp) {
        if op.kind == .delete, Self.isTempId(op.id), ops.contains(where: { $0.kind == .create && $0.id == op.id }) {
            ops.removeAll { $0.references(op.id) }
            return
        }
        var stamped = op
        stamped.seq = nextSeq
        nextSeq += 1
        ops.append(stamped)
    }

    /// `head` was accepted: remove it and, for a create, swap in the real id.
    mutating func finishHead(_ head: PendingOp, createdId: String?) {
        ops.removeAll { $0.seq == head.seq }
        if head.kind == .create, let createdId, createdId != head.id {
            ops = ops.map { $0.remapped(from: head.id, to: createdId) }
        }
    }

    /// `head` was refused: remove it (and, for a create, everything needing its id).
    mutating func rejectHead(_ head: PendingOp) {
        ops.removeAll { $0.seq == head.seq }
        if head.kind == .create, Self.isTempId(head.id) {
            ops.removeAll { $0.references(head.id) }
        }
    }
}

// MARK: - Applying ops locally

enum OfflineJSON {
    static func value<T: Encodable>(_ v: T) -> JSONValue? {
        guard let data = try? JSONEncoder().encode(v) else { return nil }
        return try? JSONDecoder().decode(JSONValue.self, from: data)
    }

    static func decode<T: Decodable>(_ type: T.Type, from v: JSONValue) -> T? {
        guard let data = try? JSONEncoder().encode(v) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    /// `op` applied to a local list the way the server would: a create adds
    /// (or replaces) the row, an update merges the patch into its JSON, a
    /// delete removes it. Idempotent, so queued ops can be laid over freshly
    /// loaded or cached data at any time.
    static func apply<T: Codable & Identifiable>(_ op: PendingOp, to list: [T], prepend: Bool = true) -> [T] where T.ID == String {
        switch op.kind {
        case .create:
            var fields = op.body ?? [:]
            fields["id"] = .string(op.id)
            if fields["created_date"] == nil { fields["created_date"] = .from(Date()) }
            guard let row = decode(T.self, from: .object(fields)) else { return list }
            if let i = list.firstIndex(where: { $0.id == op.id }) {
                var out = list
                out[i] = row
                return out
            }
            return prepend ? [row] + list : list + [row]
        case .update:
            guard let patch = op.body else { return list }
            return list.map { item in
                guard item.id == op.id, case .object(var current)? = value(item) else { return item }
                for (k, v) in patch { current[k] = v }
                return decode(T.self, from: .object(current)) ?? item
            }
        case .delete:
            return list.filter { $0.id != op.id }
        }
    }
}

// MARK: - Disk

/// The last data loaded for one user, shown at launch before the network answers.
struct OfflineCache: Codable {
    var userId: String
    /// When this data was last loaded from the server.
    var savedAt: Date
    var user: User?
    var assignments: [Assignment]
    var classes: [SchoolClass]
    var notifications: [AppNotification]
    var connections: Connections
}

/// Cache and queue files in Application Support/Offline. One user at a time:
/// wiped on sign-out, and ignored (then wiped) when another account loads.
enum OfflineStorage {
    private static let io = DispatchQueue(label: "education.studyspot.offline", qos: .utility)

    private static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("Offline", isDirectory: true)
    }
    private static var cacheURL: URL { directory.appendingPathComponent("cache.json") }
    private static var queueURL: URL { directory.appendingPathComponent("queue.json") }

    static func readCache() -> OfflineCache? {
        guard let data = try? Data(contentsOf: cacheURL) else { return nil }
        return try? JSONDecoder().decode(OfflineCache.self, from: data)
    }

    static func readQueue(userId: String) -> OfflineQueue? {
        guard let data = try? Data(contentsOf: queueURL),
              let q = try? JSONDecoder().decode(OfflineQueue.self, from: data),
              q.userId == userId else { return nil }
        return q
    }

    /// The signed-in user from the cache, for starting while offline (AuthStore.check).
    static func cachedUser() -> User? {
        guard let user = readCache()?.user, !user.isDemo else { return nil }
        return user
    }

    static func writeCache(_ cache: OfflineCache) {
        guard let data = try? JSONEncoder().encode(cache) else { return }
        write(data, to: cacheURL)
    }

    static func writeQueue(_ queue: OfflineQueue) {
        if queue.ops.isEmpty {
            let url = queueURL
            io.async { try? FileManager.default.removeItem(at: url) }
            return
        }
        guard let data = try? JSONEncoder().encode(queue) else { return }
        write(data, to: queueURL)
    }

    static func clear() {
        let dir = directory
        io.async { try? FileManager.default.removeItem(at: dir) }
    }

    /// Writes happen in order on a background queue.
    private static func write(_ data: Data, to url: URL) {
        let dir = directory
        io.async {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
    }
}

// MARK: - Connectivity

/// Watches the device's network path (NWPathMonitor) and reports changes on the main actor.
@MainActor
final class NetworkMonitor {
    private let monitor = NWPathMonitor()
    private var started = false

    func start(onChange: @escaping @MainActor @Sendable (Bool) -> Void) {
        guard !started else { return }
        started = true
        monitor.pathUpdateHandler = Self.handler(onChange)
        monitor.start(queue: DispatchQueue(label: "education.studyspot.network"))
    }

    /// Built outside the main actor: NWPathMonitor calls it on its own queue.
    nonisolated private static func handler(_ onChange: @escaping @MainActor @Sendable (Bool) -> Void) -> @Sendable (NWPath) -> Void {
        { path in
            let online = path.status == .satisfied
            Task { @MainActor in onChange(online) }
        }
    }
}

// MARK: - Encoding the models (for the cache and for applying patches)
//
// The models are decoded leniently from server JSON; these write them back in
// the same shape (dates as ISO-8601 strings) so they round-trip.

extension User: Encodable {
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(username, forKey: .username)
        try c.encodeIfPresent(name, forKey: .name)
        try c.encodeIfPresent(fullName, forKey: .fullName)
        try c.encode(role, forKey: .role)
        try c.encodeIfPresent(disabledReason, forKey: .disabledReason)
        try c.encode(isVerified, forKey: .isVerified)
        try c.encode(twofaEnabled, forKey: .twofaEnabled)
        try c.encodeIfPresent(twofaMethod, forKey: .twofaMethod)
        try c.encode(twofaMethods, forKey: .twofaMethods)
        try c.encode(mfaPending, forKey: .mfaPending)
        try c.encode(data, forKey: .data)
    }
}

extension Assignment: Encodable {
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(title, forKey: .title)
        try c.encodeIfPresent(classId, forKey: .classId)
        try c.encodeIfPresent(className, forKey: .className)
        try c.encodeIfPresent(dueDate.map(ISO8601.string(from:)), forKey: .dueDate)
        try c.encode(priority, forKey: .priority)
        try c.encode(type, forKey: .type)
        try c.encode(notes, forKey: .notes)
        try c.encode(description, forKey: .description)
        try c.encode(source, forKey: .source)
        try c.encodeIfPresent(externalId, forKey: .externalId)
        try c.encode(completed, forKey: .completed)
        try c.encode(progress, forKey: .progress)
        try c.encode(subtasks, forKey: .subtasks)
        try c.encode(points, forKey: .points)
        try c.encodeIfPresent(score, forKey: .score)
        try c.encodeIfPresent(createdDate.map(ISO8601.string(from:)), forKey: .createdDate)
        try c.encodeIfPresent(updatedDate.map(ISO8601.string(from:)), forKey: .updatedDate)
    }
}

extension SchoolClass: Encodable {
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(color, forKey: .color)
        try c.encodeIfPresent(teacherName, forKey: .teacherName)
        try c.encodeIfPresent(subject, forKey: .subject)
        try c.encodeIfPresent(room, forKey: .room)
        try c.encode(schedule, forKey: .schedule)
        try c.encode(source, forKey: .source)
        try c.encodeIfPresent(externalId, forKey: .externalId)
        try c.encode(bestStreak, forKey: .bestStreak)
    }
}

extension AppNotification: Encodable {
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(title, forKey: .title)
        try c.encode(content, forKey: .content)
        try c.encode(type, forKey: .type)
        try c.encode(read, forKey: .read)
        try c.encodeIfPresent(actionLabel, forKey: .actionLabel)
        try c.encodeIfPresent(actionURL, forKey: .actionURL)
        try c.encodeIfPresent(createdDate.map(ISO8601.string(from:)), forKey: .createdDate)
    }
}

extension Connections: Encodable {
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(calendar, forKey: .calendar)
        try c.encode(classroom, forKey: .classroom)
        try c.encode(blackboard, forKey: .blackboard)
        try c.encode(ical, forKey: .ical)
    }
}
