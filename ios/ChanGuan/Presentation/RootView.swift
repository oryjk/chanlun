import SwiftUI

struct RootView: View {
    @ObservedObject var model: AppModel
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.scenePhase) private var scenePhase
    @State private var searching = false
    var body: some View {
        Group {
            if sizeClass == .regular {
                NavigationSplitView {
                    List {
                        Section("缠观") {
                            ForEach(AppTab.allCases, id: \.self) { tab in
                                Button { model.changeTab(tab) } label: {
                                    Label(title(tab), systemImage: icon(tab))
                                        .foregroundStyle(model.tab == tab ? Theme.accent : .primary)
                                }
                            }
                        }
                        if let stock = model.stock {
                            Section("当前证券") {
                                Button { model.changeTab(.market) } label: {
                                    VStack(alignment: .leading) { Text(stock.name); Text(stock.symbol).font(.caption).foregroundStyle(.secondary) }
                                }
                            }
                        }
                    }
                    .navigationTitle("缠观")
                } detail: {
                    NavigationStack { screen(model.tab) }
                }
            } else {
                TabView(selection: Binding(get: { model.tab }, set: { model.changeTab($0) })) {
                    ForEach(AppTab.allCases, id: \.self) { tab in
                        NavigationStack { screen(tab) }
                            .tabItem { Label(title(tab), systemImage: icon(tab)) }.tag(tab)
                    }
                }
            }
        }
        .tint(Theme.accent)
        .sheet(isPresented: $searching) { StockSearchView(model: model) }
        .onAppear { model.activate(scenePhase == .active) }
        .onChange(of: scenePhase) { _, phase in model.activate(phase == .active) }
    }
    @ViewBuilder private func screen(_ tab: AppTab) -> some View {
        Group {
            switch tab {
            case .market: MarketView(model: model)
            case .rankings: RankingView(model: model)
            case .account: AccountView(model: model)
            }
        }
        .navigationTitle(title(tab)).navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if tab != .account {
                ToolbarItem(placement: .topBarLeading) {
                    Button { searching = true } label: { Image(systemName: "magnifyingglass") }.accessibilityLabel("搜索股票或 ETF")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { model.reload(refresh: true) } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("刷新当前页面")
                }
            }
        }
    }
    private func title(_ tab: AppTab) -> String { tab == .market ? "行情" : tab == .rankings ? "榜单" : "账号" }
    private func icon(_ tab: AppTab) -> String { tab == .market ? "chart.xyaxis.line" : tab == .rankings ? "list.number" : "person.crop.circle" }
}
