import XCTest
@testable import ChanGuan

final class DTOTests: XCTestCase {
    func testLegacyRankingMissingQualityStaysObservation() throws {
        let data = Data("""
        {"rank":1,"stock":{"secid":"600519.SH","symbol":"600519","name":"贵州茅台","market":"沪A"},"bias":"bullish","strength":3,"latest":1500,"change_percent":1,"invalidation_price":1400}
        """.utf8)
        let entry = try JSONDecoder.market.decode(RankingEntry.self, from: data)
        XCTAssertNil(entry.signalQuality)
        XCTAssertFalse(entry.isConfirmedGroup)
        XCTAssertEqual(entry.stock.secid, "600519.SH")
    }

    func testISO8601AcceptsFractionalAndWholeSeconds() throws {
        struct Sample: Decodable { let time: Date }
        for text in ["2026-11-10T15:00:00+08:00", "2026-11-10T07:00:00.123Z"] {
            XCTAssertNoThrow(try JSONDecoder.market.decode(Sample.self, from: Data("{\"time\":\"\(text)\"}".utf8)))
        }
    }
}
