import Foundation

protocol SessionStore {
    func token() throws -> String?
    func save(_ token: String) throws
    func clear() throws
}
protocol AuthReader { func session() async throws -> AppSession }
protocol AuthCommands {
    func challenge() async throws -> LoginChallenge
    func login(challenge: String, credential: AppleCredential) async throws -> AppLoginResult
    func logout() async throws
    func deleteAccount() async throws
}
@MainActor protocol AppleAuthorizing {
    func authorize(nonce: String, state: String) async throws -> AppleCredential
}

@MainActor
struct AuthUseCases {
    let reader: any AuthReader
    let commands: any AuthCommands
    let authorizer: any AppleAuthorizing
    let store: any SessionStore

    func login() async throws -> AppLoginResult {
        let challenge = try await commands.challenge()
        let credential = try await authorizer.authorize(nonce: challenge.nonce, state: challenge.state)
        return try await commands.login(challenge: challenge.challengeId, credential: credential)
    }
    func persist(_ result: AppLoginResult) throws { try store.save(result.token) }
    func logout() async throws {
        // Server revocation first, local removal even when offline.
        do { try await commands.logout() }
        catch { try store.clear(); throw error }
        try store.clear()
    }
    func deleteAccount() async throws {
        try await commands.deleteAccount()
        try store.clear()
    }
}
