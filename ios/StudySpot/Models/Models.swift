import Foundation

// MARK: - Enums shared by assignments and templates

enum Priority: String, CaseIterable, Codable, Identifiable, Sendable {
    case low, medium, high
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
    var rank: Int { switch self { case .high: 3; case .medium: 2; case .low: 1 } }
}

enum AssignmentType: String, CaseIterable, Codable, Identifiable, Sendable {
    case homework, project, quiz, test, reading, other
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
}

enum Source: String, Codable, Sendable {
    case manual
    case googleCalendar = "google_calendar"
    case googleClassroom = "google_classroom"
    case blackboard

    var label: String {
        switch self {
        case .manual: "Manual"
        case .googleCalendar: "Calendar"
        case .googleClassroom: "Classroom"
        case .blackboard: "Blackboard"
        }
    }
}

// MARK: - User

struct User: Decodable, Equatable, Sendable {
    var id: String
    /// Sign-in name. Accounts created before usernames existed use their old
    /// address as their username.
    var username: String
    var name: String?
    var fullName: String?
    var role: String
    var disabledReason: String?
    var isVerified: Bool
    var twofaEnabled: Bool
    var twofaMethod: String?
    var twofaMethods: [String]
    /// True while this session still needs its second factor verified (the
    /// server refuses other requests until then).
    var mfaPending: Bool
    var data: [String: JSONValue]

    enum CodingKeys: String, CodingKey {
        case id, username, name, role, data
        case fullName = "full_name"
        case disabledReason = "disabled_reason"
        case isVerified = "is_verified"
        case twofaEnabled = "twofa_enabled"
        case twofaMethod = "twofa_method"
        case twofaMethods = "twofa_methods"
        case mfaPending = "mfa_pending"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        username = c.flexString(.username) ?? ""
        name = c.flexString(.name)
        fullName = c.flexString(.fullName)
        role = c.flexString(.role) ?? "user"
        disabledReason = c.flexString(.disabledReason)
        isVerified = c.flexBool(.isVerified) ?? false
        twofaEnabled = c.flexBool(.twofaEnabled) ?? false
        twofaMethod = c.flexString(.twofaMethod)
        twofaMethods = (try? c.decodeIfPresent([String].self, forKey: .twofaMethods)) ?? []
        mfaPending = c.flexBool(.mfaPending) ?? false
        data = (try? c.decodeIfPresent([String: JSONValue].self, forKey: .data)) ?? [:]
    }

    var isAdmin: Bool { role == "admin" }
    var isDemo: Bool { role == "demo" }

    var displayName: String {
        if let n = name, !n.isEmpty { return n }
        if let n = fullName, !n.isEmpty { return n }
        return username.split(separator: "@").first.map(String.init) ?? "User"
    }

    var onboardingCompleted: Bool { data["onboarding_completed"]?.boolValue == true }
    var leaderboardEnabled: Bool { data["leaderboard_enabled"]?.boolValue == true }
    var welcomeStyle: String { data["welcome_style"]?.stringValue ?? "large" }
    var syncCompletionToClassroom: Bool { data["sync_completion_to_classroom"]?.boolValue != false }
    var lastSync: Date? { data["last_sync"]?.stringValue.flatMap(ISO8601.date(from:)) }
    var blackboardInstanceURL: String? { data["blackboard_instance_url"]?.stringValue }

    var gradingScale: [GradeEntry] {
        guard let arr = data["grading_scale"]?.arrayValue, !arr.isEmpty else { return Grading.defaultScale }
        let parsed = arr.compactMap { v -> GradeEntry? in
            guard let letter = v["letter"]?.stringValue, let min = v["min"]?.doubleValue else { return nil }
            return GradeEntry(letter: letter, min: min)
        }
        return parsed.isEmpty ? Grading.defaultScale : parsed
    }

    /// Enabled 2FA methods, accounting for the legacy single-method field.
    var enabledTwoFactorMethods: [TwoFactorMethod] {
        var raw: [String] = twofaMethods
        if raw.isEmpty, let arr = data["twofa_methods"]?.arrayValue { raw = arr.compactMap(\.stringValue) }
        if raw.isEmpty, let m = twofaMethod { raw = [m] }
        if raw.isEmpty, twofaEnabled { raw = ["totp"] }
        return raw.compactMap(TwoFactorMethod.init(rawValue:))
    }
}

