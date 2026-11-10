import SwiftUI

struct RankingView: View {
    @ObservedObject var model: AppModel
    @State private var query = ""
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("全市场收盘结构榜单").font(.headline)
                    if let board = model.board {
                        Text("收盘日期 \(board.tradeDate ?? "待更新")").font(.system(size: 20, weight: .bold))
                        if let date = board.generatedAt {
                            Text("更新时间 \(MarketTime.date(date, format: "yyyy-MM-dd HH:mm:ss")) 北京时间").font(.caption).foregroundStyle(.secondary)
                            Text("已分析 \(board.analyzedCount ?? 0) / \(board.totalCount ?? 0) 只\((board.failedCount ?? 0) > 0 ? "，\(board.failedCount ?? 0) 只暂无有效行情" : "")").font(.footnote)
                        }
                        if board.state == "running" || board.state == "preparing" {
                            Text("后台正在更新，已处理 \(board.progressProcessed ?? 0) / \(board.progressTotal ?? 0) 只").font(.footnote).foregroundStyle(.secondary)
                        }
                        if let error = board.updateError { Text(error).font(.footnote).foregroundStyle(.secondary) }
                    }
                    Text("结构端点日期不等于当时已识别信号，首次识别与确认时间未追溯").font(.caption).foregroundStyle(.secondary)
                    TextField("搜索代码、名称、科创、结论", text: $query).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .font(.subheadline).padding(10).overlay(RoundedRectangle(cornerRadius: 4).stroke(Theme.line))
                }.padding()
                if !model.boardError.isEmpty { ErrorRow(text: model.boardError) { model.reload() } }
                if model.boardLoading { LoadingView(title: "正在读取收盘榜单") }
                if let board = model.board {
                    if board.generatedAt == nil {
                        LoadingView(title: "首份榜单正在准备，后台完成后会自动显示")
                    } else {
                        side("偏多结构", entries: board.bullish)
                        side("偏空结构", entries: board.bearish)
                    }
                }
                Text("结构识别仅供研究，不构成买卖建议").font(.caption).foregroundStyle(.secondary).padding()
            }.frame(maxWidth: 1200).frame(maxWidth: .infinity).background(.white)
        }.background(Theme.page)
    }
    private func side(_ title: String, entries: [RankingEntry]) -> some View {
        let filtered = RankingRules.filter(entries, query: query)
        return VStack(alignment: .leading, spacing: 0) {
            Theme.page.frame(height: 8)
            Text(title).font(.system(size: 17, weight: .semibold)).padding()
            RankingGroupView(title: "已确认同向结构", entries: filtered.filter(\.isConfirmedGroup), query: query) { model.select($0, daily: true) }
            RankingGroupView(title: "候选与其他观察结构", entries: filtered.filter { !$0.isConfirmedGroup }, query: query) { model.select($0, daily: true) }
        }
    }
}

private struct RankingGroupView: View {
    let title: String
    let entries: [RankingEntry]
    let query: String
    let select: (Stock) -> Void
    @State private var visible = 10
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack { Text(title).font(.subheadline.weight(.semibold)); Spacer(); Text("\(entries.count) 只").font(.caption).foregroundStyle(.secondary) }.padding(.horizontal).padding(.vertical, 10)
            if entries.isEmpty { Text(query.isEmpty ? "当前没有符合条件的结构" : "没有匹配结果，请调整关键词").font(.footnote).foregroundStyle(.secondary).padding() }
            ForEach(Array(entries.prefix(visible))) { entry in
                Divider().padding(.leading)
                Button { select(entry.stock) } label: { RankingRow(entry: entry).padding() }.buttonStyle(.plain)
            }
            if entries.count > visible { Button("展开更多（剩余 \(entries.count - visible) 只）") { visible += 10 }.font(.footnote).frame(maxWidth: .infinity).padding() }
        }.onChange(of: query) { _, _ in visible = 10 }
    }
}

