import XCTest
@testable import ChanGuan

final class MemorySession: SessionStore {
    var value: String?
    func token() throws -> String? { value }
    func save(_ token: String) throws { value = token }
    func clear() throws { value = nil }
}

struct OfflineAuth: AuthReader, AuthCommands {
    func session() async throws -> AppSession { .init(authenticated: false, user: nil) }
    func challenge() async throws -> LoginChallenge { throw URLError(.notConnectedToInternet) }
    func login(challenge: String, credential: AppleCredential) async throws -> AppLoginResult { throw URLError(.notConnectedToInternet) }
    func logout() async throws { throw URLError(.notConnectedToInternet) }
    func deleteAccount() async throws { throw URLError(.notConnectedToInternet) }
}

@MainActor final class CancelledApple: AppleAuthorizing {
    func authorize(nonce: String, state: String) async throws -> AppleCredential { throw CancellationError() }
}

@MainActor private final class ImmediateApple: AppleAuthorizing {
    func authorize(nonce: String, state: String) async throws -> AppleCredential {
        .init(identityToken: "identity", authorizationCode: "code", nickname: nil)
    }
}

@MainActor private final class SuspendedAuth: AuthReader, AuthCommands {
    var pendingSession: CheckedContinuation<AppSession, Error>?
    var pendingLogin: CheckedContinuation<AppLoginResult, Error>?
    func session() async throws -> AppSession { try await withCheckedThrowingContinuation { pendingSession = $0 } }
    func challenge() async throws -> LoginChallenge { .init(challengeId: "challenge", nonce: "nonce", state: "state", expiresAt: .distantFuture) }
    func login(challenge: String, credential: AppleCredential) async throws -> AppLoginResult { try await withCheckedThrowingContinuation { pendingLogin = $0 } }
    func logout() async throws { }
    func deleteAccount() async throws { }
}

private struct EmptyRoutes: RouteStore {
    func restore() -> (Stock?, Timeframe) { (nil, .daily) }
    func save(stock: Stock?, timeframe: Timeframe) { }
}
private struct UnusedMarket: MarketReader, QuoteWriter {
    func search(_ query: String) async throws -> [Stock] { [] }
    func snapshot(secid: String, timeframe: Timeframe, refresh: Bool) async throws -> MarketSnapshot { throw CancellationError() }
    func quote(secid: String) async throws -> MarketQuote { throw CancellationError() }
    func series(secid: String) async throws -> QuoteSeries { .init(secid: secid, points: []) }
    func rankings() async throws -> RankingBoard { throw CancellationError() }
    func confluence(secid: String) async throws -> SignalConfluence { .init(secid: secid, conclusion: "test", levels: []) }
    func record(_ quote: MarketQuote) async throws -> QuoteSeries { .init(secid: quote.secid, points: []) }
}

@MainActor
final class AuthFlowTests: XCTestCase {
    private func model(_ gateway: SuspendedAuth, store: MemorySession) -> AppModel {
        let market = UnusedMarket()
        return AppModel(market: .init(reader: market, writer: market), auth: .init(reader: gateway, commands: gateway, authorizer: ImmediateApple(), store: store), routes: EmptyRoutes())
    }
    func testOfflineLogoutClearsLocalSession() async {
        let store = MemorySession(); store.value = "token"
        let gateway = OfflineAuth()
        let auth = AuthUseCases(reader: gateway, commands: gateway, authorizer: CancelledApple(), store: store)
        do { try await auth.logout(); XCTFail("Expected offline error") } catch { }
        XCTAssertNil(store.value)
    }
    func testOfflineDeletionKeepsSessionForRetry() async {
        let store = MemorySession(); store.value = "token"
        let gateway = OfflineAuth()
        let auth = AuthUseCases(reader: gateway, commands: gateway, authorizer: CancelledApple(), store: store)
        do { try await auth.deleteAccount(); XCTFail("Expected offline error") } catch { }
        XCTAssertEqual(store.value, "token")
    }
    func testRevokedCredentialCannotBeRestoredByLateSessionResponse() async throws {
        let gateway = SuspendedAuth(), store = MemorySession()
        let model = model(gateway, store: store)
        let restore = Task { await model.restoreSession() }
        for _ in 0..<100 { if gateway.pendingSession != nil { break }; await Task.yield() }
        XCTAssertNotNil(gateway.pendingSession)
        model.credentialsRevoked()
        gateway.pendingSession?.resume(returning: .init(authenticated: true, user: .init(userId: "old", nickname: "old")))
        await restore.value
        XCTAssertNil(model.user)
        XCTAssertNil(store.value)
    }
    func testRevokedCredentialCannotBeReplacedByLateLoginToken() async throws {
        let gateway = SuspendedAuth(), store = MemorySession()
        let model = model(gateway, store: store)
        let login = Task { await model.login() }
        for _ in 0..<100 { if gateway.pendingLogin != nil { break }; await Task.yield() }
        XCTAssertNotNil(gateway.pendingLogin)
        model.credentialsRevoked()
        gateway.pendingLogin?.resume(returning: .init(user: .init(userId: "old", nickname: "old"), token: "late-token", expiresAt: .distantFuture))
        await login.value
        XCTAssertNil(model.user)
        XCTAssertNil(store.value)
    }
}
