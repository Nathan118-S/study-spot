import SwiftUI
import Observation

enum AccentChoice: String, CaseIterable, Identifiable {
    case orange, blue, purple, green, rose
    var id: String { rawValue }
    var label: String { rawValue.capitalized }

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

    /// "#rrggbb" → Color (falls back to slate gray).
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else {
            self = Color(red: 0.58, green: 0.64, blue: 0.72)
            return
        }
        self.init(red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
    }
}