private struct RankingRow: View {
    let entry: RankingEntry
    private func price(_ value: Double) -> String { MarketFormat.price(value, decimals: entry.stock.decimals) }
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(alignment: .firstTextBaseline) {
                Text("\(entry.rank)").font(.caption).foregroundStyle(.secondary).frame(minWidth: 18, alignment: .leading)
                Text(entry.stock.name).font(.system(size: 15, weight: .semibold))
                Spacer()
                Text(price(entry.latest)).font(.system(size: 17, weight: .bold)).monospacedDigit().foregroundStyle(Theme.direction(entry.changePercent))
                Text(MarketFormat.percent(entry.changePercent)).font(.caption).monospacedDigit().foregroundStyle(Theme.direction(entry.changePercent))
            }
            Text("\(entry.stock.symbol)  \(entry.stock.market)  \(entry.stock.symbol.hasPrefix("688") || entry.stock.symbol.hasPrefix("689") ? "科创  " : "")强度 \(entry.strength)").font(.caption).foregroundStyle(.secondary)
            Text(SignalRules.conclusion(bias: entry.bias, latest: entry.latest, point: entry.latestPoint, quality: entry.signalQuality).title).font(.subheadline.weight(.medium))
            if let trend = entry.trend, trend.count >= 2 { RankingSparkline(entry: entry, trend: trend).frame(height: 72) }
            if let point = entry.buyPoint ?? (entry.latestPoint?.side == "buy" ? entry.latestPoint : nil) { pointRow(point) }
            if let point = entry.sellPoint ?? (entry.latestPoint?.side == "sell" ? entry.latestPoint : nil) { pointRow(point) }
            if let reference = entry.signalQuality?.invalidationPrice {
                let change = entry.latest > 0 ? (reference - entry.latest) / entry.latest * 100 : 0
                Text("信号失效参考 \(price(reference))（距现价 \(MarketFormat.percent(change))）").font(.caption).foregroundStyle(.secondary)
            } else { Text("信号自身失效参考待补齐").font(.caption).foregroundStyle(.secondary) }
            Text(entry.signalQuality?.barsSincePivot.map { "距结构端点 \($0) 根已收盘 K 线" } ?? "端点年龄待计算").font(.caption).foregroundStyle(.secondary)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
    private func pointRow(_ point: SignalPoint) -> some View {
        let change = point.price > 0 ? (entry.latest - point.price) / point.price * 100 : 0
        return Text("\(point.label)结构端点 \(MarketTime.date(point.time))  \(point.confirmed ? "已确认" : "待确认")  较端点价 \(MarketFormat.percent(change))").font(.caption).foregroundStyle(.secondary)
    }
}

private struct RankingSparkline: View {
    let entry: RankingEntry
    let trend: [QuotePoint]
    var body: some View {
        Canvas { context, size in
            let markers = [entry.buyPoint, entry.sellPoint].compactMap { $0 }
            let prices = trend.map(\.price) + markers.map(\.price)
            let projection = PriceProjection(minimum: prices.min() ?? 0, maximum: prices.max() ?? 1, top: 16, height: Double(size.height - 32))
            func x(_ index: Int) -> CGFloat { CGFloat(index) / CGFloat(max(1, trend.count - 1)) * max(1, size.width - 12) + 6 }
            var path = Path()
            for (index, point) in trend.enumerated() {
                let position = CGPoint(x: x(index), y: CGFloat(projection.y(point.price)))
                if index == 0 { path.move(to: position) } else { path.addLine(to: position) }
            }
            context.stroke(path, with: .color(Theme.accent), lineWidth: 1.3)
            for marker in markers {
                guard let index = trend.indices.min(by: { abs(trend[$0].time.timeIntervalSince(marker.time)) < abs(trend[$1].time.timeIntervalSince(marker.time)) }) else { continue }
                let title = "\(marker.side == "buy" ? "买" : "卖") \(MarketFormat.price(marker.price, decimals: entry.stock.decimals))  \(MarketTime.date(marker.time, format: "MM-dd"))"
                let color = marker.side == "buy" ? Theme.up : Theme.down
                let labelX = min(max(65, x(index)), max(65, size.width - 65))
                context.draw(Text(title).font(.system(size: 9)).foregroundColor(color), at: CGPoint(x: labelX, y: marker.side == "buy" ? size.height - 5 : 5))
                let dot = CGRect(x: x(index) - 2, y: CGFloat(projection.y(marker.price)) - 2, width: 4, height: 4)
                context.fill(Path(ellipseIn: dot), with: .color(color))
            }
        }.accessibilityLabel("近期走势，标记日期为结构端点日期")
    }
}
