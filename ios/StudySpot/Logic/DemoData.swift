import Foundation

/// Demo role: fake, pre-populated content so anyone with the "demo" role can
/// browse the app without real data or connected integrations.
enum DemoData {
    static let connections = Connections(calendar: true, classroom: true, blackboard: true)

    static var classes: [SchoolClass] {
        [
            SchoolClass(id: "demo-c1", name: "AP Calculus", color: "#6366f1", teacherName: "Mrs. Chen", source: .googleClassroom, externalId: "d1"),
            SchoolClass(id: "demo-c2", name: "English Literature", color: "#ec4899", teacherName: "Mr. Patel", source: .googleClassroom, externalId: "d2"),
            SchoolClass(id: "demo-c3", name: "Physics", color: "#10b981", teacherName: "Dr. Lee", source: .blackboard, externalId: "d3"),
            SchoolClass(id: "demo-c4", name: "US History", color: "#f59e0b", teacherName: "Ms. Garcia", source: .manual),
        ]
    }

    private static func due(_ offsetDays: Int, hour: Int = 9) -> Date {
        let cal = Calendar.current
        let day = cal.date(byAdding: .day, value: offsetDays, to: Date())!
        return cal.date(bySettingHour: hour, minute: 0, second: 0, of: day)!
    }

    static var assignments: [Assignment] {
        func a(_ id: String, _ title: String, _ cls: String, _ clsName: String, _ off: Int, _ pr: Priority,
               _ type: AssignmentType, _ src: Source, done: Bool = false, progress: Double, points: Double, score: Double? = nil) -> Assignment {
            Assignment(id: id, title: title, classId: cls, className: clsName, dueDate: due(off), priority: pr, type: type,
                       source: src, externalId: src == .manual ? nil : id, completed: done, progress: progress, points: points, score: score)
        }
        return [
            a("demo-a1", "Problem Set 4", "demo-c1", "AP Calculus", -2, .high, .homework, .googleClassroom, progress: 40, points: 100),
            a("demo-a2", "Essay Draft", "demo-c2", "English Literature", 0, .high, .project, .googleClassroom, progress: 60, points: 50),
            a("demo-a3", "Lab Report: Pendulums", "demo-c3", "Physics", 2, .medium, .project, .blackboard, progress: 20, points: 80),
            a("demo-a4", "Chapter 5 Reading", "demo-c4", "US History", 4, .low, .reading, .manual, progress: 0, points: 20),
            a("demo-a5", "Quiz 3: Derivatives", "demo-c1", "AP Calculus", 1, .medium, .quiz, .googleClassroom, progress: 0, points: 40),
            a("demo-a6", "Vocab Worksheet", "demo-c2", "English Literature", -5, .low, .homework, .googleClassroom, done: true, progress: 100, points: 30, score: 28),
            a("demo-a7", "Kinematics Problem Set", "demo-c3", "Physics", -1, .high, .homework, .blackboard, done: true, progress: 100, points: 50, score: 47),
            a("demo-a8", "Unit 2 Test", "demo-c1", "AP Calculus", 7, .high, .test, .googleClassroom, progress: 0, points: 100),
            a("demo-a9", "Poetry Analysis", "demo-c2", "English Literature", 3, .medium, .homework, .googleClassroom, progress: 15, points: 40),
            a("demo-a10", "Vectors Worksheet", "demo-c3", "Physics", 0, .high, .homework, .blackboard, progress: 0, points: 30),
            a("demo-a11", "Cold War Timeline", "demo-c4", "US History", 5, .medium, .project, .manual, progress: 35, points: 60),
            a("demo-a12", "Integration Practice", "demo-c1", "AP Calculus", -1, .high, .homework, .googleClassroom, progress: 50, points: 50),
            a("demo-a13", "Newton\u{2019}s Laws Lab", "demo-c3", "Physics", 6, .low, .project, .blackboard, progress: 10, points: 70),
            a("demo-a14", "Socratic Seminar Prep", "demo-c2", "English Literature", 2, .medium, .reading, .googleClassroom, progress: 25, points: 35),
        ]
    }

    /// Precomputed streaks with fake (inflated) numbers.
    static var streaks: [ClassStreak] {
        let data: [(Int, StreakStatus, Int, Int, Bool)] = [
            (14, .active, 8, 8, true), (9, .active, 6, 6, true), (5, .atrisk, 7, 6, true), (0, .broken, 5, 3, false),
        ]
        return zip(classes, data).map { c, d in
            ClassStreak(schoolClass: c, verified: d.4, streak: d.0, status: d.1, total: d.2, completed: d.3)
        }
    }

    static let streakSummary = StreakSummary(active: 2, atRisk: 1, broken: 1, best: 14, total: 4)

    static let leaderboard = Leaderboard(entries: [
        LeaderboardEntry(userId: "demo-u1", name: "Aisha K.", bestStreak: 28),
        LeaderboardEntry(userId: "demo-u2", name: "Marcus T.", bestStreak: 22),
        LeaderboardEntry(userId: "demo-self", name: "You", bestStreak: 14),
        LeaderboardEntry(userId: "demo-u3", name: "Sofia R.", bestStreak: 11),
        LeaderboardEntry(userId: "demo-u4", name: "Liam P.", bestStreak: 8),
    ], currentUserId: "demo-self")
}
