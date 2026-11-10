import XCTest
@testable import ChanGuan

final class ChartGeometryTests: XCTestCase {
    func testPriceProjectionRoundTripAndConstantPrices() {
        let projection = PriceProjection(minimum: 10, maximum: 10, top: 20, height: 100)
        XCTAssertTrue(projection.y(10).isFinite)
        XCTAssertEqual(projection.price(at: projection.y(10)), 10, accuracy: 0.000001)
    }

    func testViewportStaysInsideDataAndSupportsSingleBar() {
        var viewport = ChartViewport(count: 1)
        viewport.pan(bars: 100, count: 1)
        XCTAssertEqual(viewport.lower, 0)
        XCTAssertEqual(viewport.upper, 1)
        viewport.zoom(scale: 3, anchor: 0.5, count: 1)
        XCTAssertEqual(viewport.upper, 1)
    }

    func testLunchBreakHasNoHorizontalDuration() {
        let morning = ISO8601DateFormatter().date(from: "2026-11-10T11:30:00+08:00")!
        let afternoon = ISO8601DateFormatter().date(from: "2026-11-10T13:00:00+08:00")!
        XCTAssertEqual(MarketTime.tradingMinute(morning), MarketTime.tradingMinute(afternoon))
    }
}
