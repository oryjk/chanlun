import Foundation

enum QuoteSeriesRules {
    static func merge(_ current: [QuotePoint], _ incoming: [QuotePoint], stock: Stock, reference: Date) -> [QuotePoint] {
        let zone = MarketTime.session(stock: stock).zone
        let day = MarketTime.date(reference, zone: zone)
        var values: [Date: QuotePoint] = [:]
        for point in current + incoming where MarketTime.date(point.time, zone: zone) == day {
            values[point.time] = point
        }
        return values.values.sorted { $0.time < $1.time }
    }
}
