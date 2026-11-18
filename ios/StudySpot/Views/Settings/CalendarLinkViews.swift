import SwiftUI

// Google Calendar links: for students whose school blocks connecting their
// Google account, Study Spot can import a calendar from its public URL (or
// its secret iCal address) instead (server/src/functions/ical.js). This file has the
// Settings → Google section that lists the links, and the step-by-step
// guide for adding one.

private let googleCalendarSettingsURL = URL(string: "https://calendar.google.com/calendar/r/settings")!

// MARK: - Settings section

/// "Can't connect your Google account?" plus the list of added links.
/// Shown under the Calendar/Classroom rows in Settings → Google (and so in
/// onboarding's services step too).
struct CalendarLinksSettings: View {
    var onAdd: () -> Void
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data

    private var isDemo: Bool { auth.user?.isDemo == true }

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                Text("Paste your calendar's public link instead and Study Spot will import your assignments from it.")
                    .font(.subheadline).foregroundStyle(.secondary)
                HStack {
                    Spacer()
                    Button(action: onAdd) {
                        Label("Add a calendar link", systemImage: "link.badge.plus")
                    }
                    .glassButtonStyle()
                    .disabled(isDemo)
                }
            }
            .padding(.vertical, 4)
            .task { await data.loadICalFeeds() }
        } header: {
            Text("Can't connect your Google account?")
        } footer: {
            if isDemo { Text("Calendar links aren't available on the demo account.") }
        }
        if !data.icalFeeds.isEmpty {
            Section {
                ForEach(data.icalFeeds) { feed in
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

/// One added calendar link: its name, sync status and actions.
struct CalendarLinkRow: View {
    var feed: ICalFeed
    @Environment(DataStore.self) private var data
    @Environment(ToastCenter.self) private var toasts
    @State private var busy = false
    @State private var renaming = false
    @State private var newName = ""
    @State private var confirmingRemove = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "calendar").foregroundStyle(Palette.secondary).frame(width: 24)
            VStack(alignment: .leading, spacing: 3) {
                Text(feed.label).font(.body.weight(.medium)).lineLimit(2)
                statusLine
                if feed.importsEverything { Pill(text: "Every event") }
            }
            Spacer(minLength: 8)
            if busy { ProgressView() } else { actionsMenu }
        }
        .padding(.vertical, 2)
        .alert("Rename calendar", isPresented: $renaming) {
            TextField("Name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Save") { Task { await update(["label": .string(newName)], resync: false) } }
        } message: {
            Text("Its class in Study Spot gets the new name too.")
        }
        .confirmationDialog("Remove “\(feed.label)”?", isPresented: $confirmingRemove, titleVisibility: .visible) {
            Button("Keep imported assignments") { Task { await remove(deleteAssignments: false) } }
            Button("Also delete its assignments", role: .destructive) { Task { await remove(deleteAssignments: true) } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Study Spot will stop checking this calendar.")
        }
    }

    @ViewBuilder private var statusLine: some View {
        if let err = feed.lastError {
            Text(verbatim: "Couldn't sync: \(err)").font(.caption).foregroundStyle(.red)
        } else {
            Text(verbatim: syncedText).font(.caption).foregroundStyle(.secondary)
        }
    }

    private var syncedText: String {
        guard let last = feed.lastSyncedAt else { return "Not synced yet" }
        var when = "just now"
        if Date().timeIntervalSince(last) >= 60 {
            let f = RelativeDateTimeFormatter()
            f.unitsStyle = .short
            when = f.localizedString(for: last, relativeTo: Date())
        }
        var text = "Synced \(when)"
        if let n = feed.lastImported, n > 0 { text += " · \(n) new" }
        return text
    }

    private var actionsMenu: some View {
        Menu {
            Button { Task { await syncNow() } } label: {
                Label("Sync now", systemImage: "arrow.triangle.2.circlepath")
            }
            Button { newName = feed.label; renaming = true } label: {
                Label("Rename", systemImage: "pencil")
            }
            Toggle(isOn: modeBinding) {
                Label("Import every event", systemImage: "tray.full")
            }
            Divider()
            Button(role: .destructive) { confirmingRemove = true } label: {
                Label("Remove", systemImage: "trash")
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.title3)
                .foregroundStyle(Palette.secondary)
                .frame(width: 32, height: 32)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Actions for \(feed.label)")
    }

    private var modeBinding: Binding<Bool> {
        Binding(
            get: { feed.importsEverything },
            set: { all in Task { await update(["mode": .string(all ? "all" : "auto")], resync: true) } }
        )
    }

    private func syncNow() async {
        busy = true
        defer { busy = false }
        await runSync(announce: true)
    }

    /// icalSyncFeeds syncs all of the user's links.
    private func runSync(announce: Bool) async {
        do {
            let r = try await APIClient.shared.invoke("icalSyncFeeds")
            let imported = Int(r["imported"]?.doubleValue ?? 0)
            let errors = r["errors"]?.arrayValue ?? []
            if let mine = errors.first(where: { $0["id"]?.stringValue == feed.id }) {
                toasts.show("Couldn't sync", mine["error"]?.stringValue, error: true)
            } else if announce {
                toasts.show("Synced", imported == 1 ? "1 new assignment" : "\(imported) new assignments")
            }
        } catch {
            toasts.show("Couldn't sync", error.localizedDescription, error: true)
        }
        await data.loadICalFeeds()
        await data.reloadData()
    }

    private func update(_ patch: [String: JSONValue], resync: Bool) async {
        busy = true
        defer { busy = false }
        var payload = patch
        payload["id"] = .string(feed.id)
        do {
            try await APIClient.shared.invoke("icalUpdateFeed", payload)
        } catch {
            toasts.show("Couldn't save", error.localizedDescription, error: true)
            await data.loadICalFeeds()
            return
        }
        if resync {
            await runSync(announce: false)
        } else {
            await data.loadICalFeeds()
            await data.reloadData()
        }
    }

    private func remove(deleteAssignments: Bool) async {
        busy = true
        defer { busy = false }
        do {
            try await APIClient.shared.invoke("icalRemoveFeed", [
                "id": .string(feed.id), "removeAssignments": .bool(deleteAssignments),
            ])
            toasts.show("Calendar removed")
        } catch {
            toasts.show("Couldn't remove", error.localizedDescription, error: true)
        }
        await data.loadICalFeeds()
        await data.refreshConnections()
        await data.reloadData()
    }
}

// MARK: - Guide

/// "Add your Google Calendar with a link": a step-by-step sheet (5 steps and
/// a done screen) ending in the form that adds the link.
struct CalendarLinkGuide: View {
    enum Page: Int { case open = 1, settings, makePublic, copy, paste, done }

    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data
    @Environment(ThemeStore.self) private var theme

    @State private var page: Page = .open
    @State private var link = ""
    @State private var name = ""
    @State private var everyEvent = false
    @State private var adding = false
    @State private var error = ""
    @State private var imported = 0
    @State private var addedLabel = ""

    private var isDemo: Bool { auth.user?.isDemo == true }
    private var trimmedLink: String { link.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if page != .done {
                    GuideProgress(step: page.rawValue, total: 5)
                }
                ScrollView {
                    pageContent
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                }
                .scrollDismissesKeyboard(.interactively)
                .id(page)
                .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                        removal: .move(edge: .leading).combined(with: .opacity)))
                bottomBar
            }
            .appBackground()
            .navigationTitle("Calendar link")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(page == .done ? "Close" : "Cancel") { dismiss() }
                        .disabled(adding)
                }
            }
        }
        .interactiveDismissDisabled(adding)
    }

    @ViewBuilder private var pageContent: some View {
        switch page {
        case .open:
            GuideOpenStep()
        case .settings:
            GuideSettingsStep()
        case .makePublic:
            GuideMakePublicStep()
        case .copy:
            GuideCopyStep()
        case .paste:
            GuidePasteStep(link: $link, name: $name, everyEvent: $everyEvent,
                           error: error, isDemo: isDemo, adding: adding)
        case .done:
            GuideDoneView(imported: imported, label: addedLabel)
        }
    }

    @ViewBuilder private var bottomBar: some View {
        HStack {
            if page == .done {
                Button { restart() } label: { Label("Add another calendar", systemImage: "plus") }
                    .glassButtonStyle()
                Spacer()
                Button { dismiss() } label: { Text("Done").frame(minWidth: 56) }
                    .glassButtonStyle(prominent: true)
            } else {
                Button { go(-1) } label: { Label("Back", systemImage: "arrow.left") }
                    .disabled(page == .open || adding)
                Spacer()
                if page == .paste {
                    addButton
                } else {
                    Button { go(1) } label: { Label("Next", systemImage: "arrow.right") }
                        .glassButtonStyle(prominent: true)
                }
            }
        }
        .padding()
    }

    private var addButton: some View {
        Button { Task { await add() } } label: {
            if adding {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Importing…")
                }
            } else {
                Label("Add calendar", systemImage: "calendar.badge.plus")
            }
        }
        .glassButtonStyle(prominent: true)
        .disabled(adding || isDemo || trimmedLink.isEmpty)
    }

    private func go(_ delta: Int) {
        guard let next = Page(rawValue: page.rawValue + delta), next != .done else { return }
        withAnimation(theme.animation(.spring(duration: 0.35))) { page = next }
    }

    private func restart() {
        link = ""
        name = ""
        everyEvent = false
        error = ""
        withAnimation(theme.animation(.spring(duration: 0.35))) { page = .settings }
    }

    private func add() async {
        let url = trimmedLink
        guard !url.isEmpty, !isDemo else { return }
        error = ""
        adding = true
        var payload: [String: JSONValue] = ["url": .string(url), "mode": .string(everyEvent ? "all" : "auto")]
        let label = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !label.isEmpty { payload["label"] = .string(label) }
        do {
            let res = try await APIClient.shared.invoke("icalAddFeed", payload)
            imported = Int(res["imported"]?.doubleValue ?? 0)
            addedLabel = res["feed"]?["label"]?.stringValue ?? "your calendar"
        } catch {
            self.error = error.localizedDescription
            adding = false
            return
        }
        adding = false
        withAnimation(theme.animation(.spring(duration: 0.35))) { page = .done }
        await data.refreshConnections()
        await data.loadICalFeeds()
        await data.reloadData()
    }
}

