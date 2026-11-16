import UserNotifications

/// Local notifications for the study timer, so a phase ending while the app
/// is in the background still alerts the student.
enum LocalNotifier {
    static let timerId = "study-timer-phase-end"

    static func requestPermission() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }

    static func scheduleTimerEnd(at date: Date, title: String, body: String) {
        let interval = date.timeIntervalSinceNow
        guard interval > 1 else { return }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: timerId, content: content, trigger: trigger))
    }

    static func cancelTimer() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [timerId])
    }
}
