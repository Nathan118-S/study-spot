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
