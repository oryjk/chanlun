import SwiftUI

struct RealtimeChartView: View {
    let points: [QuotePoint]
    let previousClose: Double?
    let decimals: Int
    let stock: Stock?
    var body: some View {
        Canvas { context, size in
            guard !points.isEmpty else { return }
            let prices = points.map(\.price) + (previousClose.map { [$0] } ?? [])
            let projection = PriceProjection(minimum: prices.min() ?? 0, maximum: prices.max() ?? 1, top: 20, height: Double(size.height - 50))
            let width = max(1, size.width - 65)
            for fraction in [0.0, 0.5, 1.0] {
                let y = 20 + (size.height - 50) * fraction
                var line = Path(); line.move(to: CGPoint(x: 0, y: y)); line.addLine(to: CGPoint(x: width, y: y))
                context.stroke(line, with: .color(Theme.line), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                context.draw(Text(MarketFormat.price(projection.price(at: Double(y)), decimals: decimals)).font(.system(size: 10)).foregroundColor(.secondary), at: CGPoint(x: width + 5, y: y), anchor: .leading)
            }
            var path = Path()
            for (index, point) in points.enumerated() {
                let position = CGPoint(x: width * CGFloat(MarketTime.tradingMinute(point.time, stock: stock) / MarketTime.session(stock: stock).duration), y: CGFloat(projection.y(point.price)))
                if index == 0 { path.move(to: position) } else { path.addLine(to: position) }
            }
            context.stroke(path, with: .color(Theme.accent), lineWidth: 1.5)
            let labels = MarketTime.axisLabels(stock: stock)
            for (_, fraction) in labels where fraction > 0 && fraction < 1 {
                let x = width * CGFloat(fraction)
                var lunch = Path(); lunch.move(to: CGPoint(x: x, y: 10)); lunch.addLine(to: CGPoint(x: x, y: size.height - 30))
                context.stroke(lunch, with: .color(.gray.opacity(0.5)), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
            }
            for (title, fraction) in labels {
                let x = min(max(20, width * CGFloat(fraction)), width - 20)
                context.draw(Text(title).font(.system(size: 10)).foregroundColor(.secondary), at: CGPoint(x: x, y: size.height - 12))
            }
        }
        .accessibilityLabel("当日分时走势图")
        .accessibilityValue(points.last.map { "最新价格 \(MarketFormat.price($0.price, decimals: decimals))" } ?? "暂无分时数据")
    }
}