// MARK: - Guide pieces

private struct GuideProgress: View {
    var step: Int
    var total: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Add your Google Calendar with a link")
                .font(.headline)
                .foregroundStyle(Palette.secondary)
            Text("Step \(step) of \(total)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            HStack(spacing: 4) {
                ForEach(1...total, id: \.self) { i in
                    Capsule()
                        .fill(i <= step ? Color.accentColor : Palette.secondary.opacity(0.15))
                        .frame(height: 5)
                }
            }
            .accessibilityHidden(true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal)
        .padding(.top, 8)
    }
}

private struct GuideStepTitle: View {
    var number: Int
    var title: String

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            Text("\(number)")
                .font(.subheadline.bold())
                .foregroundStyle(Palette.base)
                .frame(width: 26, height: 26)
                .background(Palette.secondary, in: Circle())
                .accessibilityHidden(true)
            Text(title)
                .font(.title3.bold())
                .foregroundStyle(Palette.secondary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// A tinted box for tips, warnings and the privacy note.
private struct GuideNote<Content: View>: View {
    var symbol: String
    var tint: Color
    @ViewBuilder var content: Content

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbol).foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 8) { content }
        }
        .font(.footnote)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tint.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct GuideOpenStep: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("If your school won't let Study Spot connect to your Google account, you can paste your calendar's public link instead. Study Spot reads your calendar and imports your assignments. It takes about a minute.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glassCard(cornerRadius: 14)
            GuideStepTitle(number: 1, title: "Open Google Calendar on a computer")
            Text(verbatim: "Go to calendar.google.com in a web browser and sign in with your school account. These settings aren't in the Google Calendar phone app. On a phone, open the page in your browser and choose “Desktop site”.")
            Button {
                openURL(googleCalendarSettingsURL)
            } label: {
                Label("Open Google Calendar", systemImage: "arrow.up.right.square")
            }
            .glassButtonStyle(prominent: true)
        }
    }
}

