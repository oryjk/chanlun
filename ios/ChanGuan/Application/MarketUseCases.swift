import Foundation

protocol MarketReader {
    func search(_ query: String) async throws -> [Stock]
    func snapshot(secid: String, timeframe: Timeframe, refresh: Bool) async throws -> MarketSnapshot
    func quote(secid: String) async throws -> MarketQuote
    func series(secid: String) async throws -> QuoteSeries
    func rankings() async throws -> RankingBoard
    func confluence(secid: String) async throws -> SignalConfluence
}
protocol QuoteWriter { func record(_ quote: MarketQuote) async throws -> QuoteSeries }

struct MarketUseCases {
    let reader: any MarketReader
    let writer: any QuoteWriter
}

protocol RouteStore {
    func restore() -> (Stock?, Timeframe)
    func save(stock: Stock?, timeframe: Timeframe)
}