enum TwoFactorMethod: String, CaseIterable, Identifiable, Sendable {
    case totp, passkey
    var id: String { rawValue }
    var label: String {
        switch self {
        case .totp: "Authenticator app"
        case .passkey: "Passkey"
        }
    }
    var detail: String {
        switch self {
        case .totp: "Google Authenticator, Authy, 2FAS"
        case .passkey: "Face ID or Touch ID; also signs you in without a password"
        }
    }
    var symbol: String {
        switch self {
        case .totp: "iphone"
        case .passkey: "person.badge.key"
        }
    }
}

// MARK: - Entities

struct Subtask: Codable, Hashable, Sendable {
    var text: String
    var done: Bool
}

struct Assignment: Decodable, Identifiable, Hashable, Sendable {
    var id: String
    var title: String
    var classId: String?
    var className: String?
    var dueDate: Date?
    var priority: Priority
    var type: AssignmentType
    var notes: String
    var description: String
    var source: Source
    var externalId: String?
    var completed: Bool
    var progress: Double
    var subtasks: [Subtask]
    var points: Double
    var score: Double?
    var createdDate: Date?
    var updatedDate: Date?

    enum CodingKeys: String, CodingKey {
        case id, title, priority, type, notes, description, source, completed, progress, subtasks, points, score
        case classId = "class_id"
        case className = "class_name"
        case dueDate = "due_date"
        case externalId = "external_id"
        case createdDate = "created_date"
        case updatedDate = "updated_date"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = c.flexString(.title) ?? "Untitled"
        classId = c.flexString(.classId).flatMap { $0.isEmpty ? nil : $0 }
        className = c.flexString(.className)
        dueDate = c.flexDate(.dueDate)
        priority = Priority(rawValue: c.flexString(.priority) ?? "") ?? .medium
        type = AssignmentType(rawValue: c.flexString(.type) ?? "") ?? .homework
        notes = c.flexString(.notes) ?? ""
        description = c.flexString(.description) ?? ""
        source = Source(rawValue: c.flexString(.source) ?? "") ?? .manual
        externalId = c.flexString(.externalId)
        completed = c.flexBool(.completed) ?? false
        progress = c.flexDouble(.progress) ?? 0
        subtasks = (try? c.decodeIfPresent([Subtask].self, forKey: .subtasks)) ?? []
        points = c.flexDouble(.points) ?? 0
        score = c.flexDouble(.score)
        createdDate = c.flexDate(.createdDate)
        updatedDate = c.flexDate(.updatedDate)
    }

    init(id: String, title: String, classId: String?, className: String?, dueDate: Date?,
         priority: Priority, type: AssignmentType, source: Source, externalId: String? = nil,
         completed: Bool, progress: Double, points: Double, score: Double? = nil,
         notes: String = "", description: String = "", subtasks: [Subtask] = []) {
        self.id = id; self.title = title; self.classId = classId; self.className = className
        self.dueDate = dueDate; self.priority = priority; self.type = type; self.source = source
        self.externalId = externalId; self.completed = completed; self.progress = progress
        self.points = points; self.score = score; self.notes = notes; self.description = description
        self.subtasks = subtasks; self.createdDate = Date(); self.updatedDate = Date()
    }

    var isOverdue: Bool {
        guard let d = dueDate else { return false }
        return d < Date() && !completed
    }

    var isGraded: Bool { score != nil && points > 0 }
}

/// One weekly meeting of a class: weekday (0 = Sunday … 6 = Saturday) and
/// local start/end times as "HH:mm".
struct ClassMeeting: Codable, Hashable, Sendable, Identifiable {
    var day: Int
    var start: String
    var end: String
    var id: String { "\(day)-\(start)-\(end)" }

