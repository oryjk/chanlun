import Foundation

struct Stock: Codable, Hashable, Identifiable {
    let secid: String
    let symbol: String
    let name: String
    let market: String
    var id: String { secid }
    var decimals: Int { market == "ETF" ? 3 : 2 }
}

enum Timeframe: String, CaseIterable, Identifiable {
    case fiveMinutes = "5m", thirtyMinutes = "30m", oneHour = "1h", daily = "1d", realtime
    var id: String { rawValue }
    var label: String {
        switch self {
        case .fiveMinutes: return "5 分钟"
        case .thirtyMinutes: return "30 分钟"
        case .oneHour: return "1 小时"
        case .daily: return "日线"
        case .realtime: return "实时"
        }
    }
}

struct Candle: Decodable {
    let time: Date
    let open, close, high, low, volume: Double
}

struct Instrument: Decodable {
    let secid, symbol, name: String
    let decimal: Int
    let currency, exchange: String?
}

struct MarketSummary: Decodable {
    let latest, change, changePercent, high, low, volume: Double
}

struct Fractal: Decodable {
    let type: String
    let candleIndex: Int
    let time: Date
    let price: Double
}

struct Pen: Decodable, Identifiable {
    let id, startIndex, endIndex: Int
    let direction: String
    let startTime, endTime: Date
    let startPrice, endPrice, high, low: Double
    let confirmed: Bool
}

struct Segment: Decodable, Identifiable {
    let id, startIndex, endIndex: Int
    let direction: String
    let startTime, endTime: Date
    let startPrice, endPrice, high, low: Double
    let confirmed: Bool
}

struct Zhongshu: Decodable, Identifiable {
    let id, startIndex, endIndex: Int
    let startTime, endTime: Date
    let low, high, gg, dd: Double
    let confirmed: Bool
}

struct ChanAnalysis: Decodable {
    let fractals: [Fractal]
    let pens: [Pen]
    let segments: [Segment]
    let zhongshu: [Zhongshu]
    let mergedCount: Int
    let trend: String
}

struct MacdPoint: Decodable { let dif, dea, histogram: Double }

struct SignalPoint: Decodable {
    let kind: Int
    let side: String
    let penId, candleIndex: Int
    let time: Date
    let price: Double
    let confirmed: Bool
    let reason: String
    let invalidationPrice: Double?
    let sourceZoneId: Int?
    var label: String { "\([1: "一", 2: "二", 3: "三"][kind] ?? String(kind))类\(side == "buy" ? "买" : "卖")点" }
}

struct Divergence: Decodable {
    let kind, direction: String
    let time: Date
    let price: Double
    let confirmed: Bool
}

struct ChanSignals: Decodable {
    let trendType: String
    let divergences: [Divergence]
    let points: [SignalPoint]
}

struct SignalQuality: Decodable {
    let state: String?
    let invalidationPrice: Double?
    let momentumStatus, volumeStatus, historyStatus: String?
    let macdHistogram, macdAreaRatio, pullbackMacdRatio: Double?
    let breakoutVolumeRatio, pullbackVolumeRatio: Double?
    let referenceTouched: Bool?
    let barsSincePivot: Int?
    let firstReferenceCrossedAt: Date?
    let firstReferenceCrossedClose: Double?
    let historyCheckedThrough, firstDetectedAt, confirmedAt: Date?
    let reasons: [String]?
}

struct MarketGuidance: Decodable {
    let bias: String
    let reasons: [String]
    let invalidationPrice: Double?
    let strength: Int
}

struct MarketSnapshot: Decodable {
    let instrument: Instrument
    let timeframe, timeframeLabel: String
    let candles: [Candle]
    let analysis: ChanAnalysis
    let summary: MarketSummary
    let guidance: MarketGuidance
    let macd: [MacdPoint]
    let signals: ChanSignals
    let signalQuality: SignalQuality?
}

struct QuotePoint: Decodable { let time: Date; let price: Double }
struct QuoteSeries: Decodable { let secid: String; let points: [QuotePoint] }
struct MarketQuote: Decodable {
    let secid, symbol, name: String
    let time: Date
    let open, high, low, previousClose, latest, change, changePercent, volume, amount: Double
}

struct LevelSignal: Decodable {
    let timeframe, timeframeLabel, trendType: String
    let latestPoint, lowerConfirmation: SignalPoint?
}
struct SignalConfluence: Decodable { let secid, conclusion: String; let levels: [LevelSignal] }

struct RankingEntry: Decodable, Identifiable {
    let rank: Int
    let stock: Stock
    let bias: String
    let strength: Int
    let latest, changePercent: Double
    let invalidationPrice: Double?
    let latestPoint, buyPoint, sellPoint: SignalPoint?
    let trend: [QuotePoint]?
    let signalQuality: SignalQuality?
    let signalGroup: String?
    var id: String { stock.secid }
    var isConfirmedGroup: Bool { signalGroup == "confirmed" }
}

struct RankingBoard: Decodable {
    let generatedAt: Date?
    let timeframe: String
    let bullish, bearish: [RankingEntry]
    let tradeDate: String?
    let totalCount, analyzedCount, failedCount, progressTotal, progressProcessed: Int?
    let state, updateError: String?
    let analysisVersion: Int?
}

struct AppUser: Codable, Equatable { let userId, nickname: String }
struct AppSession: Decodable { let authenticated: Bool; let user: AppUser? }
struct LoginChallenge: Decodable { let challengeId, nonce, state: String; let expiresAt: Date }
struct AppleCredential { let identityToken, authorizationCode: String; let nickname: String? }
struct AppLoginResult: Decodable { let user: AppUser; let token: String; let expiresAt: Date }
