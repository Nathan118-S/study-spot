import SwiftUI

// Settings → Canvas, Schoology & Brightspace: add a school's Canvas,
// Schoology or Brightspace (D2L) calendar by its personal feed link
// (server: icalAddFeed with kind canvas / schoology / brightspace). Uses the
// shared CalendarLinkGuide; this file has the page, the onboarding card and
// each system's steps 1–2.

// MARK: - Settings page

struct LMSCalendarLinksSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @State private var guideKind: CalendarLinkKind?

    private var isDemo: Bool { auth.user?.isDemo == true }
    private var feeds: [ICalFeed] {
        data.icalFeeds.filter { feed in CalendarLinkKind.lms.contains { $0.matches(feed) } }
    }

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                Text("If your school uses Canvas, Schoology or Brightspace, paste your calendar link from it and Study Spot will import your due dates. It works without your school approving anything.")
                    .font(.subheadline).foregroundStyle(.secondary)
                LMSSystemButtons(isDemo: isDemo) { guideKind = $0 }
            }
            .padding(.vertical, 4)
            .task { await data.loadICalFeeds() }
            .sheet(item: $guideKind) { kind in CalendarLinkGuide(kind: kind) }
        } footer: {
            if isDemo { Text("Calendar links aren't available on the demo account.") }
        }
        if !feeds.isEmpty {
            Section {
                ForEach(feeds) { feed in
                    CalendarLinkRow(feed: feed)
                }
            } header: {
                Text("Calendar links")
            } footer: {
                Text("Study Spot checks these calendars for changes every couple of hours and whenever you sync.")
            }
        }
    }
}

/// Onboarding's services step: a compact card with the three buttons.
struct LMSCalendarLinksCard: View {
    @Environment(AuthStore.self) private var auth
    @State private var guideKind: CalendarLinkKind?

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                Text("Add your school's calendar by link")
                    .font(.subheadline).foregroundStyle(.secondary)
                LMSSystemButtons(isDemo: auth.user?.isDemo == true) { guideKind = $0 }
            }
            .padding(.vertical, 4)
            .sheet(item: $guideKind) { kind in CalendarLinkGuide(kind: kind) }
        } header: {
            Text("Canvas, Schoology & Brightspace")
        }
    }
}

/// Canvas / Schoology / Brightspace buttons, each opening that system's guide.
private struct LMSSystemButtons: View {
    var isDemo: Bool
    var onSelect: (CalendarLinkKind) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(CalendarLinkKind.lms) { kind in
                Button { onSelect(kind) } label: {
                    Text(verbatim: kind.systemName)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity)
                }
                .glassButtonStyle()
                .disabled(isDemo)
            }
        }
    }
}

// MARK: - Guide steps

/// Steps 1–2 of the Canvas, Schoology and Brightspace guides (step 3 is the
/// shared paste form).
struct LMSGuideStep: View {
    var kind: CalendarLinkKind
    var step: Int

    var body: some View {
        if step == 1 {
            LMSOpenStep(kind: kind)
        } else {
            LMSCopyStep(kind: kind)
        }
    }
}

private struct LMSOpenStep: View {
    var kind: CalendarLinkKind

    private var text: LocalizedStringKey {
        switch kind {
        case .schoology: return "Sign in to Schoology in a web browser and open **Calendar** from the top menu."
        case .brightspace: return "Sign in to Brightspace in a web browser and open **Calendar** (from the homepage or a course)."
        default: return "Sign in to Canvas in a web browser and select **Calendar** in the menu on the left."
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            GuideIntro(text: "If your school uses Canvas, Schoology or Brightspace, paste your calendar link from it and Study Spot will import your due dates. It works without your school approving anything.")
            GuideStepTitle(number: 1, title: "Open your \(kind.systemName) calendar")
            Text(text)
        }
    }
}

private struct LMSCopyStep: View {
    var kind: CalendarLinkKind

    private var title: String {
        switch kind {
        case .schoology: return "Copy the iCal link"
        case .brightspace: return "Copy the subscription link"
        default: return "Copy the calendar feed"
        }
    }

    private var text: LocalizedStringKey {
        switch kind {
        case .schoology: return "Select **Export** (or the calendar's export/iCal option) and copy the iCal feed link. If you don't see it, your school may have turned it off."
        case .brightspace: return "Select **Subscribe**, choose **All Courses**, and copy the link. It contains **feed.ics?token=**."
        default: return "At the bottom of the right-hand side, select **Calendar Feed**, then copy the link. It ends in **.ics**."
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            GuideStepTitle(number: 2, title: title)
            Text(text)
            LMSLinkIllustration(kind: kind)
            GuideNote(symbol: "exclamationmark.triangle", tint: .orange) {
                Text("Don't see it?").font(.footnote.weight(.semibold))
                Text("Your school may have turned it off. Ask your school's IT team or add assignments yourself.")
            }
            PrivacyNote(kind: kind)
        }
    }
}

// MARK: - Illustration

/// The system's "Calendar Feed" / "Export" / "Subscribe" control highlighted,
/// and the feed link with its copy action.
private struct LMSLinkIllustration: View {
    var kind: CalendarLinkKind

    private var control: String {
        switch kind {
        case .schoology: return "Export"
        case .brightspace: return "Subscribe"
        default: return "Calendar Feed"
        }
    }

    private var link: String {
        switch kind {
        case .schoology: return "…/calendar/feed/ical/…/ical.ics"
        case .brightspace: return "…/d2l/le/calendar/feed/user/feed.ics?token=…"
        default: return "https://…/feeds/calendars/user_….ics"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Calendar")
                .font(.subheadline.weight(.semibold))
            HStack {
                Spacer()
                MockMenuItem(text: control, highlighted: true)
                    .frame(width: 160)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .strokeBorder(Color.accentColor, lineWidth: 1.5)
                    }
            }
            MockLinkField(text: link, truncation: .head)
        }
        .padding(14)
        .frame(maxWidth: 360)
        .glassCard(cornerRadius: 16)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Illustration: \(control) highlighted, and the calendar link with its copy button.")
    }
}
