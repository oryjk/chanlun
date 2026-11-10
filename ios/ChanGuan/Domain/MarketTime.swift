import Foundation

enum MarketTime {
    static let shanghai = TimeZone(identifier: "Asia/Shanghai")!
    struct Session {
        let zone: TimeZone
        let windows: [(Int, Int)]
        let label: String
        var duration: Double { Double(windows.reduce(0) { $0 + $1.1 - $1.0 }) }
    }
    static func session(stock: Stock? = nil) -> Session {
        let market = stock?.market.uppercased() ?? ""
        if market.contains("美") || market.contains("US") {
            return Session(zone: TimeZone(identifier: "America/New_York")!, windows: [(570, 960)], label: "纽约时间")
        }
        if market.contains("港") || market.contains("HK") {
            return Session(zone: TimeZone(identifier: "Asia/Hong_Kong")!, windows: [(570, 720), (780, 960)], label: "香港时间")
        }
        return Session(zone: shanghai, windows: [(570, 690), (780, 900)], label: "北京时间")
    }
    static func zone(currency: String?) -> TimeZone {
        currency == "USD" ? TimeZone(identifier: "America/New_York")! : currency == "HKD" ? TimeZone(identifier: "Asia/Hong_Kong")! : shanghai
    }
    static func date(_ value: Date, format: String = "yyyy-MM-dd", zone: TimeZone = shanghai) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.timeZone = zone
        formatter.dateFormat = format
        return formatter.string(from: value)
    }
    static func isOpen(_ value: Date = Date(), stock: Stock? = nil) -> Bool {
        let session = session(stock: stock)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = session.zone
        let c = calendar.dateComponents([.weekday, .hour, .minute], from: value)
        guard let day = c.weekday, (2...6).contains(day) else { return false }
        let minute = (c.hour ?? 0) * 60 + (c.minute ?? 0)
        return session.windows.contains { ($0.0..<$0.1).contains(minute) }
    }
    static func tradingMinute(_ value: Date, stock: Stock? = nil) -> Double {
        let session = session(stock: stock)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = session.zone
        let c = calendar.dateComponents([.hour, .minute, .second], from: value)
        let minute = Double((c.hour ?? 0) * 60 + (c.minute ?? 0)) + Double(c.second ?? 0) / 60
        var elapsed = 0.0
        for (start, end) in session.windows {
            if minute < Double(end) { return elapsed + max(0, minute - Double(start)) }
            elapsed += Double(end - start)
        }
        return elapsed
    }
    static func clock(_ minute: Int) -> String { String(format: "%02d:%02d", minute / 60, minute % 60) }
    static func axisLabels(stock: Stock?) -> [(String, Double)] {
        let session = session(stock: stock)
        guard let first = session.windows.first, let last = session.windows.last else { return [] }
        var labels = [(clock(first.0), 0.0)]
        if session.windows.count > 1 {
            labels.append(("\(clock(first.1)) / \(clock(session.windows[1].0))", Double(first.1 - first.0) / session.duration))
        }
        labels.append((clock(last.1), 1.0))
        return labels
    }
}
