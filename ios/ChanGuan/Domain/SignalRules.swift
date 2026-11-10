import Foundation

enum MarketFormat {
    static func price(_ value: Double, decimals: Int) -> String {
        guard value.isFinite else { return "—" }
        return String(format: "%.*f", locale: Locale(identifier: "en_US_POSIX"), decimals, value)
    }
    static func percent(_ value: Double) -> String { String(format: "%+.2f%%", value) }
    static func volume(_ value: Double) -> String {
        if value >= 100_000_000 { return String(format: "%.2f 亿", value / 100_000_000) }
        if value >= 10_000 { return String(format: "%.1f 万", value / 10_000) }
        return String(format: "%.0f", value)
    }
}

struct SignalConclusion { let title, detail: String }
enum SignalRules {
    static func trend(_ value: String) -> String {
        ["up_trend":"上涨趋势", "down_trend":"下跌趋势", "consolidation":"盘整", "none":"结构不足"][value] ?? value
    }
    static func conclusion(bias: String, latest: Double, point: SignalPoint?, quality: SignalQuality?) -> SignalConclusion {
        if let point, quality?.state == "invalidated" || quality?.historyStatus == "invalidated" || quality?.firstReferenceCrossedAt != nil {
            return .init(title: "\(point.side == "buy" ? "买" : "卖")点结构已失效", detail: "已有收盘价越过该信号对应的失效参考，即使价格恢复也不继续沿用原信号")
        }
        if let point, !point.confirmed {
            return .init(title: point.side == "buy" ? "候选买点，等待确认" : "候选卖点，注意风险", detail: "结构端点仍可能修正，候选状态不能作为已确认的买卖依据")
        }
        let aligned = point?.side == (bias == "bullish" ? "buy" : bias == "bearish" ? "sell" : "")
        if aligned, quality?.state != "confirmed" { return .init(title: "结构已确认，证据待补齐", detail: "尚不能核对该信号的有效性或当前 K 线未收盘，等待完整评价") }
        if aligned, quality?.historyStatus != "checked_from_pivot" { return .init(title: "结构已确认，历史待核对", detail: "尚未核对端点起的已收盘 K 线是否曾越界，暂列观察") }
        if aligned { return .init(title: point?.side == "buy" ? "买点结构已确认" : "卖点结构已确认", detail: "仅代表结构确认，量价辅助条件尚未验证；需另行制定入场、退出与仓位规则") }
        if bias == "bullish" {
            if let point, point.side == "sell" {
                return latest > point.price ? .init(title: "刚突破中枢，等回踩买点", detail: "现价已站上中枢上沿并突破前期卖点，等待回踩不破确认买点") : .init(title: "出现卖点，暂不买入", detail: "价格虽在中枢上方，但最近信号为卖点，注意冲高回落风险")
            }
            return .init(title: "偏多结构，等待买点", detail: "价格位于中枢上方，等待回抽出现买点信号")
        }
        if bias == "bearish" {
            if let point, point.side == "buy" {
                return latest < point.price ? .init(title: "刚跌破中枢，等反抽确认", detail: "现价已跌破中枢下沿及前期买点，结构转弱，暂宜规避") : .init(title: "低位买点，观察筑底", detail: "中枢下方出现买点信号，需收复中枢下沿确认转强")
            }
            return .init(title: "偏空结构，暂不买入", detail: "价格位于中枢下方，尚未出现明确买点信号")
        }
        return .init(title: "中枢震荡，暂不操作", detail: "价格尚未离开笔中枢核心区间或方向未达成一致，等待突破信号")
    }
}
