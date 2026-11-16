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

    static let webURLKey = "ss_web_url"

    /// The Study Spot website, where users sign in. Defaults to the
    /// `StudySpotWebURL` Info.plist value, else the server address (the usual
    /// setup serves the website and API from one domain).
    static var defaultWebURL: String {
        let configured = (Bundle.main.object(forInfoDictionaryKey: "StudySpotWebURL") as? String) ?? ""
        return configured.isEmpty || configured.hasPrefix("$(") ? serverURL : configured
    }

    static var webURL: String {
        get {
            let stored = UserDefaults.standard.string(forKey: webURLKey) ?? ""
            return normalize(stored.isEmpty ? defaultWebURL : stored)
        }
        set {
            let n = normalize(newValue)
            if n.isEmpty { UserDefaults.standard.removeObject(forKey: webURLKey) }
            else { UserDefaults.standard.set(n, forKey: webURLKey) }
        }
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