private struct GuideSettingsStep: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            GuideStepTitle(number: 2, title: "Open the calendar's settings")
            Text("In the list on the left under “My calendars”, point to the calendar that has your assignments, click the three dots (⋮), then choose **Settings and sharing**.")
            CalendarMenuIllustration()
            GuideNote(symbol: "lightbulb", tint: Palette.secondary) {
                Text("Each Google Classroom class has its own calendar named after the class. Your main calendar has your name. Add each one you want, one at a time.")
            }
        }
    }
}

private struct GuideMakePublicStep: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            GuideStepTitle(number: 3, title: "Make the calendar public")
            Text("Under **Access permissions for events**, turn on **Make available to public** and choose **See all event details**. If Google shows a warning, click **OK**.")
            PublicAccessIllustration()
            GuideNote(symbol: "eye", tint: Palette.secondary) {
                Text("Anyone with the link can see this calendar's events. Prefer to keep it private? Copy the **Secret address in iCal format** (under Integrate calendar) instead. Study Spot accepts that too.")
            }
            GuideNote(symbol: "exclamationmark.triangle", tint: .orange) {
                Text("Don't see this option, or it's greyed out?").font(.footnote.weight(.semibold))
                Text("Your school has turned off public calendars. Email your school's IT team or add assignments yourself.")
                SchoolITEmailButton(service: .google)
            }
        }
    }
}

