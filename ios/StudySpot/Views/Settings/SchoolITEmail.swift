import SwiftUI

/// A ready-to-send email asking a school's IT team to approve Study Spot,
/// for when the school blocks connecting third-party apps. Opens in the
/// user's mail app; they add the recipient and the school's name.
/// (The website has the same text in src/lib/schoolItEmail.js.)
enum SchoolITEmail {
    enum Service { case google, blackboard, both }

    static let placeholder = "[School name]"

    static func senderName(_ user: User?) -> String {
        let n = (user?.fullName ?? user?.name ?? "").trimmingCharacters(in: .whitespaces)
        if !n.isEmpty { return n }
        let u = (user?.username ?? "").split(separator: "@").first.map(String.init) ?? ""
        return u.isEmpty ? "A student" : u
    }

    private static func what(_ s: Service) -> String {
        switch s {
        case .google: return "my school Google account (Google Calendar and Google Classroom)"
        case .blackboard: return "my Blackboard account"
        case .both: return "my school Google account (Google Calendar and Google Classroom) and my Blackboard account"
        }
    }

    private static let googleDetails = [
        "Study Spot only asks for read-only access to Google Calendar and to my Google Classroom courses and coursework (and, if I choose, marking my own Classroom work as turned in). It doesn't ask for access to Gmail or Drive.",
        "In the Google Admin console this is under Security → Access and data control → API controls → App access control, where Study Spot can be marked as trusted.",
    ]
    private static let blackboardDetails = [
        "Study Spot uses Blackboard's REST API to read my courses and the due dates in the gradebook. It doesn't change anything in Blackboard.",
        "In Blackboard this is done by an administrator under REST API Integrations.",
    ]

    static func url(user: User?, service: Service) -> URL? {
        let name = senderName(user)
        var details: [String] = []
        if service != .blackboard { details += googleDetails + [""] }
        if service != .google { details += blackboardDetails + [""] }
        let body = ([
            "Hello \(placeholder) IT team,",
            "",
            "My name is \(name), and I'm a student at \(placeholder). I use Study Spot (https://studyspot.education), a study planner that brings my assignments and due dates into one place so I can keep up with my schoolwork.",
            "",
            "When I try to connect \(what(service)), it looks like the school has to approve third-party apps first. Could you please approve Study Spot so students can connect it?",
            "",
        ] + details + ["Thank you for your help!", "", name]).joined(separator: "\n")
        var c = URLComponents()
        c.scheme = "mailto"
        c.queryItems = [
            URLQueryItem(name: "subject", value: "Request to approve Study Spot at \(placeholder)"),
            URLQueryItem(name: "body", value: body),
        ]
        return c.url
    }
}

/// Shown before connecting Google or Blackboard: many schools restrict which
/// third-party apps can access school accounts. Offers a drafted email to
/// the school's IT team.
struct SchoolAccessNote: View {
    var service: SchoolITEmail.Service
    @Environment(AuthStore.self) private var auth
    @Environment(\.openURL) private var openURL
    @State private var confirming = false

    private var label: String {
        switch service {
        case .google: return "school Google"
        case .blackboard: return "Blackboard"
        case .both: return "school Google or Blackboard"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label {
                Text("Some schools block connecting third-party apps like Study Spot to \(label) accounts. If the connection is blocked or fails, ask your school's IT team, or add your assignments manually.")
            } icon: {
                Image(systemName: "info.circle")
            }
            Button {
                confirming = true
            } label: {
                Label("Email my school's IT team", systemImage: "envelope")
            }
            .font(.footnote.weight(.semibold))
            .buttonStyle(.borderless)
        }
        .font(.footnote)
        .foregroundStyle(Palette.secondary)
        .alert("Before you send it", isPresented: $confirming) {
            Button("Cancel", role: .cancel) {}
            Button("Open email") {
                if let url = SchoolITEmail.url(user: auth.user, service: service) { openURL(url) }
            }
        } message: {
            Text("We'll open a drafted email in your mail app asking your school to approve Study Spot, signed with your name.\n\nReplace every “\(SchoolITEmail.placeholder)” with your school's name, and add your IT team's email address as the recipient.")
        }
    }
}
