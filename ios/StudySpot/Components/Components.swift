import SwiftUI
import Observation

// MARK: - Toasts

@MainActor
@Observable
final class ToastCenter {
    struct Toast: Identifiable, Equatable {
        let id = UUID()
        var title: String
        var message: String?
        var isError: Bool
    }

    var current: Toast?

    func show(_ title: String, _ message: String? = nil, error: Bool = false) {
        let toast = Toast(title: title, message: message, isError: error)
        current = toast
        Task {
            try? await Task.sleep(for: .seconds(3))
            if current?.id == toast.id { current = nil }
        }
    }
}

/// Toasts live in their own pass-through window above everything (sheets and
/// full-screen covers included), so they're visible wherever they're raised.
@MainActor
enum ToastWindow {
    private static var window: UIWindow?

    static func install(toasts: ToastCenter, theme: ThemeStore) {
        guard window == nil,
              let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }) ??
                UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first
        else { return }
        let host = UIHostingController(rootView: ToastOverlay().environment(toasts).tint(Color(Palette.uiAccent)))
        host.view.backgroundColor = .clear
        let w = PassThroughWindow(windowScene: scene)
        w.rootViewController = host
        w.windowLevel = .alert + 1
        w.isHidden = false
        window = w
    }

    static func setStyle(_ scheme: ColorScheme?) {
        window?.overrideUserInterfaceStyle = scheme == .dark ? .dark : (scheme == .light ? .light : .unspecified)
    }
}

/// Lets touches through everywhere except on the toast itself.
private final class PassThroughWindow: UIWindow {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        return hit === rootViewController?.view ? nil : hit
    }
}

struct ToastOverlay: View {
    @Environment(ToastCenter.self) private var toasts

    var body: some View {
        VStack {
            if let t = toasts.current {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: t.isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                        .foregroundStyle(t.isError ? .red : .green)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t.title).font(.subheadline.weight(.semibold))
                        if let m = t.message { Text(m).font(.caption).foregroundStyle(.secondary) }
                    }
                    Spacer(minLength: 0)
                }
                .padding(12)
                .glassCard(cornerRadius: 18)
                .shadow(radius: 8, y: 4)
                .padding(.horizontal)
                .transition(.move(edge: .top).combined(with: .opacity))
                .onTapGesture { toasts.current = nil }
            }
            Spacer()
        }
        .animation(.spring(duration: 0.35), value: toasts.current)
        .allowsHitTesting(toasts.current != nil)
    }
}

// MARK: - Small building blocks

struct StatCard: View {
    var label: String
    var value: String
    var tint: Color = Palette.secondary
    var symbol: String?
    var symbolTint: Color = Palette.secondary.opacity(0.7)

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                if let symbol { Image(systemName: symbol).foregroundStyle(symbolTint).font(.caption) }
                Text(label).font(.caption).foregroundStyle(.secondary)
            }
            Text(value).font(.title2.bold()).foregroundStyle(tint).contentTransition(.numericText())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassCard(cornerRadius: 16)
    }
}

struct Pill: View {
    var text: String
    var color: Color = Palette.secondary
    var symbol: String?
    var filled = false

    var body: some View {
        HStack(spacing: 3) {
            if let symbol { Image(systemName: symbol) }
            Text(text)
        }
        .font(.caption2.weight(.semibold))
        .padding(.horizontal, 7)
        .padding(.vertical, 3)
        .foregroundStyle(filled ? .white : color)
        .background(filled ? color : color.opacity(0.14), in: Capsule())
    }
}

struct ColorDot: View {
    var hex: String
    var size: CGFloat = 8
    var body: some View { Circle().fill(Color(hex: hex)).frame(width: size, height: size) }
}

struct Card<Content: View>: View {
    var padding: CGFloat = 14
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 10) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .glassCard(cornerRadius: 18)
    }
}

struct ProgressBar: View {
    var value: Double // 0...100
    var tint: Color = .accentColor
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.secondary.opacity(0.12))
                Capsule().fill(tint).frame(width: geo.size.width * max(0, min(1, value / 100)))
            }
        }
        .frame(height: 6)
        .animation(.easeOut, value: value)
    }
}

/// A numeric-only text field for 6-digit verification codes.
struct CodeField: View {
    @Binding var code: String
    var body: some View {
        TextField("123456", text: $code)
            .keyboardType(.numberPad)
            .textContentType(.oneTimeCode)
            .font(.title2.monospacedDigit())
            .multilineTextAlignment(.center)
            .padding(12)
            .background(Palette.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
            .onChange(of: code) { _, new in
                let digits = String(new.filter(\.isNumber).prefix(6))
                if digits != new { code = digits }
            }
    }
}

// MARK: - Domain styling

extension Priority {
    var color: Color {
        switch self { case .high: .red; case .medium: .orange; case .low: .green }
    }
}

enum GradeStyle {
    static func color(_ letter: String?) -> Color {
        switch letter?.first {
        case "A": .green
        case "B": .mint
        case "C": .orange
        case "D": .orange
        case "F": .red
        default: .secondary
        }
    }
}

extension StreakStatus {
    var color: Color {
        switch self { case .active: .green; case .atrisk: .orange; case .broken: .red; case .empty: .secondary }
    }
    var flameColor: Color {
        switch self { case .active: .orange; case .atrisk: .yellow; default: .secondary }
    }
}

extension Double {
    /// "25" for whole numbers, "25.5" otherwise.
    var clean: String { self == rounded() ? String(Int(self)) : String(self) }
}

enum DateText {
    static func short(_ d: Date?) -> String {
        guard let d else { return "No due date" }
        return d.formatted(.dateTime.month(.abbreviated).day().hour().minute())
    }

    static func long(_ d: Date?) -> String {
        guard let d else { return "No due date" }
        return d.formatted(.dateTime.weekday(.wide).month(.abbreviated).day().hour().minute())
    }

    static func ago(_ d: Date?) -> String {
        guard let d else { return "" }
        let m = Int(Date().timeIntervalSince(d) / 60)
        if m < 1 { return "just now" }
        if m < 60 { return "\(m)m ago" }
        let h = m / 60
        if h < 24 { return "\(h)h ago" }
        return "\(h / 24)d ago"
    }
}

struct LoadingView: View {
    var body: some View { ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity) }
}
