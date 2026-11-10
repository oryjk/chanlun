import Foundation

enum RankingRules {
    static func filter(_ entries: [RankingEntry], query: String) -> [RankingEntry] {
        let keyword = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !keyword.isEmpty else { return entries }
        return entries.filter { entry in
            let title = SignalRules.conclusion(bias: entry.bias, latest: entry.latest, point: entry.latestPoint, quality: entry.signalQuality).title
            let star = entry.stock.symbol.hasPrefix("688") || entry.stock.symbol.hasPrefix("689") ? "科创板" : ""
            let state = entry.latestPoint.map { $0.confirmed ? "已确认" : "待确认" } ?? ""
            return "\(entry.stock.name) \(entry.stock.symbol) \(entry.stock.market) \(star) \(state) \(title)".lowercased().contains(keyword)
        }
    }
}
