import Foundation

struct ChartViewport {
    var lower: Double
    var upper: Double
    init(count: Int) { lower = Double(max(0, count - 100)); upper = Double(max(1, count)) }
    var span: Double { max(1, upper - lower) }
    mutating func pan(bars: Double, count: Int) {
        let width = min(span, Double(max(1, count)))
        lower = min(max(0, lower + bars), max(0, Double(count) - width))
        upper = lower + width
    }
    mutating func zoom(scale: Double, anchor: Double, count: Int) {
        guard scale.isFinite, scale > 0 else { return }
        let fraction = min(1, max(0, anchor))
        let center = lower + span * fraction
        let width = min(Double(max(1, count)), max(Double(min(12, max(1, count))), span / scale))
        lower = min(max(0, center - width * fraction), max(0, Double(count) - width))
        upper = lower + width
    }
}

struct PriceProjection {
    let minimum, maximum, top, height: Double
    init(minimum: Double, maximum: Double, top: Double, height: Double) {
        let padding = max(abs(maximum - minimum) * 0.06, max(abs(minimum) * 0.001, 0.001))
        self.minimum = minimum - padding
        self.maximum = maximum + padding
        self.top = top
        self.height = max(1, height)
    }
    func y(_ price: Double) -> Double { top + (maximum - price) / (maximum - minimum) * height }
    func price(at y: Double) -> Double { maximum - (y - top) / height * (maximum - minimum) }
}

struct ChartLayers: Equatable {
    var fractals = true
    var pens = true
    var segments = true
    var zhongshu = true
    var points = true
}
