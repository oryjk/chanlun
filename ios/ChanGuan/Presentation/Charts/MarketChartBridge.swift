import SwiftUI

struct MarketChartBridge: UIViewRepresentable {
    let snapshot: MarketSnapshot
    let layers: ChartLayers
    let decimals: Int
    let onSelection: (ChartSelection?) -> Void
    func makeUIView(context: Context) -> MarketChartView { MarketChartView(frame: .zero) }
    func updateUIView(_ view: MarketChartView, context: Context) {
        view.onSelection = onSelection
        view.update(snapshot: snapshot, layers: layers, decimals: decimals)
    }
}
