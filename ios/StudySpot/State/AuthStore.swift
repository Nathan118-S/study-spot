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

    var isTwoFactorVerified: Bool {
        guard let token = api.token else { return false }
        return Keychain.get(Keychain.twoFactorVerifiedKey) == token
    }

    var needsTwoFactor: Bool {
        guard let user else { return false }
        return user.twofaEnabled && !user.enabledTwoFactorMethods.isEmpty && !isTwoFactorVerified
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

    func markTwoFactorVerified() {
        if let token = api.token { Keychain.set(token, for: Keychain.twoFactorVerifiedKey) }
        // Trigger observation so the gate re-evaluates.
        let u = user
        user = u
        phase = .signedIn
    }

    func signOut() {
        api.setToken(nil)
        Keychain.delete(Keychain.twoFactorVerifiedKey)
        user = nil
        phase = .signedOut
    }
}
