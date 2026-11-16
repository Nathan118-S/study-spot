import SwiftUI
import Observation

enum AccentChoice: String, CaseIterable, Identifiable {
    case orange, blue, purple, green, rose
    var id: String { rawValue }
    var label: String { rawValue.capitalized }

    /// Hex equivalent, shared with widgets and Live Activities.
    var hex: String {
        switch self {
        case .orange: "#f19d37"
        case .blue: "#2463eb"
        case .purple: "#7c3bed"
        case .green: "#21c45d"
        case .rose: "#e5345a"
        }
    }

    /// HSL values shared with the web app's accent palette.
    var color: Color {
        switch self {
        case .orange: Color(hue: 33 / 360, saturation: 0.87, lightness: 0.58)
        case .blue: Color(hue: 221 / 360, saturation: 0.83, lightness: 0.53)
        case .purple: Color(hue: 262 / 360, saturation: 0.83, lightness: 0.58)
        case .green: Color(hue: 142 / 360, saturation: 0.71, lightness: 0.45)
        case .rose: Color(hue: 347 / 360, saturation: 0.77, lightness: 0.55)
        }
    }
}

/// Appearance preferences, stored on-device like the web app's localStorage.
@MainActor
@Observable
final class ThemeStore {
    private let defaults = UserDefaults.standard

    var dark: Bool { didSet { defaults.set(dark, forKey: "cf-theme-dark") } }
    var syncWithDevice: Bool { didSet { defaults.set(syncWithDevice, forKey: "cf-theme-sync") } }
    var animations: Bool { didSet { defaults.set(animations, forKey: "cf-animations") } }
    var accent: AccentChoice { didSet { defaults.set(accent.rawValue, forKey: "cf-accent") } }

    init() {
        let d = UserDefaults.standard
        dark = d.bool(forKey: "cf-theme-dark")
        syncWithDevice = d.object(forKey: "cf-theme-sync") == nil ? true : d.bool(forKey: "cf-theme-sync")
        animations = d.object(forKey: "cf-animations") == nil ? true : d.bool(forKey: "cf-animations")
        accent = AccentChoice(rawValue: d.string(forKey: "cf-accent") ?? "") ?? .orange
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
