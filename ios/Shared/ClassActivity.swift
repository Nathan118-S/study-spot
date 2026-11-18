import Foundation
#if canImport(ActivityKit)
import ActivityKit

/// "Class starts soon" Live Activity: appears 10 minutes before a class with
/// a circular countdown to its start and anything due that day.
///
/// The server starts it with an ActivityKit push-to-start (see
/// server/src/jobs/classActivities.js), so the JSON field names and types
/// here must match that payload. Times are Unix seconds (not Date) so the
/// push JSON decodes the same way everywhere.
struct ClassStartAttributes: ActivityAttributes {
    struct DueItem: Codable, Hashable {
        var title: String
        var className: String
    }

    struct ContentState: Codable, Hashable {
        var startsAt: Double          // Unix seconds
        var dueToday: [DueItem]       // up to 3
        var dueCount: Int

        var startDate: Date { Date(timeIntervalSince1970: startsAt) }
        /// The countdown ring runs over the 10 minutes before class.
        var countdownStart: Date { startDate.addingTimeInterval(-ClassStartAttributes.leadTime) }
    }

    static let leadTime: TimeInterval = 10 * 60

    var classId: String
    var className: String
    var subject: String
    var room: String
    var colorHex: String
}
#endif