    static let weekdayNames = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
    static let weekdayShort = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]

    var startComponents: (hour: Int, minute: Int)? { Self.parse(start) }

    static func parse(_ s: String) -> (hour: Int, minute: Int)? {
        let parts = s.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2, (0..<24).contains(parts[0]), (0..<60).contains(parts[1]) else { return nil }
        return (parts[0], parts[1])
    }

    /// The next time this meeting starts at or after `date`.
    func nextStart(after date: Date, calendar: Calendar = .current) -> Date? {
        guard let t = startComponents else { return nil }
        var comps = DateComponents()
        comps.weekday = day + 1 // Calendar weekdays are 1-based (1 = Sunday)
        comps.hour = t.hour
        comps.minute = t.minute
        return calendar.nextDate(after: date.addingTimeInterval(-1), matching: comps, matchingPolicy: .nextTime)
    }
}

struct SchoolClass: Decodable, Identifiable, Hashable, Sendable {
    var id: String
    var name: String
    var color: String
    var teacherName: String?
    var subject: String?
    var room: String?
    var schedule: [ClassMeeting]
    var source: Source
    var externalId: String?
    var bestStreak: Int

    enum CodingKeys: String, CodingKey {
        case id, name, color, source, subject, room, schedule
        case teacherName = "teacher_name"
        case externalId = "external_id"
        case bestStreak = "best_streak"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = c.flexString(.name) ?? "Unnamed"
        color = c.flexString(.color) ?? "#6366f1"
        teacherName = c.flexString(.teacherName)
        subject = c.flexString(.subject)
        room = c.flexString(.room)
        schedule = (try? c.decodeIfPresent([ClassMeeting].self, forKey: .schedule)) ?? []
        source = Source(rawValue: c.flexString(.source) ?? "") ?? .manual
        externalId = c.flexString(.externalId)
        bestStreak = Int(c.flexDouble(.bestStreak) ?? 0)
    }

    init(id: String, name: String, color: String, teacherName: String?, source: Source, externalId: String? = nil) {
        self.id = id; self.name = name; self.color = color; self.teacherName = teacherName
        self.source = source; self.externalId = externalId; self.bestStreak = 0
        self.subject = nil; self.room = nil; self.schedule = []
    }
}

struct AssignmentTemplate: Decodable, Identifiable, Hashable, Sendable {
    var id: String
    var name: String
    var title: String
    var type: AssignmentType
    var priority: Priority
    var points: Double
    var notes: String

    enum CodingKeys: String, CodingKey { case id, name, title, type, priority, points, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = c.flexString(.name) ?? ""
        title = c.flexString(.title) ?? ""
        type = AssignmentType(rawValue: c.flexString(.type) ?? "") ?? .homework
        priority = Priority(rawValue: c.flexString(.priority) ?? "") ?? .medium
        points = c.flexDouble(.points) ?? 0
        notes = c.flexString(.notes) ?? ""
    }
}

struct AppNotification: Decodable, Identifiable, Hashable, Sendable {
    var id: String
    var title: String
    var content: String
    var type: String
    var read: Bool
    var actionLabel: String?
    var actionURL: String?
    var createdDate: Date?

    enum CodingKeys: String, CodingKey {
        case id, title, content, type, read
        case actionLabel = "action_label"
        case actionURL = "action_url"
        case createdDate = "created_date"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = c.flexString(.title) ?? ""
        content = c.flexString(.content) ?? ""
        type = c.flexString(.type) ?? "info"
        read = c.flexBool(.read) ?? false
        actionLabel = c.flexString(.actionLabel)
        actionURL = c.flexString(.actionURL)
        createdDate = c.flexDate(.createdDate)
    }
}

struct GradeEntry: Codable, Hashable, Sendable {
    var letter: String
    var min: Double
}

struct Connections: Decodable, Equatable, Sendable {
    var calendar = false
    var classroom = false
    var blackboard = false
    /// At least one Google Calendar link (secret iCal address) is added.
    var ical = false

    var any: Bool { calendar || classroom || blackboard || ical }

    init(calendar: Bool = false, classroom: Bool = false, blackboard: Bool = false, ical: Bool = false) {
        self.calendar = calendar; self.classroom = classroom; self.blackboard = blackboard; self.ical = ical
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        calendar = c.flexBool(.calendar) ?? false
        classroom = c.flexBool(.classroom) ?? false
        blackboard = c.flexBool(.blackboard) ?? false
        ical = c.flexBool(.ical) ?? false
    }

    enum CodingKeys: String, CodingKey { case calendar, classroom, blackboard, ical }
}

