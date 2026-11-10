import SwiftUI

struct MarketView: View {
    @ObservedObject var model: AppModel
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var selection: ChartSelection?
    @State private var showTerms = false
    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                header.padding()
                Divider()
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 24) {
                        ForEach(Timeframe.allCases) { timeframe in
                            Button { selection = nil; model.changeTimeframe(timeframe) } label: {
                                VStack(spacing: 8) {
                                    Text(timeframe.label).font(.system(size: 14, weight: model.timeframe == timeframe ? .semibold : .regular))
                                    Rectangle().fill(model.timeframe == timeframe ? Theme.accent : .clear).frame(height: 2)
                                }
                            }.buttonStyle(.plain)
                        }
                    }.padding(.horizontal).padding(.top, 12)
                }
                if !model.error.isEmpty { ErrorRow(text: model.error) { model.reload(refresh: true) } }
                if model.loading { LoadingView(title: "正在读取行情与结构") }
                if model.timeframe == .realtime {
                    QuoteView(model: model)
                } else if let snapshot = model.snapshot {
                    chartLayers.padding(.horizontal).padding(.vertical, 12)
                    if let selection, snapshot.candles.indices.contains(selection.index) {
                        candleDetails(snapshot, index: selection.index).padding(.horizontal)
                    } else {
                        Text("左右拖动查看历史，双指缩放，长按查看详情").font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal)
                    }
                    MarketChartBridge(snapshot: snapshot, layers: model.layers, decimals: model.stock?.decimals ?? 2) { selection = $0 }
                        .frame(height: sizeClass == .regular ? 510 : 380)
                    sectionBreak
                    SignalPanel(snapshot: snapshot, decimals: model.stock?.decimals ?? 2).padding()
                    sectionBreak
                    VStack(alignment: .leading, spacing: 12) {
                        Text("多级别联立").font(.headline)
                        if model.confluenceLoading { ProgressView("正在比对四个周期").font(.footnote) }
                        if let confluence = model.confluence {
                            Text(confluence.conclusion).font(.subheadline)
                            ForEach(confluence.levels, id: \.timeframe) { level in
                                HStack(alignment: .top) {
                                    Text(level.timeframeLabel).frame(width: 64, alignment: .leading)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(level.latestPoint.map { "\($0.label)  \($0.confirmed ? "已确认" : "候选")" } ?? "暂无买卖点")
                                        Text(level.lowerConfirmation == nil ? "暂无已确认的次级别印证" : "已形成同向结构印证").foregroundStyle(.secondary)
                                    }
                                }.font(.footnote)
                            }
                        }
                        if !model.confluenceError.isEmpty { Text(model.confluenceError).font(.footnote).foregroundStyle(.secondary) }
                    }.frame(maxWidth: .infinity, alignment: .leading).padding()
                    sectionBreak
                    VStack(alignment: .leading, spacing: 10) {
                        HStack { Text("行情区间").font(.headline); Spacer(); Button("术语说明") { showTerms = true }.font(.footnote) }
                        Text("区间最高 \(price(snapshot.summary.high))   区间最低 \(price(snapshot.summary.low))").font(.footnote)
                        Text("近 20 根量能 \(MarketFormat.volume(snapshot.summary.volume))").font(.footnote).foregroundStyle(.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading).padding()
                } else if !model.loading {
                    Text("暂无行情，请搜索证券或点击右上角刷新").font(.subheadline).foregroundStyle(.secondary).padding(30)
                }
                Text("结构识别仅供研究，不构成买卖建议").font(.caption).foregroundStyle(.secondary).padding()
            }.frame(maxWidth: 1200).frame(maxWidth: .infinity).background(.white)
        }
        .background(Theme.page)
        .sheet(isPresented: $showTerms) { TermInfoView() }
        .onChange(of: model.stock?.secid) { _, _ in selection = nil }
    }
    private var sectionBreak: some View { Theme.page.frame(height: 8) }
    private func price(_ value: Double) -> String { MarketFormat.price(value, decimals: model.stock?.decimals ?? 2) }
    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(model.stock?.name ?? "选择证券").font(.system(size: 20, weight: .semibold)).lineLimit(1)
                Text(model.stock?.symbol ?? "").font(.caption).foregroundStyle(.secondary)
                Spacer()
            }
            let latest = model.timeframe == .realtime ? model.quote?.latest : model.snapshot?.summary.latest
            let change = model.timeframe == .realtime ? model.quote?.changePercent : model.snapshot?.summary.changePercent
            HStack(alignment: .firstTextBaseline, spacing: 16) {
                Text(latest.map(price) ?? "—").font(.system(size: 28, weight: .bold, design: .rounded)).monospacedDigit()
                Text(change.map(MarketFormat.percent) ?? "—").font(.subheadline).monospacedDigit()
                Spacer()
                Text(model.timeframe.label).font(.caption).foregroundStyle(.secondary)
            }.foregroundStyle(Theme.direction(change ?? 0)).frame(height: 36)
        }
    }
    private var chartLayers: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 17) {
                layer("分型", key: \.fractals, color: Color(ChartPalette.fractal))
                layer("笔", key: \.pens, color: Color(ChartPalette.gold))
                layer("线段", key: \.segments, color: Theme.accent)
                layer("笔中枢", key: \.zhongshu, color: Color(ChartPalette.violet))
                layer("买卖点", key: \.points, color: Theme.up)
            }
        }
    }
    private func layer(_ title: String, key: WritableKeyPath<ChartLayers, Bool>, color: Color) -> some View {
        Button { model.layers[keyPath: key].toggle() } label: {
            HStack(spacing: 4) {
                Rectangle().fill(color).frame(width: 13, height: title == "笔中枢" ? 6 : 2)
                Text(title).font(.caption)
            }.opacity(model.layers[keyPath: key] ? 1 : 0.35)
        }.buttonStyle(.plain).accessibilityLabel("\(title)图层，\(model.layers[keyPath: key] ? "已开启" : "已关闭")")
    }
    private func candleDetails(_ snapshot: MarketSnapshot, index: Int) -> some View {
        let candle = snapshot.candles[index]
        let base = index > 0 ? snapshot.candles[index - 1].close : candle.open
        return VStack(alignment: .leading, spacing: 4) {
            Text(MarketTime.date(candle.time, format: "yyyy-MM-dd HH:mm", zone: MarketTime.zone(currency: snapshot.instrument.currency))).font(.caption).foregroundStyle(.secondary)
            Text("开 \(price(candle.open))  高 \(price(candle.high))  低 \(price(candle.low))  收 \(price(candle.close))").font(.caption).monospacedDigit()
            Text("涨跌 \(MarketFormat.percent(base > 0 ? (candle.close - base) / base * 100 : 0))  量 \(MarketFormat.volume(candle.volume))").font(.caption).foregroundStyle(Theme.direction(candle.close - base))
            if snapshot.macd.indices.contains(index) {
                let point = snapshot.macd[index]
                Text("DIF \(String(format: "%.3f", point.dif))  DEA \(String(format: "%.3f", point.dea))  MACD \(String(format: "%.3f", point.histogram))").font(.caption).monospacedDigit()
            }
            ForEach(Array(snapshot.signals.points.filter { $0.candleIndex == index }.enumerated()), id: \.offset) { _, point in
                Text("\(point.label) \(point.confirmed ? "结构已确认" : "候选")  首次识别与确认时间未追溯").font(.caption)
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