private struct GuideCopyStep: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            GuideStepTitle(number: 4, title: "Copy the public URL")
            Text("Scroll down to **Integrate calendar** and copy **Public URL to this calendar**.")
            Text(verbatim: "It starts with https://calendar.google.com/calendar/embed?src=")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            PublicURLIllustration()
            PrivacyNote()
        }
    }
}

private struct PrivacyNote: View {
    var body: some View {
        GuideNote(symbol: "lock", tint: Palette.secondary) {
            Text("Study Spot only reads this calendar. If you turn off public access later, Study Spot stops getting updates from it.")
                .foregroundStyle(.secondary)
        }
    }
}

private struct GuidePasteStep: View {
    @Binding var link: String
    @Binding var name: String
    @Binding var everyEvent: Bool
    var error: String
    var isDemo: Bool
    var adding: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            GuideStepTitle(number: 5, title: "Paste it here")
            linkField
            nameField
            modePicker
            if isDemo {
                Text("Calendar links aren't available on the demo account.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            PrivacyNote()
        }
        .disabled(adding)
    }

    private var linkField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Calendar link").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            HStack(spacing: 8) {
                TextField("https://calendar.google.com/calendar/embed?src=…", text: $link)
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                Button(action: paste) {
                    Label("Paste", systemImage: "doc.on.clipboard")
                }
                .glassButtonStyle()
            }
            if !error.isEmpty {
                Text(verbatim: error).font(.footnote).foregroundStyle(.red)
            }
        }
    }

    private var nameField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Name (optional)").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            TextField("AP Biology", text: $name)
                .textFieldStyle(.roundedBorder)
        }
    }

    private var modePicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("What to import").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ImportModeOption(title: "Only events that look like assignments",
                             desc: "Homework, quizzes, tests, projects and everything from Google Classroom.",
                             selected: !everyEvent) { everyEvent = false }
            ImportModeOption(title: "Every event in this calendar", desc: nil,
                             selected: everyEvent) { everyEvent = true }
        }
    }

    private func paste() {
        if let s = UIPasteboard.general.string {
            link = s.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }
}

