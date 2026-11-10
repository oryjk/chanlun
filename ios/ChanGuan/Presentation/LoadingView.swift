import SwiftUI

struct LoadingView: View {
    let title: String
    var body: some View {
        VStack(spacing: 12) {
            TimelineView(.animation(minimumInterval: 1 / 20)) { timeline in
                Canvas { context, size in
                    let phase = timeline.date.timeIntervalSinceReferenceDate * 2
                    var path = Path()
                    for step in 0...80 {
                        let x = Double(step) / 80 * size.width
                        let y = size.height / 2 + sin(Double(step) / 9 - phase) * 8 * sin(Double(step) / 80 * .pi)
                        if step == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
                    }
                    context.stroke(path, with: .color(Theme.accent), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                }
            }.frame(width: 90, height: 28).accessibilityHidden(true)
            Text(title).font(.subheadline).foregroundStyle(.secondary)
        }.frame(maxWidth: .infinity).padding(.vertical, 35).accessibilityElement(children: .combine)
    }
}

struct ErrorRow: View {
    let text: String
    let retry: () -> Void
    var body: some View {
        HStack(alignment: .top) {
            Text(text).font(.footnote).foregroundStyle(.secondary)
            Spacer()
            Button("重试", action: retry).font(.footnote)
        }.padding().background(.white)
    }
}
