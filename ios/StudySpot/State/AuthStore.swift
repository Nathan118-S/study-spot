import Foundation
import Observation

@MainActor
@Observable
final class AuthStore {
    enum Phase: Equatable {
        case loading
        case signedOut
        case unreachable(String)
        case signedIn
    }

    var phase: Phase = .loading
    var user: User?
    /// Shown on the login screen after a disabled account is signed out.
    var disabledReason: String?
    /// Set once per app launch so the welcome greeting shows on login/open.
    var hasGreetedThisLaunch = false

    private let api = APIClient.shared

    /// The server marks a sign-in as pending until the second factor is
    /// verified; nothing else works until then.
    var needsTwoFactor: Bool {
        guard let user else { return false }
        return user.mfaPending && !user.enabledTwoFactorMethods.isEmpty
    }

    var needsOnboarding: Bool {
        guard let user else { return false }
        return !user.isDemo && !user.onboardingCompleted
    }

    func check() async {
        guard api.token != nil else {
            phase = .signedOut
            return
        }
        if user == nil { phase = .loading }
        do {
            let me = try await api.me()
            if me.role == "disabled" {
                disabledReason = me.disabledReason ?? ""
                signOut()
                return
            }
            user = me
            phase = .signedIn
            // Reminders and do-not-disturb are scheduled in the user's time zone.
            let tz = TimeZone.current.identifier
            if !me.mfaPending, me.data["timezone"]?.stringValue != tz {
                Task { try? await update(["timezone": .string(tz)]) }
            }
        } catch let e as APIError where e.isAuthError {
            signOut()
        } catch {
            phase = user == nil ? .unreachable(error.localizedDescription) : .signedIn
        }
    }

    /// Re-fetch the user (after a settings change) without flashing a loader.
    func refreshUser() async {
        if let me = (try? await api.me()) { user = me }
    }

    func update(_ patch: [String: JSONValue]) async throws {
        let updated = try await api.updateMe(patch)
        user = updated
    }

    func didSignIn() async {
        disabledReason = nil
        hasGreetedThisLaunch = false
        await check()
    }

    /// Verification endpoints return a full session token that replaces the
    /// pending one.
    func completeTwoFactor(_ response: JSONValue) async {
        if let token = response["access_token"]?.stringValue, !token.isEmpty {
            api.setToken(token)
        }
        await refreshUser()
    }

    func signOut() {
        let outgoing = api.token
        Task { await ClassActivityManager.unregister(bearer: outgoing) }
        Task { await PushNotifications.unregister(bearer: outgoing) }
        api.setToken(nil)
        user = nil
        phase = .signedOut
    }
}
