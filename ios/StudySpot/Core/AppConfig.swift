import Foundation

/// App-wide configuration. The server address defaults to the
/// `StudySpotServerURL` Info.plist value and can be changed on the sign-in
/// screen, so one build works with any self-hosted Study Spot server.
enum AppConfig {
    static let serverURLKey = "ss_server_url"
    static let urlScheme = "studyspot"

    static var defaultServerURL: String {
        let configured = (Bundle.main.object(forInfoDictionaryKey: "StudySpotServerURL") as? String) ?? ""
        return configured.isEmpty || configured.hasPrefix("$(") ? "http://localhost:8787" : configured
    }

    /// Base URL of the server, e.g. "https://studyspot.example.com".
    static var serverURL: String {
        get {
            let stored = UserDefaults.standard.string(forKey: serverURLKey) ?? ""
            return normalize(stored.isEmpty ? defaultServerURL : stored)
        }
        set { UserDefaults.standard.set(normalize(newValue), forKey: serverURLKey) }
    }

    /// The API lives under /api on the server.
    static var apiBaseURL: String {
        let base = serverURL
        return base.hasSuffix("/api") ? base : base + "/api"
    }

    /// Relying-party ID used for passkeys: the server's host. The app must list
    /// it as an associated domain ("webcredentials:<host>") — see ios/README.md.
    static var passkeyRelyingParty: String {
        URL(string: serverURL)?.host ?? "localhost"
    }

    static func normalize(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        if !s.isEmpty, !s.lowercased().hasPrefix("http://"), !s.lowercased().hasPrefix("https://") {
            s = "https://" + s
        }
        return s
    }
}
