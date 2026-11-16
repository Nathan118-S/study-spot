import AuthenticationServices
import UIKit

/// Runs browser-based OAuth (Google sign-in, Google Calendar/Classroom,
/// Blackboard) in an ASWebAuthenticationSession and returns the URL the
/// server redirected back to on the app's custom scheme.
@MainActor
final class WebAuthSession: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = WebAuthSession()
    private var current: ASWebAuthenticationSession?

    struct Cancelled: Error {}

    func start(_ url: URL, ephemeral: Bool = false) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: AppConfig.urlScheme) { callback, error in
                if let callback {
                    continuation.resume(returning: callback)
                } else if let err = error as? ASWebAuthenticationSessionError, err.code == .canceledLogin {
                    continuation.resume(throwing: Cancelled())
                } else {
                    continuation.resume(throwing: error ?? Cancelled())
                }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = ephemeral
            current = session
            if !session.start() {
                continuation.resume(throwing: APIError(status: 0, message: "Couldn't open the sign-in page."))
            }
        }
    }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated { UIApplication.shared.activeKeyWindow ?? ASPresentationAnchor() }
    }
}

extension UIApplication {
    var activeKeyWindow: UIWindow? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
    }
}

extension URL {
    /// Reads a value from the query string or the #fragment.
    func param(_ name: String) -> String? {
        let comps = URLComponents(url: self, resolvingAgainstBaseURL: false)
        if let v = comps?.queryItems?.first(where: { $0.name == name })?.value { return v }
        if let fragment = comps?.fragment {
            var f = URLComponents()
            f.query = fragment
            return f.queryItems?.first(where: { $0.name == name })?.value
        }
        return nil
    }
}
