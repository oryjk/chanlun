import SwiftUI

enum AppTab: String, CaseIterable { case market, rankings, account }

@MainActor
final class AppModel: ObservableObject {
    let market: MarketUseCases
    let auth: AuthUseCases
    private let routes: any RouteStore
    @Published var stock: Stock?
    @Published var timeframe: Timeframe
    @Published var tab: AppTab = .market
    @Published var snapshot: MarketSnapshot?
    @Published var confluence: SignalConfluence?
    @Published var quote: MarketQuote?
    @Published var series: [QuotePoint] = []
    @Published var board: RankingBoard?
    @Published var user: AppUser?
    @Published var loading = false
    @Published var confluenceLoading = false
    @Published var boardLoading = false
    @Published var accountBusy = false
    @Published var error = ""
    @Published var boardError = ""
    @Published var confluenceError = ""
    @Published var accountError = ""
    @Published var layers = ChartLayers()
    private var active = false
    private(set) var authEpoch = 0
    private var generation = 0
    private var loadTask: Task<Void, Never>?
    private var boardTask: Task<Void, Never>?

    init(market: MarketUseCases, auth: AuthUseCases, routes: any RouteStore) {
        self.market = market; self.auth = auth; self.routes = routes
        let saved = routes.restore()
        stock = saved.0; timeframe = saved.1
    }
    func start() async {
        if stock == nil {
            do {
                let found = try await market.reader.search("600519")
                if stock == nil { stock = found.first { $0.symbol == "600519" } ?? found.first }
            } catch { self.error = error.localizedDescription }
        }
        reload()
    }
    func activate(_ value: Bool) {
        guard value != active else { return }
        active = value
        if value { reload() }
        else { generation += 1; loadTask?.cancel(); boardTask?.cancel(); loading = false; boardLoading = false; confluenceLoading = false }
    }
    func changeTab(_ value: AppTab) {
        guard tab != value else { return }
        tab = value; reload()
    }
    func select(_ value: Stock, daily: Bool = false) {
        stock = value
        if daily { timeframe = .daily }
        tab = .market
        snapshot = nil; quote = nil; series = []; confluence = nil
        routes.save(stock: value, timeframe: timeframe)
        reload()
    }
    func changeTimeframe(_ value: Timeframe) {
        guard timeframe != value else { return }
        timeframe = value; snapshot = nil; confluence = nil; quote = nil; series = []
        routes.save(stock: stock, timeframe: value)
        reload()
    }
    func reload(refresh: Bool = false) {
        generation += 1
        let current = generation
        loadTask?.cancel(); boardTask?.cancel()
        loading = false; confluenceLoading = false; boardLoading = false
        guard active else { return }
        if tab == .rankings { startRankings(); return }
        guard tab == .market, let stock else { return }
        error = ""; confluenceError = ""
        loading = snapshot == nil && quote == nil
        if timeframe == .realtime {
            loadTask = Task { [weak self] in await self?.pollQuotes(stock: stock, generation: current) }
        } else {
            let period = timeframe
            confluenceLoading = true
            loadTask = Task { [weak self] in
                guard let self else { return }
                async let levels = self.market.reader.confluence(secid: stock.secid)
                do {
                    let data = try await self.market.reader.snapshot(secid: stock.secid, timeframe: period, refresh: refresh)
                    guard self.accept(current) else { return }
                    self.snapshot = data
                } catch {
                    if self.accept(current), !(error is CancellationError) { self.error = error.localizedDescription }
                }
                if self.accept(current) { self.loading = false }
                do {
                    let data = try await levels
                    if self.accept(current) { self.confluence = data }
                } catch {
                    if self.accept(current), !(error is CancellationError) { self.confluenceError = error.localizedDescription }
                }
                if self.accept(current) { self.confluenceLoading = false }
            }
        }
    }
    private func accept(_ current: Int) -> Bool { active && generation == current && !Task.isCancelled }
    private func pollQuotes(stock: Stock, generation current: Int) async {
        do {
            let history = try await market.reader.series(secid: stock.secid)
            if accept(current) { series = history.points }
        } catch {
            if accept(current) { self.error = error.localizedDescription }
        }
        var first = true
        var failures = 0
        while accept(current) {
            if first || MarketTime.isOpen(stock: stock) {
                first = false
                do {
                    let value = try await market.reader.quote(secid: stock.secid)
                    guard accept(current) else { return }
                    quote = value
                    series = QuoteSeriesRules.merge(series, [], stock: stock, reference: value.time)
                    if MarketTime.isOpen(stock: stock) {
                        let sampled = try await market.writer.record(value)
                        guard accept(current) else { return }
                        series = QuoteSeriesRules.merge(series, sampled.points, stock: stock, reference: value.time)
                    }
                    error = ""; failures = 0
                } catch {
                    if accept(current) { self.error = error.localizedDescription; failures = min(4, failures + 1) }
                }
                if accept(current) { loading = false }
            }
            do { try await Task.sleep(for: .seconds(failures > 0 ? min(30, 3 * pow(2, Double(failures))) : 3)) }
            catch { return }
        }
    }
    private func startRankings() {
        boardLoading = board == nil
        let current = generation
        boardTask = Task { [weak self] in
            guard let self else { return }
            while self.accept(current) {
                do {
                    let value = try await self.market.reader.rankings()
                    guard self.accept(current) else { return }
                    self.board = value; self.boardError = ""
                } catch {
                    if self.accept(current) { self.boardError = error.localizedDescription }
                }
                if self.accept(current) { self.boardLoading = false }
                do { try await Task.sleep(for: .seconds(60)) } catch { return }
            }
        }
    }
    func restoreSession() async {
        guard !accountBusy else { return }
        authEpoch += 1
        let epoch = authEpoch
        do {
            let result = try await auth.reader.session()
            guard epoch == authEpoch else { return }
            user = result.user
            if !result.authenticated { try? auth.store.clear() }
        } catch {
            if epoch == authEpoch { accountError = "无法恢复登录状态，可继续查看行情，稍后重试登录" }
        }
    }
    func login() async {
        guard !accountBusy else { return }
        accountBusy = true; accountError = ""
        authEpoch += 1
        let epoch = authEpoch
        defer { accountBusy = false }
        do {
            let result = try await auth.login()
            guard epoch == authEpoch else { return }
            try auth.persist(result)
            user = result.user
        } catch { if epoch == authEpoch { accountError = error.localizedDescription } }
    }
    func logout() async {
        authEpoch += 1
        accountBusy = true; accountError = ""
        defer { accountBusy = false; user = nil }
        do { try await auth.logout() }
        catch { accountError = "本机已退出，服务端退出未完成，请联网后重新登录或等待会话到期" }
    }
    func deleteAccount() async {
        authEpoch += 1
        accountBusy = true; accountError = ""
        defer { accountBusy = false }
        do { try await auth.deleteAccount(); user = nil }
        catch {
            if let session = try? await auth.reader.session(), !session.authenticated { credentialsRevoked() }
            accountError = error.localizedDescription
        }
    }
    func credentialsRevoked() { authEpoch += 1; try? auth.store.clear(); user = nil }
}
