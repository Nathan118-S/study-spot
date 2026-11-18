import SwiftUI

/// First-run setup wizard: welcome, appearance, leaderboard, security,
/// services, grading. Every step can be changed later in Settings.
struct OnboardingView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ThemeStore.self) private var theme

    private enum Step: Int, CaseIterable {
        case welcome, appearance, leaderboard, security, services, grading
        var title: String {
            switch self {
            case .welcome: "Welcome"
            case .appearance: "Appearance"
            case .leaderboard: "Leaderboard"
            case .security: "Security"
            case .services: "Services"
            case .grading: "Grading"
            }
        }
        var symbol: String {
            switch self {
            case .welcome: "graduationcap"
            case .appearance: "paintpalette"
            case .leaderboard: "trophy"
            case .security: "checkmark.shield"
            case .services: "powerplug"
            case .grading: "checklist"
            }
        }
    }

    @State private var step: Step = .welcome
    @State private var finishing = false
    @State private var bounce = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 4) {
                    ForEach(Step.allCases, id: \.self) { s in
                        Capsule().fill(s.rawValue <= step.rawValue ? Color.accentColor : Palette.secondary.opacity(0.15))
                            .frame(height: 5)
                    }
                }
                .padding(.horizontal)
                .padding(.top, 8)

                Group {
                    switch step {
                    case .welcome: welcome
                    case .appearance: Form { AppearanceSettings() }
                    case .leaderboard: Form {
                        Section { Text("Compete with other students by showing the top study streaks on the Streaks page.").foregroundStyle(.secondary) }
                        LeaderboardSetting()
                    }
                    case .security: Form { TwoFactorSettings() }
                    case .services: Form {
                        GoogleSettings()
                        BlackboardSettings()
                        LMSCalendarLinksCard()
                    }
                    case .grading: Form { GradingScaleEditor() }
                    }
                }
                .id(step)
                .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                        removal: .move(edge: .leading).combined(with: .opacity)))
                .frame(maxHeight: .infinity)

                HStack {
                    Button {
                        go(-1)
                    } label: { Label("Back", systemImage: "arrow.left") }
                    .disabled(step == .welcome || finishing)
                    Spacer()
                    if step == .grading {
                        Button { Task { await finish() } } label: {
                            if finishing { ProgressView() } else { Label("Finish", systemImage: "checkmark") }
                        }
                        .glassButtonStyle(prominent: true)
                    } else {
                        Button { go(1) } label: {
                            if step == .welcome { Label("Get started", systemImage: "sparkles") }
                            else { Label("Next", systemImage: "arrow.right") }
                        }
                        .glassButtonStyle(prominent: true)
                    }
                }
                .padding()
                .disabled(finishing)
            }
            .appBackground()
            .navigationTitle(step.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Image(systemName: step.symbol).foregroundStyle(.tint) }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Skip setup") { Task { await finish() } }.disabled(finishing)
                }
            }
        }
        .interactiveDismissDisabled()
    }

    private var welcome: some View {
        VStack(spacing: 16) {
            Spacer()
            LogoMark(size: 96)
                .offset(y: bounce ? -8 : 0)
                .rotationEffect(.degrees(bounce ? -6 : 6))
                .animation(theme.animations ? .easeInOut(duration: 1.2).repeatForever(autoreverses: true) : nil, value: bounce)
                .onAppear { bounce = true }
            (Text("Welcome to ") + Text("Study Spot").foregroundStyle(.tint))
                .font(.largeTitle.bold())
                .multilineTextAlignment(.center)
            Text("Let's set up your account in a few quick steps. You can change any of these later in Settings.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)
            Spacer()
        }
        .padding()
    }

    private func go(_ delta: Int) {
        guard let next = Step(rawValue: step.rawValue + delta) else { return }
        withAnimation(theme.animation(.spring(duration: 0.35))) { step = next }
    }

    private func finish() async {
        finishing = true
        do {
            try await auth.update(["onboarding_completed": true])
        } catch {
            finishing = false
        }
    }
}