private struct ImportModeOption: View {
    var title: String
    var desc: String?
    var selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(selected ? Color.accentColor : Palette.secondary.opacity(0.5))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline.weight(.medium)).foregroundStyle(.primary)
                    if let desc {
                        Text(desc).font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(selected ? Color.accentColor : Palette.secondary.opacity(0.2), lineWidth: selected ? 1.5 : 1)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct GuideDoneView: View {
    var imported: Int
    var label: String

    private var summary: String {
        if imported == 0 { return "No assignments found yet — new ones will appear automatically." }
        return "Imported \(imported) assignment\(imported == 1 ? "" : "s") from \(label)."
    }

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(.green)
                .accessibilityHidden(true)
            Text("You're all set")
                .font(.title2.bold())
                .foregroundStyle(Palette.secondary)
            Text(verbatim: summary)
                .multilineTextAlignment(.center)
            Text("Study Spot checks this calendar for changes every couple of hours and whenever you sync. Add each class calendar the same way.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 24)
    }
}

// MARK: - Illustrations (drawn, no Google artwork)

/// A calendar sidebar row with its ⋮ menu open on "Settings and sharing".
private struct CalendarMenuIllustration: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("My calendars")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            MockCalendarRow(color: .blue, name: "Your Name", active: false)
            MockCalendarRow(color: .green, name: "AP Biology", active: true)
            HStack {
                Spacer()
                VStack(alignment: .leading, spacing: 0) {
                    MockMenuItem(text: "Display this only", highlighted: false)
                    MockMenuItem(text: "Hide from list", highlighted: false)
                    MockMenuItem(text: "Settings and sharing", highlighted: true)
                }
                .frame(width: 200)
                .padding(.vertical, 4)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(Palette.secondary.opacity(0.15))
                }
                .shadow(color: .black.opacity(0.12), radius: 8, y: 3)
            }
        }
        .padding(14)
        .frame(maxWidth: 360)
        .glassCard(cornerRadius: 16)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Illustration: the AP Biology calendar's three-dot menu, with Settings and sharing highlighted.")
    }
}

private struct MockCalendarRow: View {
    var color: Color
    var name: String
    var active: Bool

    var body: some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 3)
                .fill(color)
                .frame(width: 14, height: 14)
                .overlay {
                    Image(systemName: "checkmark")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.white)
                }
            Text(verbatim: name).font(.footnote)
            Spacer()
            if active {
                Text(verbatim: "⋮")
                    .font(.headline)
                    .frame(width: 26, height: 26)
                    .background(Color.accentColor.opacity(0.2), in: Circle())
                    .overlay { Circle().strokeBorder(Color.accentColor, lineWidth: 1.5) }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background(active ? Palette.secondary.opacity(0.08) : Color.clear,
                    in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

private struct MockMenuItem: View {
    var text: String
    var highlighted: Bool

    var body: some View {
        Text(verbatim: text)
            .font(highlighted ? .footnote.weight(.semibold) : .footnote)
            .foregroundStyle(highlighted ? Color.primary : Color.secondary)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(highlighted ? Color.accentColor.opacity(0.2) : Color.clear)
    }
}

/// The "Make available to public" checkbox with its "See all event details" menu.
private struct PublicAccessIllustration: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Access permissions for events")
                .font(.subheadline.weight(.semibold))
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.accentColor)
                    .frame(width: 18, height: 18)
                    .overlay {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white)
                    }
                Text("Make available to public").font(.footnote.weight(.semibold))
                Spacer(minLength: 0)
            }
            .padding(8)
            .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(Color.accentColor, lineWidth: 1.5)
            }
            HStack(spacing: 6) {
                Text("See all event details").font(.footnote)
                Image(systemName: "chevron.down").font(.caption2.weight(.semibold))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .overlay {
                RoundedRectangle(cornerRadius: 6).strokeBorder(Palette.secondary.opacity(0.3))
            }
            .padding(.leading, 28)
        }
        .padding(14)
        .frame(maxWidth: 360)
        .glassCard(cornerRadius: 16)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Illustration: Make available to public checked, with See all event details chosen.")
    }
}

/// The "Public URL to this calendar" box with its copy action highlighted.
private struct PublicURLIllustration: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Integrate calendar")
                .font(.subheadline.weight(.semibold))
            Text("Public URL to this calendar")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack(spacing: 8) {
                Text(verbatim: "https://calendar.google.com/calendar/embed?src=…")
                    .font(.caption2.monospaced())
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
                    .overlay {
                        RoundedRectangle(cornerRadius: 6).strokeBorder(Palette.secondary.opacity(0.2))
                    }
                Image(systemName: "doc.on.doc")
                    .font(.footnote)
                    .frame(width: 32, height: 32)
                    .background(Color.accentColor.opacity(0.2), in: Circle())
                    .overlay { Circle().strokeBorder(Color.accentColor, lineWidth: 1.5) }
            }
            Text("Select the link in the box and copy it.")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(14)
        .frame(maxWidth: 360)
        .glassCard(cornerRadius: 16)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Illustration: the Public URL to this calendar box, with the link selected to copy.")
    }
}
