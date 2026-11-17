import ActivityKit
import Foundation
import UserNotifications

/// "Class starts soon" Live Activities.
///
/// - With push configured on the server (APNs key in Admin → Integration
///   Secrets, and the app built with the Push Notifications capability), the
///   server starts the activity 5 minutes before each class even when the app
///   is closed, and ends it after class starts. This manager registers the
///   device's push-to-start token and each activity's update token.
/// - Otherwise it falls back to a notification 5 minutes before class;
///   opening it (or having the app open then) starts the Live Activity.
@MainActor
enum ClassActivityManager {
    static let lead = ClassStartAttributes.leadTime
    private static let endAfter: TimeInterval = 10 * 60
    private static let reminderPrefix = "class-start-"

    /// Whether the server can start activities (APNs configured).
    private(set) static var serverPush = false
    /// This device's push-to-start token, once iOS provides one.
    private(set) static var pushToStartToken: String?
    private static var observing = false
    private static var observedActivities = Set<String>()

    private static var bundleId: String { Bundle.main.bundleIdentifier ?? "" }
    private static var apsEnvironment: String {
        #if DEBUG
        "development"
        #else
        "production"
        #endif
    }

    static func isEnabled(for user: User?) -> Bool {
        guard let user, !user.isDemo else { return false }
        return user.data["class_live_activities"]?.boolValue != false
    }

    /// Call after sign-in and whenever classes change.
    static func configure(user: User?, classes: [SchoolClass], assignments: [Assignment]) async {
        guard isEnabled(for: user) else {
            await cancelReminders()
            end(where: { _ in true })
            return
        }
        if let cfg = (try? await APIClient.shared.request("/devices/config", as: JSONValue.self)) {
            serverPush = cfg["classActivitiesPush"]?.boolValue == true
        }
        startObserving()
        await scheduleReminders(classes: classes)
        refresh(classes: classes, assignments: assignments)
    }

    /// Starts an activity if a class begins within 5 minutes (app must be in
    /// the foreground), and ends activities whose class has started.
    static func refresh(classes: [SchoolClass], assignments: [Assignment], now: Date = Date()) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        end(where: { $0.content.state.startDate < now.addingTimeInterval(-endAfter) })

