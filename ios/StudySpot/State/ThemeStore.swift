import SwiftUI
import Observation

/// Appearance preferences, stored on-device like the web app's localStorage.
/// The colour theme also syncs through user.data.theme (see RootView).
@MainActor
@Observable
final class ThemeStore {
    private let defaults = UserDefaults.standard

    var dark: Bool { didSet { defaults.set(dark, forKey: "cf-theme-dark") } }
    var syncWithDevice: Bool { didSet { defaults.set(syncWithDevice, forKey: "cf-theme-sync") } }
    var animations: Bool { didSet { defaults.set(animations, forKey: "cf-animations") } }

    /// The colour theme (ColorTheme.swift). Stored in ActiveColorTheme so
    /// `Palette` can read it; reading it here still drives SwiftUI updates.
    var colorTheme: ColorTheme {
        get { ActiveColorTheme.shared.theme }
        set {
            ActiveColorTheme.shared.theme = newValue
            defaults.set(newValue.rawValue, forKey: ActiveColorTheme.storageKey)
            Palette.applyAppearance()
        }
    }

    /// The accent color in use (shared with widgets and Live Activities).
    var accent: ColorTheme { colorTheme }

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
    /// The active theme's values (ColorTheme.swift); `.studyspot` is the
    /// original palette: base #FEFDFB / #15120F, surface #FFFFFF / #221D18,
    /// secondary navy #1F2B47 / #A9B8DC, accent orange #F19D37.
    private static var spec: ColorTheme.Spec { ActiveColorTheme.shared.theme.spec }

    /// 60%: the canvas behind everything.
    static var base: Color { Color(ColorTheme.ui(spec.base)) }
    /// Cards and list rows sit on this, slightly lifted from the base.
    static var surface: Color { Color(ColorTheme.ui(spec.surface)) }
    /// 20%: structure and secondary emphasis.
    static var secondary: Color { Color(ColorTheme.ui(spec.secondary)) }
    /// 20%: actions and highlights.
    static var accent: Color { ActiveColorTheme.shared.theme.color }

    /// Resolved whenever UIKit draws, so they follow theme changes.
    static let uiBase = live { $0.base }
    static let uiSecondary = live { $0.secondary }
    static let uiAccent = UIColor { ActiveColorTheme.shared.theme.accentUIColor.resolvedColor(with: $0) }

    private static func live(_ pick: @escaping (ColorTheme.Spec) -> (light: UInt32, dark: UInt32)) -> UIColor {
        UIColor { traits in
            let pair = pick(ActiveColorTheme.shared.theme.spec)
            return UIColor(rgb: traits.userInterfaceStyle == .dark ? pair.dark : pair.light)
        }
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
