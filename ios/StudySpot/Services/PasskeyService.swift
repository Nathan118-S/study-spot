import AuthenticationServices
import UIKit

enum Base64URL {
    static func encode(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func decode(_ s: String) -> Data? {
        var b = s.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b.count % 4 != 0 { b += "=" }
        return Data(base64Encoded: b)
    }
}

/// Native passkeys (Face ID / Touch ID) using the server's WebAuthn endpoints.
/// Requires the associated domain "webcredentials:<server host>" and the
/// server's /.well-known/apple-app-site-association (set IOS_APP_ID).
final class PasskeyService: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<ASAuthorization, Error>?
    private let api = APIClient.shared

    /// passkeyRegisterStart → platform credential → passkeyRegisterFinish.
    @MainActor
    func register() async throws {
        let opts = try await api.invoke("passkeyRegisterStart")
        guard let challenge = opts["challenge"]?.stringValue.flatMap(Base64URL.decode),
              let userId = opts["user"]?["id"]?.stringValue.flatMap(Base64URL.decode) else {
            throw APIError(status: 0, message: "Invalid passkey options from server")
        }
        let name = opts["user"]?["name"]?.stringValue ?? "Study Spot"
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: AppConfig.passkeyRelyingParty)
        let request = provider.createCredentialRegistrationRequest(challenge: challenge, name: name, userID: userId)
        let auth = try await perform(request)
        guard let cred = auth.credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration,
              let attestation = cred.rawAttestationObject else {
            throw APIError(status: 0, message: "Passkey setup failed")
        }
        try await api.invoke("passkeyRegisterFinish", [
            "credentialId": .string(Base64URL.encode(cred.credentialID)),
            "attestationObject": .string(Base64URL.encode(attestation)),
            "clientDataJSON": .string(Base64URL.encode(cred.rawClientDataJSON)),
        ])
    }

    /// passkeyLoginStart → assertion → passkeyLoginFinish. Returns the
    /// server's response (which carries a full session token).
    @MainActor
    @discardableResult
    func verify() async throws -> JSONValue {
        let opts = try await api.invoke("passkeyLoginStart")
        guard let challenge = opts["challenge"]?.stringValue.flatMap(Base64URL.decode) else {
            throw APIError(status: 0, message: "Invalid passkey options from server")
        }
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: AppConfig.passkeyRelyingParty)
        let request = provider.createCredentialAssertionRequest(challenge: challenge)
        request.allowedCredentials = (opts["allowCredentials"]?.arrayValue ?? []).compactMap {
            $0["id"]?.stringValue.flatMap(Base64URL.decode).map(ASAuthorizationPlatformPublicKeyCredentialDescriptor.init(credentialID:))
        }
        let auth = try await perform(request)
        guard let cred = auth.credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion else {
            throw APIError(status: 0, message: "Passkey verification failed")
        }
        return try await api.invoke("passkeyLoginFinish", [
            "credentialId": .string(Base64URL.encode(cred.credentialID)),
            "authenticatorData": .string(Base64URL.encode(cred.rawAuthenticatorData)),
            "clientDataJSON": .string(Base64URL.encode(cred.rawClientDataJSON)),
            "signature": .string(Base64URL.encode(cred.signature)),
        ])
    }

    @MainActor
    private func perform(_ request: ASAuthorizationRequest) async throws -> ASAuthorization {
        try await withCheckedThrowingContinuation { c in
            continuation = c
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        continuation?.resume(returning: authorization)
        continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated { UIApplication.shared.activeKeyWindow ?? ASPresentationAnchor() }
    }
}
