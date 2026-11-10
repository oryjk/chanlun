import Foundation

final class MarketGateway: MarketReader, QuoteWriter {
    private let api: APIClient
    init(api: APIClient) { self.api = api }
    func search(_ query: String) async throws -> [Stock] {
        struct Result: Decodable { let items: [Stock] }
        let result: Result = try await api.send("api/stocks/search", query: [.init(name: "q", value: query)])
        return result.items
    }
    func snapshot(secid: String, timeframe: Timeframe, refresh: Bool = false) async throws -> MarketSnapshot {
        try await api.send("api/chan", query: [.init(name: "secid", value: secid), .init(name: "timeframe", value: timeframe.rawValue), .init(name: "refresh", value: String(refresh))])
    }
    func quote(secid: String) async throws -> MarketQuote {
        try await api.send("api/quote", query: [.init(name: "secid", value: secid)])
    }
    func series(secid: String) async throws -> QuoteSeries {
        try await api.send("api/quote/series", query: [.init(name: "secid", value: secid)])
    }
    func rankings() async throws -> RankingBoard {
        try await api.send("api/rankings", query: [.init(name: "limit", value: "500")])
    }
    func confluence(secid: String) async throws -> SignalConfluence {
        try await api.send("api/chan/confluence", query: [.init(name: "secid", value: secid)])
    }
    func record(_ quote: MarketQuote) async throws -> QuoteSeries {
        struct Sample: Encodable { let secid: String; let time: Date; let price: Double }
        return try await api.send("api/quote/samples", body: APIClient.encode(Sample(secid: quote.secid, time: quote.time, price: quote.latest)))
    }
}
