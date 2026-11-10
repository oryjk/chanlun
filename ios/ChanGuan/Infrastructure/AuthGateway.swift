import Foundation

final class AuthGateway: AuthReader, AuthCommands {
    private let api: APIClient
    private struct Ack: Decodable { let ok: Bool }
    init(api: APIClient) { self.api = api }
    func session() async throws -> AppSession { try await api.send("api/app-auth/session", authenticated: true) }
    func challenge() async throws -> LoginChallenge { try await api.send("api/app-auth/challenge", body: Data("{}".utf8)) }
    func login(challenge: String, credential: AppleCredential) async throws -> AppLoginResult {
        struct Body: Encodable { let challengeId, identityToken, authorizationCode: String; let nickname: String? }
        return try await api.send("api/app-auth/apple", body: APIClient.encode(Body(challengeId: challenge, identityToken: credential.identityToken, authorizationCode: credential.authorizationCode, nickname: credential.nickname)))
    }
    func logout() async throws { let _: Ack = try await api.send("api/app-auth/logout", body: Data("{}".utf8), authenticated: true) }
    func deleteAccount() async throws { let _: Ack = try await api.send("api/app-auth/delete-account", body: Data("{}".utf8), authenticated: true) }
}
