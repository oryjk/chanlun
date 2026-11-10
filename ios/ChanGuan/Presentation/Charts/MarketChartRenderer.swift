import UIKit

struct ChartSelection {
    let index: Int
    let time: Date
    let price: Double
}

enum MarketChartRenderer {
    static func text(_ value: String, at point: CGPoint, color: UIColor = .secondaryLabel, size: CGFloat = 10, weight: UIFont.Weight = .regular) {
        (value as NSString).draw(at: point, withAttributes: [.font: UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight), .foregroundColor: color])
    }
    static func line(_ c: CGContext, _ a: CGPoint, _ b: CGPoint, color: UIColor, width: CGFloat = 1, dashed: Bool = false) {
        c.setStrokeColor(color.cgColor); c.setLineWidth(width)
        c.setLineDash(phase: 0, lengths: dashed ? [4, 3] : [])
        c.move(to: a); c.addLine(to: b); c.strokePath()
        c.setLineDash(phase: 0, lengths: [])
    }
    static func draw(_ c: CGContext, rect: CGRect, snapshot: MarketSnapshot, viewport: ChartViewport,
                     layers: ChartLayers, decimals: Int, selected: Int?) {
        let candles = snapshot.candles
        guard !candles.isEmpty, rect.width > 90, rect.height > 100 else { return }
        let left: CGFloat = 10
        let width = rect.width - 80
        let priceRect = CGRect(x: left, y: 22, width: width, height: rect.height * 0.51)
        let volumeRect = CGRect(x: left, y: rect.height * 0.61, width: width, height: rect.height * 0.12)
        let macdRect = CGRect(x: left, y: rect.height * 0.79, width: width, height: rect.height * 0.13)
        let first = max(0, min(candles.count - 1, Int(viewport.lower)))
        let end = max(first + 1, min(candles.count, Int(ceil(viewport.upper))))
        let visible = candles[first..<end]
        let projection = PriceProjection(minimum: visible.map(\.low).min() ?? 0, maximum: visible.map(\.high).max() ?? 1,
                                         top: Double(priceRect.minY), height: Double(priceRect.height))
        func x(_ index: Int) -> CGFloat { left + CGFloat((Double(index) + 0.5 - viewport.lower) / viewport.span) * width }
        func y(_ price: Double) -> CGFloat { CGFloat(projection.y(price)) }
        let step = width / CGFloat(viewport.span)
        let barWidth = max(1, min(14, step * 0.68))
        for fraction in [0.0, 0.25, 0.5, 0.75, 1.0] {
            let level = priceRect.minY + CGFloat(fraction) * priceRect.height
            line(c, CGPoint(x: left, y: level), CGPoint(x: priceRect.maxX, y: level), color: .systemGray5, dashed: true)
            text(MarketFormat.price(projection.price(at: Double(level)), decimals: decimals), at: CGPoint(x: priceRect.maxX + 5, y: level - 6))
        }
        let maxVolume = max(1, visible.map(\.volume).max() ?? 1)
        let visibleMacd = Array(snapshot.macd.dropFirst(first).prefix(end - first))
        let maxMacd = max(0.001, visibleMacd.map { max(abs($0.histogram), max(abs($0.dif), abs($0.dea))) }.max() ?? 1)
        let macdCenter = macdRect.midY
        func macdY(_ value: Double) -> CGFloat { macdCenter - CGFloat(value / maxMacd) * macdRect.height / 2 }
        text("成交量", at: CGPoint(x: left, y: volumeRect.minY - 15))
        text(MarketFormat.volume(maxVolume), at: CGPoint(x: volumeRect.maxX + 5, y: volumeRect.minY))
        text("MACD 12/26/9", at: CGPoint(x: left, y: macdRect.minY - 17))
        line(c, CGPoint(x: left, y: macdCenter), CGPoint(x: macdRect.maxX, y: macdCenter), color: .systemGray4)
        c.saveGState()
        c.clip(to: CGRect(x: left, y: priceRect.minY, width: width, height: macdRect.maxY - priceRect.minY))
        if layers.zhongshu {
            c.saveGState(); c.clip(to: priceRect)
            for zone in snapshot.analysis.zhongshu {
                let area = CGRect(x: x(zone.startIndex) - step / 2, y: y(zone.high), width: max(step, x(zone.endIndex) - x(zone.startIndex) + step), height: max(1, y(zone.low) - y(zone.high)))
                c.setFillColor(ChartPalette.violet.withAlphaComponent(0.10).cgColor)
                c.fill(area)
                c.setStrokeColor(ChartPalette.violet.withAlphaComponent(0.55).cgColor)
                c.setLineDash(phase: 0, lengths: zone.confirmed ? [] : [4, 3]); c.stroke(area); c.setLineDash(phase: 0, lengths: [])
            }
            c.restoreGState()
        }
        for index in first..<end {
            let candle = candles[index]
            let color = candle.close >= candle.open ? ChartPalette.up : ChartPalette.down
            line(c, CGPoint(x: x(index), y: y(candle.high)), CGPoint(x: x(index), y: y(candle.low)), color: color)
            c.setFillColor(color.cgColor)
            c.fill(CGRect(x: x(index) - barWidth / 2, y: min(y(candle.open), y(candle.close)), width: barWidth, height: max(1, abs(y(candle.open) - y(candle.close)))))
            let volumeHeight = CGFloat(candle.volume / maxVolume) * volumeRect.height
            c.fill(CGRect(x: x(index) - barWidth / 2, y: volumeRect.maxY - volumeHeight, width: barWidth, height: volumeHeight))
            if index < snapshot.macd.count {
                let point = snapshot.macd[index]
                line(c, CGPoint(x: x(index), y: macdCenter), CGPoint(x: x(index), y: macdY(point.histogram)), color: point.histogram >= 0 ? ChartPalette.up : ChartPalette.down, width: max(1, barWidth / 2))
                if index > first, index - 1 < snapshot.macd.count {
                    let previous = snapshot.macd[index - 1]
                    line(c, CGPoint(x: x(index - 1), y: macdY(previous.dif)), CGPoint(x: x(index), y: macdY(point.dif)), color: ChartPalette.gold)
                    line(c, CGPoint(x: x(index - 1), y: macdY(previous.dea)), CGPoint(x: x(index), y: macdY(point.dea)), color: ChartPalette.accent)
                }
            }
        }
        // Structure strokes draw over candles; annotations draw in a final pass below.
        c.saveGState(); c.clip(to: priceRect)
        if layers.segments {
            for segment in snapshot.analysis.segments {
                line(c, CGPoint(x: x(segment.startIndex), y: y(segment.startPrice)), CGPoint(x: x(segment.endIndex), y: y(segment.endPrice)), color: ChartPalette.accent, width: 2.4, dashed: !segment.confirmed)
            }
        }
        if layers.pens {
            for pen in snapshot.analysis.pens {
                line(c, CGPoint(x: x(pen.startIndex), y: y(pen.startPrice)), CGPoint(x: x(pen.endIndex), y: y(pen.endPrice)), color: ChartPalette.gold, width: 1.7, dashed: !pen.confirmed)
            }
        }
        if layers.fractals {
            for fractal in snapshot.analysis.fractals where fractal.candleIndex >= first && fractal.candleIndex < end {
                let center = CGPoint(x: x(fractal.candleIndex), y: y(fractal.price))
                let direction: CGFloat = fractal.type == "top" ? -1 : 1
                c.setFillColor(ChartPalette.fractal.cgColor)
                c.move(to: CGPoint(x: center.x, y: center.y + direction * 2))
                c.addLine(to: CGPoint(x: center.x - 3, y: center.y + direction * 8))
                c.addLine(to: CGPoint(x: center.x + 3, y: center.y + direction * 8))
                c.closePath(); c.fillPath()
            }
        }
        c.restoreGState()
        if let selected, candles.indices.contains(selected) {
            let candle = candles[selected]
            line(c, CGPoint(x: x(selected), y: priceRect.minY), CGPoint(x: x(selected), y: macdRect.maxY), color: .secondaryLabel, dashed: true)
            line(c, CGPoint(x: left, y: y(candle.close)), CGPoint(x: priceRect.maxX, y: y(candle.close)), color: .secondaryLabel, dashed: true)
        }
        c.restoreGState()
        for index in [first, (first + end - 1) / 2, end - 1] {
            let label = MarketTime.date(candles[index].time, format: snapshot.timeframe == "1d" ? "MM-dd" : "MM-dd HH:mm", zone: MarketTime.zone(currency: snapshot.instrument.currency))
            text(label, at: CGPoint(x: min(max(left, x(index) - 22), rect.width - 100), y: rect.height - 20))
        }
        // Text has its own final layer, so candles/pens cannot obscure zone labels.
        if layers.zhongshu {
            for zone in snapshot.analysis.zhongshu where zone.endIndex >= first && zone.startIndex < end {
                text("中枢 \(zone.id)", at: CGPoint(x: min(max(left, x(zone.startIndex)), priceRect.maxX - 58), y: max(priceRect.minY, y(zone.high))), color: ChartPalette.violet, weight: .medium)
            }
        }
        if layers.points {
            for point in snapshot.signals.points where point.candleIndex >= first && point.candleIndex < end {
                let label = "\(point.side == "buy" ? "B" : "S")\(point.kind)\(point.confirmed ? "" : "?")"
                text(label, at: CGPoint(x: min(max(left, x(point.candleIndex) - 9), priceRect.maxX - 24), y: point.side == "buy" ? y(point.price) + 5 : y(point.price) - 17), color: point.side == "buy" ? ChartPalette.up : ChartPalette.down, size: 11, weight: .semibold)
            }
        }
    }
}
