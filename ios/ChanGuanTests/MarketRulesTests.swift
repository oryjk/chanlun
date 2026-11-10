import XCTest
@testable import ChanGuan

final class MarketRulesTests: XCTestCase {
    func testConclusionsMatchSharedH5Contracts() throws {
        struct Sample: Decodable {
            let name, bias, expectedLabel: String
            let latest: Double
            let point: SignalPoint?
            let quality: SignalQuality?
        }
        struct Contract: Decodable { let cases: [Sample] }
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "client_contract", withExtension: "json"))
        let contract = try JSONDecoder.market.decode(Contract.self, from: Data(contentsOf: url))
        for sample in contract.cases {
            XCTAssertEqual(SignalRules.conclusion(bias: sample.bias, latest: sample.latest, point: sample.point, quality: sample.quality).title, sample.expectedLabel, sample.name)
        }
    }
    func testETFPrecisionAndStockPrecision() {
        XCTAssertEqual(MarketFormat.price(1.234, decimals: 3), "1.234")
        XCTAssertEqual(MarketFormat.price(1.234, decimals: 2), "1.23")
    }

    func testCandidateNeverBecomesConfirmedConclusion() throws {
        let point = try JSONDecoder.market.decode(SignalPoint.self, from: Data("""
        {"kind":3,"side":"buy","pen_id":1,"candle_index":0,"time":"2026-11-10T15:00:00+08:00","price":10,"confirmed":false,"reason":"test"}
        """.utf8))
        XCTAssertEqual(SignalRules.conclusion(bias: "bullish", latest: 10, point: point, quality: nil).title, "候选买点，等待确认")
    }

    func testHistoryInvalidationWinsOverCandidateAndRecoveredPrice() throws {
        let point = try JSONDecoder.market.decode(SignalPoint.self, from: Data("""
        {"kind":3,"side":"buy","pen_id":1,"candle_index":0,"time":"2026-11-10T15:00:00+08:00","price":10,"confirmed":false,"reason":"test"}
        """.utf8))
        let quality = try JSONDecoder.market.decode(SignalQuality.self, from: Data("{\"state\":\"invalidated\"}".utf8))
        XCTAssertEqual(SignalRules.conclusion(bias: "bullish", latest: 20, point: point, quality: quality).title, "买点结构已失效")
    }

    func testShanghaiDateDoesNotFollowDeviceTimeZone() {
        let date = ISO8601DateFormatter().date(from: "2026-11-09T23:30:00Z")!
        XCTAssertEqual(MarketTime.date(date), "2026-11-10")
    }
    func testUSSessionUsesNewYorkTimeWithoutLunchBreak() {
        let stock = Stock(secid: "opaque", symbol: "AAPL", name: "Apple", market: "美股")
        let open = ISO8601DateFormatter().date(from: "2026-11-10T14:30:00Z")!
        XCTAssertTrue(MarketTime.isOpen(open, stock: stock))
        XCTAssertFalse(MarketTime.isOpen(open))
        XCTAssertEqual(MarketTime.session(stock: stock).duration, 390)
        XCTAssertEqual(MarketTime.axisLabels(stock: stock).count, 2)
    }
    func testQuoteMergeDropsPreviousMarketDayAndDeduplicatesTimestamp() {
        let stock = Stock(secid: "opaque", symbol: "600519", name: "test", market: "沪A")
        let old = ISO8601DateFormatter().date(from: "2026-11-09T15:00:00+08:00")!
        let now = ISO8601DateFormatter().date(from: "2026-11-10T09:30:00+08:00")!
        let values = QuoteSeriesRules.merge([.init(time: old, price: 9), .init(time: now, price: 10)], [.init(time: now, price: 11)], stock: stock, reference: now)
        XCTAssertEqual(values.count, 1)
        XCTAssertEqual(values.first?.price, 11)
    }
}
