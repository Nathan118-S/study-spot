import SwiftUI
import Observation

/// The Study Spot brand color. It's fixed (not user-configurable), matching
/// the website and the app icon.
enum AccentChoice {
    case orange

    /// Shared with widgets and Live Activities.
    var hex: String { "#f19d37" }

    var color: Color { Color(hue: 33 / 360, saturation: 0.87, lightness: 0.58) }
}

/// Appearance preferences, stored on-device like the web app's localStorage.
@MainActor
@Observable
final class ThemeStore {
    private let defaults = UserDefaults.standard

    var dark: Bool { didSet { defaults.set(dark, forKey: "cf-theme-dark") } }
    var syncWithDevice: Bool { didSet { defaults.set(syncWithDevice, forKey: "cf-theme-sync") } }
    var animations: Bool { didSet { defaults.set(animations, forKey: "cf-animations") } }
    let accent: AccentChoice = .orange

    init() {
        let d = UserDefaults.standard
        dark = d.bool(forKey: "cf-theme-dark")
        syncWithDevice = d.object(forKey: "cf-theme-sync") == nil ? true : d.bool(forKey: "cf-theme-sync")
        animations = d.object(forKey: "cf-animations") == nil ? true : d.bool(forKey: "cf-animations")
        d.removeObject(forKey: "cf-accent") // setting from older versions
    }

    /// nil follows the system setting.
    var colorScheme: ColorScheme? { syncWithDevice ? nil : (dark ? .dark : .light) }

    /// Manually picking a mode turns off device sync, as on the web.
    func setDarkMode(_ on: Bool) {
        syncWithDevice = false
        dark = on
    }

    func animation(_ a: Animation = .easeOut(duration: 0.25)) -> Animation? { animations ? a : nil }
}

extension Color {
    init(hue: Double, saturation s: Double, lightness l: Double) {
        let v = l + s * min(l, 1 - l)
        let sv = v == 0 ? 0 : 2 * (1 - l / v)
        self.init(hue: hue, saturation: sv, brightness: v)
    }
}

// MARK: - 60 / 20 / 20 palette
//
// 60% dominant:  warm neutral backgrounds (Palette.base)
// 20% secondary: deep navy "ink" for structure: titles, icons, chips,
//                secondary buttons, tab bar, card edges (Palette.secondary)
// 20% accent:    Study Spot orange for actions and highlights: main
//                buttons, selection, progress, switches (Palette.accent)
// Status colors (overdue red, done green, due-today orange) are semantic and
// sit outside the split.
enum Palette {
    /// 60%: the canvas behind everything.
    static let base = dynamic(light: 0xFEFDFB, dark: 0x15120F)
    /// Cards and list rows sit on this, slightly lifted from the base.
    static let surface = dynamic(light: 0xFFFFFF, dark: 0x221D18)
    /// 20%: structure and secondary emphasis.
    static let secondary = dynamic(light: 0x1F2B47, dark: 0xA9B8DC)
    /// 20%: actions and highlights.
    static let accent = AccentChoice.orange.color

    static let uiBase = uiDynamic(light: 0xFEFDFB, dark: 0x15120F)
    static let uiSecondary = uiDynamic(light: 0x1F2B47, dark: 0xA9B8DC)

    private static func uiDynamic(light: UInt32, dark: UInt32) -> UIColor {
        UIColor { $0.userInterfaceStyle == .dark ? UIColor(rgb: dark) : UIColor(rgb: light) }
    }

    private static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(uiDynamic(light: light, dark: dark))
    }

    /// UIKit-level styling for parts SwiftUI doesn't expose: navigation titles
    /// in the secondary color, and the tab bar's unselected items.
    @MainActor
    static func applyAppearance() {
        let titles = UINavigationBar.appearance()
        titles.largeTitleTextAttributes = [.foregroundColor: uiSecondary]
        titles.titleTextAttributes = [.foregroundColor: uiSecondary]
        UITabBar.appearance().unselectedItemTintColor = uiSecondary.withAlphaComponent(0.55)
    }
}

extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(red: CGFloat((rgb >> 16) & 0xff) / 255, green: CGFloat((rgb >> 8) & 0xff) / 255,
                  blue: CGFloat(rgb & 0xff) / 255, alpha: 1)
    }
}