        let existing = Activity<ClassStartAttributes>.activities
        for (cls, start) in upcomingMeetings(classes, from: now) where start.timeIntervalSince(now) <= lead + 5 {
            let already = existing.contains {
                $0.attributes.classId == cls.id && abs($0.content.state.startsAt - start.timeIntervalSince1970) < 60
            }
            if already { continue }
            let attributes = ClassStartAttributes(
                classId: cls.id, className: cls.name, subject: cls.subject ?? "", room: cls.room ?? "", colorHex: cls.color
            )
            let state = contentState(start: start, assignments: assignments, now: now)
            let content = ActivityContent(state: state, staleDate: start.addingTimeInterval(endAfter))
            // With push available, ask for an update token so the server can end it.
            var activity: Activity<ClassStartAttributes>?
            if serverPush {
                activity = try? Activity.request(attributes: attributes, content: content, pushType: .token)
            }
            if activity == nil {
                activity = try? Activity.request(attributes: attributes, content: content, pushType: nil)
            }
            if let activity { observeUpdateToken(activity) }
        }
    }

    /// Stops this device receiving class activities (call before signing out).
    static func unregister(bearer: String?) async {
        await cancelReminders()
        end(where: { _ in true })
        guard let token = pushToStartToken, let bearer else { return }
        _ = try? await APIClient.shared.send("/devices/push-to-start", method: "DELETE",
                                             body: ["token": .string(token)], bearer: bearer)
    }

    // MARK: - Helpers

    /// Each class's next meeting within the next day, soonest first.
    static func upcomingMeetings(_ classes: [SchoolClass], from now: Date) -> [(SchoolClass, Date)] {
        classes.flatMap { cls in
            cls.schedule.compactMap { m -> (SchoolClass, Date)? in
                guard let start = m.nextStart(after: now), start.timeIntervalSince(now) < 24 * 3600 else { return nil }
                return (cls, start)
            }
        }
        .sorted { $0.1 < $1.1 }
    }

    private static func contentState(start: Date, assignments: [Assignment], now: Date) -> ClassStartAttributes.ContentState {
        let due = assignments
            .filter { !$0.completed && ($0.dueDate.map(Calendar.current.isDateInToday) ?? false) }
            .sorted { ($0.dueDate ?? .distantFuture) < ($1.dueDate ?? .distantFuture) }
        return .init(
            startsAt: start.timeIntervalSince1970,
            dueToday: due.prefix(3).map { .init(title: $0.title, className: $0.className ?? "") },
            dueCount: due.count
        )
    }

    private static func end(where shouldEnd: (Activity<ClassStartAttributes>) -> Bool) {
        let finished = Activity<ClassStartAttributes>.activities.filter(shouldEnd)
        guard !finished.isEmpty else { return }
        Task { for a in finished { await a.end(nil, dismissalPolicy: .immediate) } }
    }

    // MARK: Push tokens

    private static func startObserving() {
        guard !observing else { return }
        observing = true
        if #available(iOS 17.2, *) {
            Task {
                for await data in Activity<ClassStartAttributes>.pushToStartTokenUpdates {
                    let token = data.map { String(format: "%02x", $0) }.joined()
                    pushToStartToken = token
                    _ = try? await APIClient.shared.send("/devices/push-to-start", body: [
                        "token": .string(token), "environment": .string(apsEnvironment), "bundleId": .string(bundleId),
                    ])
                    // The server now starts activities, so drop the fallback reminders.
                    if serverPush { await cancelReminders() }
                }
            }
        }
        for activity in Activity<ClassStartAttributes>.activities { observeUpdateToken(activity) }
        Task {
            for await activity in Activity<ClassStartAttributes>.activityUpdates { observeUpdateToken(activity) }
        }
    }

    private static func observeUpdateToken(_ activity: Activity<ClassStartAttributes>) {
        guard observedActivities.insert(activity.id).inserted else { return }
        Task {
            for await data in activity.pushTokenUpdates {
                let token = data.map { String(format: "%02x", $0) }.joined()
                _ = try? await APIClient.shared.send("/devices/class-activity", body: [
                    "token": .string(token), "environment": .string(apsEnvironment), "bundleId": .string(bundleId),
                    "classId": .string(activity.attributes.classId),
                    "startsAt": .number(activity.content.state.startsAt),
                ])
            }
        }
    }

    // MARK: Fallback reminders

    /// A weekly notification 5 minutes before each class meeting, used when
    /// the server can't start the Live Activity itself.
    private static func scheduleReminders(classes: [SchoolClass]) async {
        await cancelReminders()
        if serverPush && pushToStartToken != nil { return }
        let meetings = classes.flatMap { cls in cls.schedule.map { (cls, $0) } }
        guard !meetings.isEmpty else { return }
        await LocalNotifier.requestPermission()

        let center = UNUserNotificationCenter.current()
        for (cls, meeting) in meetings.prefix(60) {
            guard let t = meeting.startComponents else { continue }
            // 5 minutes earlier, wrapping to the previous day if needed.
            var minutes = t.hour * 60 + t.minute - Int(lead / 60)
            var weekday = meeting.day
            if minutes < 0 { minutes += 24 * 60; weekday = (weekday + 6) % 7 }
            var when = DateComponents()
            when.weekday = weekday + 1
            when.hour = minutes / 60
            when.minute = minutes % 60

            let content = UNMutableNotificationContent()
            content.title = "\(cls.name) starts in 5 minutes"
            var details: [String] = []
            if let subject = cls.subject, !subject.isEmpty { details.append(subject) }
            if let room = cls.room, !room.isEmpty { details.append("Room \(room)") }
            content.body = details.isEmpty ? "Get ready for class" : details.joined(separator: " · ")
            content.sound = .default
            content.userInfo = ["classId": cls.id]
            let id = "\(reminderPrefix)\(cls.id)-\(meeting.id)"
            try? await center.add(UNNotificationRequest(
                identifier: id, content: content,
                trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true)
            ))
        }
    }

    private static func cancelReminders() async {
        let center = UNUserNotificationCenter.current()
        let ids = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(reminderPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }
}
