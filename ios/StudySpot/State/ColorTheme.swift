import SwiftUI
import Observation

/// Colour themes (Settings > Appearance > Theme), mirroring src/lib/colorThemes.js
/// and android/.../ui/theme/ColorThemes.kt. `.studyspot` is the default and
/// uses the original palette values unchanged; the others are derived the same
/// way (near-white base tinted toward the theme, a deep ink for structure, an
/// accent for actions), each with light and dark variants. The choice is saved
/// on-device and in user.data.theme, so web, iOS and Android match.
enum ColorTheme: String, CaseIterable, Identifiable {
    case studyspot, ocean, forest, grape, rose, graphite

    static let `default` = ColorTheme.studyspot

    var id: String { rawValue }

    var name: String {
        switch self {
        case .studyspot: "Study Spot"
        case .ocean: "Ocean"
        case .forest: "Forest"
        case .grape: "Grape"
        case .rose: "Rose"
        case .graphite: "Graphite"
        }
    }

    struct Spec {
        var base: (light: UInt32, dark: UInt32)
        var surface: (light: UInt32, dark: UInt32)
        var secondary: (light: UInt32, dark: UInt32)
        var accent: (light: UInt32, dark: UInt32)
        var onAccent: (light: UInt32, dark: UInt32) = (0xFFFFFF, 0xFFFFFF)
    }

    /// Hex values generated from the same HSL recipe as the website.
    var spec: Spec {
        switch self {
        case .studyspot:
            Spec(base: (0xFEFDFB, 0x15120F), surface: (0xFFFFFF, 0x221D18),
                 secondary: (0x1F2B47, 0xA9B8DC), accent: (0xF19D37, 0xF19D37))
        case .ocean:
            Spec(base: (0xFBFDFE, 0x0F1215), surface: (0xFFFFFF, 0x171C21),
                 secondary: (0x17354F, 0x9DC3E7), accent: (0x2581E4, 0x3C91EC))
        case .forest:
            Spec(base: (0xFDFEFB, 0x101410), surface: (0xFFFFFF, 0x191F19),
                 secondary: (0x204639, 0xA8DCCB), accent: (0x309161, 0x40B576))
        case .grape:
            Spec(base: (0xFDFBFE, 0x120F14), surface: (0xFFFFFF, 0x1C1820),
                 secondary: (0x291F47, 0xB6A6DD), accent: (0x8C56D2, 0xA674E7))
        case .rose:
            Spec(base: (0xFEFBFB, 0x150F11), surface: (0xFFFFFF, 0x20181A),
                 secondary: (0x452132, 0xDBA9BF), accent: (0xE74B7A, 0xEE638D))
        case .graphite:
            Spec(base: (0xFCFCFD, 0x111113), surface: (0xFFFFFF, 0x1A1B1E),
                 secondary: (0x2D3139, 0xBABFCA), accent: (0x464A53, 0xCDD0D6),
                 onAccent: (0xFFFFFF, 0x15171A))
        }
    }

    /// Light-mode accent as hex, shared with widgets and Live Activities.
    var hex: String {
        self == .studyspot ? "#f19d37" : String(format: "#%06x", spec.accent.light)
    }

    /// The accent for this theme (light/dark aware).
    var accentUIColor: UIColor {
        if self == .studyspot {
            // Exactly the original brand color.
            return UIColor(Color(hue: 33 / 360, saturation: 0.87, lightness: 0.58))
        }
        return ColorTheme.ui(spec.accent)
    }

    var color: Color { Color(accentUIColor) }

    static func ui(_ pair: (light: UInt32, dark: UInt32)) -> UIColor {
        UIColor { $0.userInterfaceStyle == .dark ? UIColor(rgb: pair.dark) : UIColor(rgb: pair.light) }
    }
}

/// The theme in use. Not tied to the main actor so `Palette` can read it from
/// anywhere; SwiftUI views that read a `Palette` color re-render when it changes.
@Observable
final class ActiveColorTheme: @unchecked Sendable {
    static let shared = ActiveColorTheme()
    static let storageKey = "cf-color-theme"

    var theme: ColorTheme

    private init() {
        theme = ColorTheme(rawValue: UserDefaults.standard.string(forKey: Self.storageKey) ?? "") ?? .default
    }
}

// MARK: - Alternate app icons

/// Home Screen icons (Settings > Appearance > App icon). The alternates are
/// AppIcon-<name>.appiconset in Assets.xcassets, listed in the target's
/// ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES; previews are the
/// AppIconPreview-<name> image sets (app icons can't be loaded as images).
enum AppIconChoice: String, CaseIterable, Identifiable {
    case `default` = "Default"
    case navy = "Navy"
    case midnight = "Midnight"
    case mono = "Mono"
    case ocean = "Ocean"
    case grape = "Grape"

    var id: String { rawValue }
    var name: String { self == .default ? "Study Spot" : rawValue }
    /// nil restores the primary icon.
    var alternateIconName: String? { self == .default ? nil : "AppIcon-\(rawValue)" }
    var previewImage: String { "AppIconPreview-\(rawValue)" }

    init(alternateIconName: String?) {
        self = AppIconChoice.allCases.first { $0.alternateIconName == alternateIconName } ?? .default
    }
}
