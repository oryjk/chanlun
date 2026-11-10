import SwiftUI

struct StockSearchView: View {
    @ObservedObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [Stock] = []
    @State private var error = ""
    @State private var loading = false
    var body: some View {
        NavigationStack {
            List {
                if loading { ProgressView("正在搜索") }
                if !error.isEmpty { Text(error).foregroundStyle(.secondary) }
                ForEach(results) { stock in
                    Button {
                        model.select(stock); dismiss()
                    } label: {
                        HStack {
                            Text(stock.name).foregroundStyle(.primary)
                            Spacer()
                            Text("\(stock.symbol)  \(stock.market)").font(.caption).foregroundStyle(.secondary)
                        }.padding(.vertical, 6)
                    }
                }
                if !query.isEmpty && !loading && results.isEmpty && error.isEmpty { Text("没有找到该证券，请尝试名称或代码").foregroundStyle(.secondary) }
                if query.isEmpty { Text("输入股票、ETF 名称或代码").foregroundStyle(.secondary) }
            }
            .listStyle(.plain)
            .searchable(text: $query, prompt: "股票或 ETF 名称、代码")
            .navigationTitle("搜索证券").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } } }
            .task(id: query) {
                let input = query.trimmingCharacters(in: .whitespacesAndNewlines)
                results = []; error = ""
                guard !input.isEmpty else { loading = false; return }
                loading = true
                do {
                    try await Task.sleep(for: .milliseconds(300))
                    let found = try await model.market.reader.search(input)
                    try Task.checkCancellation()
                    results = found; loading = false
                } catch {
                    guard !Task.isCancelled else { return }
                    self.error = error.localizedDescription; loading = false
                }
            }
        }
    }
}
