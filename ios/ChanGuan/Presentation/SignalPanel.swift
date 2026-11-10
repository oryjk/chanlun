import SwiftUI

struct SignalPanel: View {
    let snapshot: MarketSnapshot
    let decimals: Int
    private func price(_ value: Double) -> String { MarketFormat.price(value, decimals: decimals) }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack { Text("结构解读").font(.headline); Spacer(); Text(SignalRules.trend(snapshot.signals.trendType)).font(.footnote).foregroundStyle(.secondary) }
            let conclusion = SignalRules.conclusion(bias: snapshot.guidance.bias, latest: snapshot.summary.latest, point: snapshot.signals.points.last, quality: snapshot.signalQuality)
            Text(conclusion.title).font(.system(size: 16, weight: .semibold))
            Text(conclusion.detail).font(.footnote).foregroundStyle(.secondary)
            ForEach(snapshot.guidance.reasons, id: \.self) { reason in Text(reason).font(.footnote) }
            if let pen = snapshot.analysis.pens.last {
                Text("最近一笔 \(pen.direction == "up" ? "向上" : "向下")  \(pen.confirmed ? "已确认" : "候选")  \(price(pen.startPrice)) → \(price(pen.endPrice))").font(.footnote)
            }
            if let zone = snapshot.analysis.zhongshu.last {
                VStack(alignment: .leading, spacing: 5) {
                    Text("当前周期笔中枢 \(zone.id)  \(zone.confirmed ? "已确认" : "候选")").font(.subheadline.weight(.medium))
                    Text("核心下沿 \(price(zone.low))  核心上沿 \(price(zone.high))").font(.footnote).monospacedDigit()
                    Text("核心宽度 \(price(zone.high - zone.low))  波动范围 \(price(zone.dd))–\(price(zone.gg))").font(.footnote).foregroundStyle(.secondary)
                }
            }
            if let quality = snapshot.signalQuality {
                Divider()
                if let reference = quality.invalidationPrice { Text("该信号失效参考 \(price(reference))").font(.subheadline).monospacedDigit() }
                if let date = quality.firstReferenceCrossedAt {
                    Text("首次收盘越界 \(MarketTime.date(date))  收盘 \(quality.firstReferenceCrossedClose.map(price) ?? "—")").font(.footnote)
                }
                if let bars = quality.barsSincePivot { Text("距结构端点 \(bars) 根已收盘 K 线").font(.footnote) }
                if let date = quality.historyCheckedThrough { Text("历史核对至 \(MarketTime.date(date, format: "yyyy-MM-dd HH:mm"))").font(.caption).foregroundStyle(.secondary) }
                Text(quality.historyStatus == "checked_from_pivot" ? "端点起现有已收盘行情已核对" : quality.historyStatus == "invalidated" ? "历史已发生收盘越界" : "历史区间尚未完整核对").font(.footnote)
                if quality.referenceTouched == true { Text("本根 K 线盘中已触及参考，收盘越界另行核对").font(.footnote) }
                HStack {
                    if let ratio = quality.macdAreaRatio { Text("MACD 面积比 \(String(format: "%.2f", ratio))") }
                    if let ratio = quality.pullbackMacdRatio { Text("回抽动能比 \(String(format: "%.2f", ratio))") }
                }.font(.caption)
                HStack {
                    if let ratio = quality.breakoutVolumeRatio { Text("前段量比 \(String(format: "%.2f", ratio))") }
                    if let ratio = quality.pullbackVolumeRatio { Text("回抽量比 \(String(format: "%.2f", ratio))") }
                }.font(.caption)
                Text("动能与量能为待验证证据，不代表成功概率").font(.caption).foregroundStyle(.secondary)
            }
            Divider()
            Text("最近买卖点").font(.subheadline.weight(.semibold))
            if snapshot.signals.points.isEmpty { Text("当前结构尚未出现买卖点").font(.footnote).foregroundStyle(.secondary) }
            ForEach(Array(snapshot.signals.points.suffix(4).reversed().enumerated()), id: \.offset) { _, point in
                VStack(alignment: .leading, spacing: 4) {
                    HStack { Text(point.label).foregroundStyle(point.side == "buy" ? Theme.up : Theme.down); Text(point.confirmed ? "已确认" : "候选").foregroundStyle(.secondary); Spacer(); Text(price(point.price)).monospacedDigit() }
                    Text("结构端点 \(MarketTime.date(point.time, format: snapshot.timeframe == "1d" ? "yyyy-MM-dd" : "MM-dd HH:mm"))").foregroundStyle(.secondary)
                    Text(point.reason).foregroundStyle(.secondary)
                }.font(.footnote)
            }
            ForEach(Array(snapshot.signals.divergences.suffix(2).enumerated()), id: \.offset) { _, divergence in
                Text("\(divergence.kind == "trend" ? "趋势" : "盘整")\(divergence.direction == "bottom" ? "底" : "顶")背驰  \(divergence.confirmed ? "已确认" : "候选")").font(.footnote)
            }
            Text("图中日期为结构端点日期，首次识别与确认时间未追溯").font(.caption).foregroundStyle(.secondary)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