/// A Google Calendar link (the calendar's "Secret address in iCal format").
/// The server never returns the secret URL itself, only a short hint.
struct ICalFeed: Decodable, Identifiable, Equatable, Sendable {
    var id: String
    var label: String
    var calendarName: String?
    var urlHint: String?
    /// "auto" (only assignment-like events) or "all".
    var mode: String
    var lastSyncedAt: Date?
    var lastError: String?
    var lastImported: Int?
    var createdAt: Date?

    var importsEverything: Bool { mode == "all" }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? ""
        label = c.flexString(.label) ?? "Google Calendar"
        calendarName = c.flexString(.calendarName)
        urlHint = c.flexString(.urlHint)
        mode = c.flexString(.mode) ?? "auto"
        lastSyncedAt = c.flexDate(.lastSyncedAt)
        lastError = c.flexString(.lastError).flatMap { $0.isEmpty ? nil : $0 }
        lastImported = c.flexDouble(.lastImported).map { Int($0) }
        createdAt = c.flexDate(.createdAt)
    }

    enum CodingKeys: String, CodingKey {
        case id, label, mode
        case calendarName = "calendar_name"
        case urlHint = "url_hint"
        case lastSyncedAt = "last_synced_at"
        case lastError = "last_error"
        case lastImported = "last_imported"
        case createdAt = "created_at"
    }
}

struct LeaderboardEntry: Decodable, Identifiable, Hashable, Sendable {
    var userId: String
    var name: String
    var bestStreak: Int
    var id: String { userId }
}

struct Leaderboard: Decodable, Sendable {
    var entries: [LeaderboardEntry]
    var currentUserId: String?
}

struct ClassroomAttachment: Decodable, Hashable, Sendable {
    var title: String
    var url: String
    var kind: String
}

// MARK: - Admin

struct AdminUser: Decodable, Identifiable, Hashable, Sendable {
    var id: String
    var username: String
    var fullName: String?
    var name: String?
    var role: String
    var disabledReason: String?
    var twofaEnabled: Bool
    var twofaMethod: String?
    var isVerified: Bool
    var onboardingCompleted: Bool
    var createdDate: Date?

    enum CodingKeys: String, CodingKey {
        case id, username, name, role
        case fullName = "full_name"
        case disabledReason = "disabled_reason"
        case twofaEnabled = "twofa_enabled"
        case twofaMethod = "twofa_method"
        case isVerified = "is_verified"
        case onboardingCompleted = "onboarding_completed"
        case createdDate = "created_date"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        username = c.flexString(.username) ?? ""
        fullName = c.flexString(.fullName)
        name = c.flexString(.name)
        role = c.flexString(.role) ?? "user"
        disabledReason = c.flexString(.disabledReason)
        twofaEnabled = c.flexBool(.twofaEnabled) ?? false
        twofaMethod = c.flexString(.twofaMethod)
        isVerified = c.flexBool(.isVerified) ?? false
        onboardingCompleted = c.flexBool(.onboardingCompleted) ?? false
        createdDate = c.flexDate(.createdDate)
    }

    var displayName: String {
        if let n = name, !n.isEmpty { return n }
        if let n = fullName, !n.isEmpty { return n }
        return username
    }
}

struct SecretStatus: Decodable, Identifiable, Hashable, Sendable {
    var key: String
    var group: String
    var label: String
    var isSet: Bool
    var setAt: Date?
    var id: String { key }

    enum CodingKeys: String, CodingKey { case key, group, label, isSet, setAt }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = try c.decode(String.self, forKey: .key)
        group = c.flexString(.group) ?? "Other"
        label = c.flexString(.label) ?? key
        isSet = c.flexBool(.isSet) ?? false
        setAt = c.flexDate(.setAt)
    }
}

struct DeployStatus: Decodable, Equatable, Sendable {
    var status: String
    var startedAt: Date?
    var finishedAt: Date?
    var log: String
    var error: String?

    enum CodingKeys: String, CodingKey { case status, startedAt, finishedAt, log, error }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        status = c.flexString(.status) ?? "idle"
        startedAt = c.flexDate(.startedAt)
        finishedAt = c.flexDate(.finishedAt)
        log = c.flexString(.log) ?? ""
        error = c.flexString(.error)
    }
}
