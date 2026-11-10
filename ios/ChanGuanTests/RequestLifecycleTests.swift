import XCTest
@testable import ChanGuan

private struct MemoryRoutes: RouteStore {
    let stock: Stock?
    func restore() -> (Stock?, Timeframe) { (stock, .daily) }
    func save(stock: Stock?, timeframe: Timeframe) { }
}

private actor DelayedMarket: MarketReader, QuoteWriter {
    var pending: [String: CheckedContinuation<MarketSnapshot, Error>] = [:]
    var searchPending: CheckedContinuation<[Stock], Error>?
    func search(_ query: String) async throws -> [Stock] {
        try await withCheckedThrowingContinuation { searchPending = $0 }
    }
    func hasSearch() -> Bool { searchPending != nil }
    func finishSearch(_ stock: Stock) { searchPending?.resume(returning: [stock]); searchPending = nil }
    func snapshot(secid: String, timeframe: Timeframe, refresh: Bool) async throws -> MarketSnapshot {
        try await withCheckedThrowingContinuation { pending[secid] = $0 }
    }
    func hasPending(_ id: String) -> Bool { pending[id] != nil }
    func finish(_ id: String) throws {
        let json = """
        {"instrument":{"secid":"\(id)","symbol":"test","name":"test","decimal":2},"timeframe":"1d","timeframe_label":"日线","candles":[],"analysis":{"fractals":[],"pens":[],"segments":[],"zhongshu":[],"merged_count":0,"trend":"none"},"summary":{"latest":10,"change":0,"change_percent":0,"high":10,"low":10,"volume":0},"guidance":{"bias":"neutral","reasons":[],"strength":0},"macd":[],"signals":{"trend_type":"none","divergences":[],"points":[]}}
        """
        let value = try JSONDecoder.market.decode(MarketSnapshot.self, from: Data(json.utf8))
        pending.removeValue(forKey: id)?.resume(returning: value)
    }
    func confluence(secid: String) async throws -> SignalConfluence { .init(secid: secid, conclusion: "test", levels: []) }
    func quote(secid: String) async throws -> MarketQuote { throw CancellationError() }
    func series(secid: String) async throws -> QuoteSeries { .init(secid: secid, points: []) }
    func rankings() async throws -> RankingBoard { throw CancellationError() }
    func record(_ quote: MarketQuote) async throws -> QuoteSeries { .init(secid: quote.secid, points: []) }
}

@MainActor
final class RequestLifecycleTests: XCTestCase {
    private func checkStartup(background: Bool) async throws {
        let reader = DelayedMarket(), gateway = OfflineAuth()
        let stock = Stock(secid: "A", symbol: "600519", name: "test", market: "沪A")
        let model = AppModel(market: .init(reader: reader, writer: reader), auth: .init(reader: gateway, commands: gateway, authorizer: CancelledApple(), store: MemorySession()), routes: MemoryRoutes(stock: nil))
        model.activate(true)
        let startup = Task { await model.start() }
        for _ in 0..<100 { if await reader.hasSearch() { break }; await Task.yield() }
        let searching = await reader.hasSearch()
        XCTAssertTrue(searching)
        if background { model.activate(false) }
        await reader.finishSearch(stock)
        await startup.value
        for _ in 0..<100 {
            if await reader.hasPending("A") { break }
            await Task.yield()
        }
        let hasPending = await reader.hasPending("A")
        XCTAssertEqual(hasPending, !background)
        if hasPending { try await reader.finish("A") }
        model.activate(false)
        XCTAssertFalse(model.loading)
    }
    func testDelayedBootstrapLoadsWhenAlreadyActive() async throws { try await checkStartup(background: false) }
    func testDelayedBootstrapDoesNotReactivateBackgroundPolling() async throws { try await checkStartup(background: true) }
    func testLatePreviousStockResponseCannotOverwriteNewSelection() async throws {
        let reader = DelayedMarket()
        let a = Stock(secid: "A", symbol: "A", name: "A", market: "沪A")
        let b = Stock(secid: "B", symbol: "B", name: "B", market: "沪A")
        let gateway = OfflineAuth()
        let model = AppModel(market: .init(reader: reader, writer: reader), auth: .init(reader: gateway, commands: gateway, authorizer: CancelledApple(), store: MemorySession()), routes: MemoryRoutes(stock: a))
        model.activate(true)
        for _ in 0..<100 {
            if await reader.hasPending("A") { break }
            await Task.yield()
        }
        let pendingA = await reader.hasPending("A")
        XCTAssertTrue(pendingA)
        model.select(b)
        for _ in 0..<100 {
            if await reader.hasPending("B") { break }
            await Task.yield()
        }
        try await reader.finish("B")
        for _ in 0..<100 { if model.snapshot != nil { break }; await Task.yield() }
        XCTAssertEqual(model.snapshot?.instrument.secid, "B")
        try await reader.finish("A")
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.snapshot?.instrument.secid, "B")
        model.activate(false)
        XCTAssertFalse(model.loading)
    }
}
