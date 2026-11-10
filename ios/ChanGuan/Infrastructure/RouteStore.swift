import Foundation

final class UserDefaultsRouteStore: RouteStore {
    func restore() -> (Stock?, Timeframe) {
        let stock = UserDefaults.standard.data(forKey: "selected-stock").flatMap { try? JSONDecoder().decode(Stock.self, from: $0) }
        let timeframe = UserDefaults.standard.string(forKey: "selected-timeframe").flatMap(Timeframe.init(rawValue:)) ?? .thirtyMinutes
        return (stock, timeframe)
    }
    func save(stock: Stock?, timeframe: Timeframe) {
        UserDefaults.standard.set(stock.flatMap { try? JSONEncoder().encode($0) }, forKey: "selected-stock")
        UserDefaults.standard.set(timeframe.rawValue, forKey: "selected-timeframe")
    }
}
