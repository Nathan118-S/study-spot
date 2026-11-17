import SwiftUI

/// The Study Spot logo (the website's yellow graduation cap), from the
/// "LogoMark" vector image in both the app's and the widgets' asset catalogs.
struct LogoMark: View {
    var size: CGFloat = 56

    var body: some View {
        Image("LogoMark")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityLabel("Study Spot")
    }
}
