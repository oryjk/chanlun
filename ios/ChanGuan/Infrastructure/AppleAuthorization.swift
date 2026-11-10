import AuthenticationServices
import CryptoKit
import UIKit

@MainActor
final class AppleAuthorization: NSObject, AppleAuthorizing, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<AppleCredential, Error>?
    private var controller: ASAuthorizationController?
    private var expectedState = ""
    private let store: any SessionStore
    init(store: any SessionStore) { self.store = store; super.init() }

    func authorize(nonce: String, state: String) async throws -> AppleCredential {
        guard continuation == nil else { throw APIError.response(409, "登录正在进行，请稍候") }
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
        request.state = state
        expectedState = state
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            self.controller = controller
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              credential.state == expectedState,
              let tokenData = credential.identityToken, let token = String(data: tokenData, encoding: .utf8),
              let codeData = credential.authorizationCode, let code = String(data: codeData, encoding: .utf8) else {
            finish(.failure(APIError.response(401, "Apple 登录信息不完整，请重试")))
            return
        }
        let name = credential.fullName.map { PersonNameComponentsFormatter().string(from: $0) }
        UserDefaults.standard.set(credential.user, forKey: "apple-credential-user")
        finish(.success(AppleCredential(identityToken: token, authorizationCode: code, nickname: name)))
    }
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish(.failure(error))
    }
    private func finish(_ result: Result<AppleCredential, Error>) {
        let callback = continuation
        continuation = nil
        controller = nil
        expectedState = ""
        callback?.resume(with: result)
    }
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?.windows.first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }

    func credentialWasRevoked() async -> Bool {
        guard let user = UserDefaults.standard.string(forKey: "apple-credential-user") else { return false }
        let state: ASAuthorizationAppleIDProvider.CredentialState? = try? await withCheckedThrowingContinuation { continuation in
            ASAuthorizationAppleIDProvider().getCredentialState(forUserID: user) { state, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: state) }
            }
        }
        guard state == .revoked || state == .notFound else { return false }
        return true
    }
}
