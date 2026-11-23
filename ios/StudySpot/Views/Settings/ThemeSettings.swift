import SwiftUI
import UIKit

// MARK: - Colour theme

/// Settings > Appearance > Theme: swatch previews of each ColorTheme. Applies at
/// once and saves to user.data.theme so the website and Android follow.
struct ColorThemeSection: View {
    @Environment(ThemeStore.self) private var theme
    @Environment(AuthStore.self) private var auth
    @Environment(\.colorScheme) private var scheme

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 10)]

    var body: some View {
        Section {
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(ColorTheme.allCases) { t in
                    Button { choose(t) } label: {
                        ThemeSwatch(theme: t, dark: scheme == .dark, selected: theme.colorTheme == t)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t.name)
                    .accessibilityAddTraits(theme.colorTheme == t ? .isSelected : [])
                }
            }
            .padding(.vertical, 6)
        } header: {
            Text("Theme")
        } footer: {
            Text("Pick your colors. Your theme syncs to Study Spot on the web and your other devices.")
        }
    }

    private func choose(_ t: ColorTheme) {
        guard theme.colorTheme != t else { return }
        theme.colorTheme = t
        guard let user = auth.user, !user.isDemo else { return }
        Task { try? await auth.update(["theme": .string(t.rawValue)]) }
    }
}

/// A tiny screen in the theme's colors: base, a card with an ink title and an
/// accent button, then the theme's name.
struct ThemeSwatch: View {
    var theme: ColorTheme
    var dark: Bool
    var selected: Bool

    private func c(_ pair: (light: UInt32, dark: UInt32)) -> Color { Color(UIColor(rgb: dark ? pair.dark : pair.light)) }

    var body: some View {
        let s = theme.spec
        let accent = theme == .studyspot ? Color(hue: 33 / 360, saturation: 0.87, lightness: 0.58) : c(s.accent)
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 6) {
                Capsule().fill(c(s.secondary)).frame(width: 30, height: 5)
                HStack {
                    Capsule().fill(c(s.secondary).opacity(0.5)).frame(width: 22, height: 4)
                    Spacer(minLength: 4)
                    Capsule().fill(accent).frame(width: 24, height: 12)
                }
                .padding(.horizontal, 6)
                .frame(height: 26)
                .background(c(s.surface), in: RoundedRectangle(cornerRadius: 5))
            }
            .padding(8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(c(s.base), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.primary.opacity(0.08)))

            HStack(spacing: 5) {
                Circle().fill(accent).frame(width: 10, height: 10)
                Text(theme.name).font(.footnote.weight(.medium)).lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 0)
                if selected { Image(systemName: "checkmark").font(.caption.weight(.bold)).foregroundStyle(accent) }
            }
        }
        .padding(6)
        .background(selected ? accent.opacity(0.12) : .clear, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(selected ? accent : Color.primary.opacity(0.12), lineWidth: selected ? 1.5 : 0.5))
        .contentShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - App icon

/// Settings > Appearance > App icon. Uses the system's alternate app icons;
/// the choice is per device, so it isn't synced.
struct AppIconSection: View {
    @Environment(ToastCenter.self) private var toasts
    @State private var current = AppIconChoice(alternateIconName: UIApplication.shared.alternateIconName)

    private let columns = [GridItem(.adaptive(minimum: 72), spacing: 12)]

    var body: some View {
        if UIApplication.shared.supportsAlternateIcons {
            Section {
                LazyVGrid(columns: columns, spacing: 14) {
                    ForEach(AppIconChoice.allCases) { icon in
                        Button { choose(icon) } label: {
                            VStack(spacing: 6) {
                                Image(icon.previewImage)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 60, height: 60)
                                    .clipShape(RoundedRectangle(cornerRadius: 13.5, style: .continuous))
                                    .overlay(RoundedRectangle(cornerRadius: 13.5, style: .continuous)
                                        .strokeBorder(Color.primary.opacity(0.1), lineWidth: 0.5))
                                    .padding(3)
                                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .strokeBorder(current == icon ? Color.accentColor : .clear, lineWidth: 2.5))
                                Text(icon.name).font(.caption).lineLimit(1).minimumScaleFactor(0.8)
                                    .foregroundStyle(current == icon ? .primary : .secondary)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(icon.name) icon")
                        .accessibilityAddTraits(current == icon ? .isSelected : [])
                    }
                }
                .padding(.vertical, 6)
            } header: {
                Text("App icon")
            } footer: {
                Text("Choose the icon on your Home Screen.")
            }
        }
    }

    private func choose(_ icon: AppIconChoice) {
        guard icon != current else { return }
        let previous = current
        current = icon
        Task { @MainActor in
            do {
                try await UIApplication.shared.setAlternateIconName(icon.alternateIconName)
            } catch {
                current = previous
                toasts.show("Couldn't change the icon", error.localizedDescription, error: true)
            }
        }
    }
}

// MARK: - Sync

extension View {
    /// Follows the signed-in user's saved theme (user.data.theme) from the
    /// website or another device.
    func followsSavedColorTheme(_ user: User?, theme: ThemeStore) -> some View {
        onChange(of: user?.data["theme"]?.stringValue, initial: true) { _, saved in
            if let saved, let t = ColorTheme(rawValue: saved), t != theme.colorTheme {
                theme.colorTheme = t
            }
        }
    }
}
