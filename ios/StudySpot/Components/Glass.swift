import SwiftUI

// Liquid Glass throughout the app.
//
// iOS 26 renders the real thing (glassEffect, .glass / .glassProminent
// button styles); bars, tab bars, sheets and menus adopt it automatically
// when built with Xcode 26. On iOS 17–18 the same helpers fall back to
// frosted materials. `#if compiler(>=6.2)` keeps the project building with
// Xcode 16, whose SDK doesn't have the iOS 26 APIs.

// MARK: - Live background

/// Soft brand-colored glows drifting slowly behind every screen, matching the
/// website's live background. Glass surfaces pick up their colors.
struct AppBackground: View {
    @Environment(ThemeStore.self) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private struct Glow {
        var color: Color
        var anchor: CGPoint       // unit-space resting position
        var drift: CGSize         // unit-space travel
        var period: Double        // seconds per cycle
        var phase: Double
    }

    private let glows: [Glow] = [
        Glow(color: Color(hue: 38 / 360, saturation: 1, lightness: 0.70), anchor: CGPoint(x: 0.10, y: 0.05), drift: CGSize(width: 0.25, height: 0.18), period: 38, phase: 0),
        Glow(color: Color(hue: 24 / 360, saturation: 0.95, lightness: 0.66), anchor: CGPoint(x: 0.95, y: 0.25), drift: CGSize(width: -0.22, height: 0.22), period: 46, phase: 1.3),
        Glow(color: Color(hue: 45 / 360, saturation: 1, lightness: 0.72), anchor: CGPoint(x: 0.25, y: 0.95), drift: CGSize(width: 0.28, height: -0.16), period: 52, phase: 2.1),
        Glow(color: Color(hue: 15 / 360, saturation: 0.9, lightness: 0.70), anchor: CGPoint(x: 0.90, y: 0.85), drift: CGSize(width: -0.25, height: -0.2), period: 60, phase: 3.4),
    ]

    var body: some View {
        let animate = theme.animations && !reduceMotion
        // Kept subtle so the base stays dominant.
        let strength = colorScheme == .dark ? 0.14 : 0.12
        GeometryReader { geo in
            let size = max(geo.size.width, geo.size.height) * 1.1
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !animate)) { context in
                let t = animate ? context.date.timeIntervalSinceReferenceDate : 0
                ZStack {
                    Palette.base  // 60%: the dominant color
                    ForEach(glows.indices, id: \.self) { i in
                        let g = glows[i]
                        // Smooth back-and-forth drift (like CSS alternate easing).
                        let p = (sin(t * 2 * .pi / g.period + g.phase) + 1) / 2
                        Circle()
                            .fill(RadialGradient(colors: [g.color.opacity(strength), g.color.opacity(0)],
                                                 center: .center, startRadius: 0, endRadius: size / 2))
                            .frame(width: size, height: size)
                            .position(x: geo.size.width * (g.anchor.x + g.drift.width * p),
                                      y: geo.size.height * (g.anchor.y + g.drift.height * p))
                    }
                }
            }
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

extension View {
    /// Live background behind this screen (lists and forms become transparent).
    func appBackground() -> some View {
        scrollContentBackground(.hidden)
            .background { AppBackground() }
    }
}

// MARK: - Glass surfaces

extension View {
    /// A Liquid Glass panel (frosted material before iOS 26).
    @ViewBuilder
    func glassCard(cornerRadius: CGFloat = 16, tint: Color? = nil, interactive: Bool = false) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            let glass: Glass = {
                var g = Glass.regular
                if let tint { g = g.tint(tint) }
                if interactive { g = g.interactive() }
                return g
            }()
            self.glassEffect(glass, in: .rect(cornerRadius: cornerRadius))
        } else {
            materialCard(cornerRadius: cornerRadius, tint: tint)
        }
        #else
        materialCard(cornerRadius: cornerRadius, tint: tint)
        #endif
    }

    /// Liquid Glass in a capsule (pills, floating bars).
    @ViewBuilder
    func glassCapsule(tint: Color? = nil) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self.glassEffect(tint.map { Glass.regular.tint($0) } ?? .regular, in: .capsule)
        } else {
            self.background(.regularMaterial, in: Capsule())
        }
        #else
        self.background(.regularMaterial, in: Capsule())
        #endif
    }

    private func materialCard(cornerRadius: CGFloat, tint: Color?) -> some View {
        background {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(.regularMaterial)
                .overlay {
                    if let tint {
                        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).fill(tint.opacity(0.15))
                    }
                }
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .strokeBorder(.white.opacity(0.35), lineWidth: 0.5)
                )
        }
    }

    /// Liquid Glass buttons: .glassProminent for the main action, .glass
    /// otherwise (bordered styles before iOS 26).
    @ViewBuilder
    func glassButtonStyle(prominent: Bool = false) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            if prominent { self.buttonStyle(.glassProminent) } else { self.buttonStyle(.glass).tint(Palette.secondary) }
        } else {
            if prominent { self.buttonStyle(.borderedProminent) } else { self.buttonStyle(.bordered).tint(Palette.secondary) }
        }
        #else
        if prominent { self.buttonStyle(.borderedProminent) } else { self.buttonStyle(.bordered).tint(Palette.secondary) }
        #endif
    }

    /// Frosted list rows so the live background shows through grouped lists.
    func glassRows() -> some View {
        listRowBackground(Rectangle().fill(.thinMaterial).overlay(Palette.surface.opacity(0.35)))
    }
}
