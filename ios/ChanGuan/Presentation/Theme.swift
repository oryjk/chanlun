import SwiftUI
import UIKit

enum Theme {
    static let accent = Color(red: 46/255, green: 74/255, blue: 110/255)
    static let page = Color(red: 238/255, green: 240/255, blue: 242/255)
    static let line = Color(red: 227/255, green: 230/255, blue: 234/255)
    static let up = Color(red: 217/255, green: 79/255, blue: 94/255)
    static let down = Color(red: 21/255, green: 147/255, blue: 110/255)
    static func direction(_ value: Double) -> Color { value >= 0 ? up : down }
}

enum ChartPalette {
    static let up = UIColor(red: 217/255, green: 79/255, blue: 94/255, alpha: 1)
    static let down = UIColor(red: 21/255, green: 147/255, blue: 110/255, alpha: 1)
    static let accent = UIColor(red: 46/255, green: 74/255, blue: 110/255, alpha: 1)
    static let gold = UIColor(red: 180/255, green: 138/255, blue: 66/255, alpha: 1)
    static let violet = UIColor(red: 125/255, green: 104/255, blue: 168/255, alpha: 1)
    static let fractal = UIColor(red: 114/255, green: 148/255, blue: 170/255, alpha: 1)
}
