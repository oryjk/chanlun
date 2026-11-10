import UIKit

final class MarketChartView: UIView, UIGestureRecognizerDelegate {
    private var snapshot: MarketSnapshot?
    private var viewport = ChartViewport(count: 0)
    private var layers = ChartLayers()
    private var decimals = 2
    private var selected: Int?
    var onSelection: ((ChartSelection?) -> Void)?
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .white
        isOpaque = true
        contentMode = .redraw
        let pan = UIPanGestureRecognizer(target: self, action: #selector(pan(_:)))
        pan.delegate = self
        addGestureRecognizer(pan)
        addGestureRecognizer(UIPinchGestureRecognizer(target: self, action: #selector(pinch(_:))))
        let press = UILongPressGestureRecognizer(target: self, action: #selector(press(_:)))
        press.minimumPressDuration = 0.3
        addGestureRecognizer(press)
        isAccessibilityElement = true
        accessibilityLabel = "K 线图，包含成交量、MACD 和缠论结构"
        accessibilityHint = "左右拖动查看历史，双指缩放，长按查看单根行情；下方也提供结构文字详情"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(snapshot: MarketSnapshot, layers: ChartLayers, decimals: Int) {
        let selectedDate = selected.flatMap { index in
            self.snapshot?.candles.indices.contains(index) == true ? self.snapshot?.candles[index].time : nil
        }
        let changed = self.snapshot?.instrument.secid != snapshot.instrument.secid || self.snapshot?.timeframe != snapshot.timeframe
        let oldCount = self.snapshot?.candles.count ?? 0
        self.snapshot = snapshot; self.layers = layers; self.decimals = decimals
        if changed {
            viewport = ChartViewport(count: snapshot.candles.count); selected = nil
        } else if oldCount != snapshot.candles.count {
            viewport.pan(bars: viewport.upper >= Double(oldCount) - 1 ? Double(snapshot.candles.count - oldCount) : 0, count: snapshot.candles.count)
        }
        if let selectedDate, let selected, (!snapshot.candles.indices.contains(selected) || snapshot.candles[selected].time != selectedDate) {
            self.selected = nil
            DispatchQueue.main.async { [weak self] in self?.onSelection?(nil) }
        }
        setNeedsDisplay()
    }
    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext(), let snapshot else { return }
        MarketChartRenderer.draw(context, rect: bounds, snapshot: snapshot, viewport: viewport, layers: layers, decimals: decimals, selected: selected)
    }
    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let pan = gestureRecognizer as? UIPanGestureRecognizer else { return true }
        let velocity = pan.velocity(in: self)
        return abs(velocity.x) > abs(velocity.y)
    }
    @objc private func pan(_ gesture: UIPanGestureRecognizer) {
        guard let snapshot else { return }
        viewport.pan(bars: -Double(gesture.translation(in: self).x / max(1, bounds.width - 80)) * viewport.span, count: snapshot.candles.count)
        gesture.setTranslation(.zero, in: self)
        selected = nil; onSelection?(nil); setNeedsDisplay()
    }
    @objc private func pinch(_ gesture: UIPinchGestureRecognizer) {
        guard let snapshot else { return }
        viewport.zoom(scale: Double(gesture.scale), anchor: Double((gesture.location(in: self).x - 10) / max(1, bounds.width - 80)), count: snapshot.candles.count)
        gesture.scale = 1
        selected = nil; onSelection?(nil); setNeedsDisplay()
    }
    @objc private func press(_ gesture: UILongPressGestureRecognizer) {
        guard let snapshot, !snapshot.candles.isEmpty else { return }
        let fraction = Double((gesture.location(in: self).x - 10) / max(1, bounds.width - 80))
        let index = min(snapshot.candles.count - 1, max(0, Int(floor(viewport.lower + fraction * viewport.span))))
        selected = index
        let candle = snapshot.candles[index]
        onSelection?(.init(index: index, time: candle.time, price: candle.close))
        accessibilityValue = "\(MarketTime.date(candle.time, format: "yyyy-MM-dd HH:mm", zone: MarketTime.zone(currency: snapshot.instrument.currency))) 收盘 \(MarketFormat.price(candle.close, decimals: decimals))"
        setNeedsDisplay()
    }
}
