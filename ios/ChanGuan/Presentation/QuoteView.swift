import SwiftUI

struct QuoteView: View {
    @ObservedObject var model: AppModel
    var body: some View {
        VStack(spacing: 14) {
            if !model.series.isEmpty {
                RealtimeChartView(points: model.series, previousClose: model.quote?.previousClose, decimals: model.stock?.decimals ?? 2, stock: model.stock).frame(height: 280)
            } else if !model.loading { Text("暂无当日分时数据，点击刷新重试").font(.footnote).foregroundStyle(.secondary).frame(height: 180) }
            if let quote = model.quote {
                let decimals = model.stock?.decimals ?? 2
                let fields: [(String, String)] = [
                    ("今开", MarketFormat.price(quote.open, decimals: decimals)), ("昨收", MarketFormat.price(quote.previousClose, decimals: decimals)),
                    ("最高", MarketFormat.price(quote.high, decimals: decimals)), ("最低", MarketFormat.price(quote.low, decimals: decimals)),
                    ("成交量", MarketFormat.volume(quote.volume)), ("成交额", MarketFormat.volume(quote.amount))]
                LazyVGrid(columns: [.init(.flexible()), .init(.flexible())], spacing: 14) {
                    ForEach(fields, id: \.0) { title, value in HStack { Text(title).foregroundStyle(.secondary); Spacer(); Text(value).monospacedDigit() }.font(.footnote) }
                }
                Text("行情时间 \(MarketTime.date(quote.time, format: "yyyy-MM-dd HH:mm:ss", zone: MarketTime.session(stock: model.stock).zone)) \(MarketTime.session(stock: model.stock).label)").font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
            }
        }.padding()
    }
}
