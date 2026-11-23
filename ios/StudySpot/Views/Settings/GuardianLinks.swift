import SwiftUI
import UIKit

// Settings → Parent view: private, read-only links to a weekly summary for a
// parent or guardian (server: guardianListLinks / guardianCreateLink /
// guardianUpdateLink / guardianRevokeLink). The parent page itself is on the
// website (/parent/<token>); the full link is only shown once, when it's made.

struct GuardianLink: Decodable, Identifiable, Equatable, Sendable {
    var id: String
    var label: String
    var showGrades: Bool
    var createdAt: Date?
    var lastViewedAt: Date?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? ""
        label = c.flexString(.label) ?? "Parent link"
        showGrades = c.flexBool(.showGrades) ?? false
        createdAt = c.flexDate(.createdAt)
        lastViewedAt = c.flexDate(.lastViewedAt)
    }

    enum CodingKeys: String, CodingKey {
        case id, label
        case showGrades = "show_grades"
        case createdAt = "created_at"
        case lastViewedAt = "last_viewed_at"
    }
}

struct GuardianLinkList: Decodable, Sendable {
    var links: [GuardianLink]
    var max: Int?
}

struct CreatedGuardianLink: Decodable, Sendable {
    var link: GuardianLink
    var url: String
}

private struct GuardianLinkUpdate: Decodable, Sendable {
    var link: GuardianLink
}

extension APIClient {
    func guardianLinks() async throws -> GuardianLinkList {
        try await invoke("guardianListLinks", as: GuardianLinkList.self)
    }

    func createGuardianLink(label: String, showGrades: Bool) async throws -> CreatedGuardianLink {
        try await invoke("guardianCreateLink", ["label": .string(label), "show_grades": .bool(showGrades)],
                         as: CreatedGuardianLink.self)
    }

    func updateGuardianLink(id: String, showGrades: Bool) async throws -> GuardianLink {
        try await invoke("guardianUpdateLink", ["id": .string(id), "show_grades": .bool(showGrades)],
                         as: GuardianLinkUpdate.self).link
    }

    func revokeGuardianLink(id: String) async throws {
        try await invoke("guardianRevokeLink", ["id": .string(id)])
    }
}

struct GuardianLinksSettings: View {
    @Environment(AuthStore.self) private var auth
    @Environment(ToastCenter.self) private var toasts
    @State private var links: [GuardianLink] = []
    @State private var maxLinks = 5
    @State private var loaded = false
    @State private var newLabel = ""
    @State private var newShowGrades = false
    @State private var creating = false
    @State private var busyId: String?
    @State private var created: (id: String, label: String, url: String)?
    @State private var copied = false
    @State private var revoking: GuardianLink?
    @State private var confirmRevoke = false

    private var isDemo: Bool { auth.user?.isDemo == true }

    var body: some View {
        Section {
            Label {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Give a parent or guardian a private, read-only link to your week: what's due and overdue, what you finished in the last 7 days, your streak and your study time. Grades are only included if you turn them on.")
                    Text("They don't need an account, and you can turn a link off at any time.")
                }
                .font(.subheadline).foregroundStyle(.secondary)
            } icon: {
                Image(systemName: "person.2").foregroundStyle(Palette.secondary)
            }
            .padding(.vertical, 4)
        } footer: {
            if isDemo { Text("Parent links aren't available on the demo account.") }
        }
        .task { await load() }
        .alert("Turn off \(revoking?.label ?? "this")'s link?", isPresented: $confirmRevoke, presenting: revoking) { link in
            Button("Turn off", role: .destructive) { Task { await revoke(link) } }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("The link stops working right away. You can make a new one later.")
        }

        if let created {
            Section {
                Text(created.url)
                    .font(.footnote.monospaced())
                    .textSelection(.enabled)
                    .lineLimit(2)
                Button {
                    UIPasteboard.general.string = created.url
                    copied = true
                    Task {
                        try? await Task.sleep(for: .seconds(1.5))
                        copied = false
                    }
                } label: {
                    Label(copied ? "Copied" : "Copy link", systemImage: copied ? "checkmark" : "doc.on.doc")
                }
                if let url = URL(string: created.url) {
                    ShareLink(item: url, message: Text("Here's my weekly Study Spot summary:")) {
                        Label("Share…", systemImage: "square.and.arrow.up")
                    }
                }
            } header: {
                Text("Link for \(created.label) is ready")
            } footer: {
                Text("Copy or share it now: for privacy, Study Spot can't show it again.")
            }
        }

        if !links.isEmpty {
            Section {
                ForEach(links) { link in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(link.label).font(.body.weight(.medium))
                                Text(viewedLine(link)).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if busyId == link.id { ProgressView() }
                            Button("Turn off", role: .destructive) {
                                revoking = link
                                confirmRevoke = true
                            }
                            .buttonStyle(.borderless)
                            .font(.subheadline)
                        }
                        Toggle("Show grades", isOn: Binding(
                            get: { link.showGrades },
                            set: { on in Task { await setGrades(link, on) } }
                        ))
                        .font(.subheadline)
                        .disabled(busyId == link.id)
                    }
                    .padding(.vertical, 2)
                }
            } header: {
                Text("Your parent links")
            }
        }

        if loaded && links.count < maxLinks {
            Section {
                TextField("Who is it for? e.g. Mom", text: $newLabel)
                    .textInputAutocapitalization(.words)
                    .disabled(isDemo)
                Toggle(isOn: $newShowGrades) {
                    SettingLabel(title: "Show grades", desc: "Your grade in each class, from scored assignments.", symbol: "graduationcap")
                }
                .disabled(isDemo)
                Button {
                    Task { await create() }
                } label: {
                    HStack {
                        Label("Create link", systemImage: "plus.circle")
                        if creating { Spacer(); ProgressView() }
                    }
                }
                .disabled(isDemo || creating || newLabel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            } header: {
                Text("New parent link")
            }
        } else if loaded {
            Section {
                Text("You have \(maxLinks) parent links, the most you can have. Turn one off to add another.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    private func viewedLine(_ link: GuardianLink) -> String {
        guard let date = link.lastViewedAt else { return "Not opened yet" }
        let f = RelativeDateTimeFormatter()
        f.unitsStyle = .full
        return "Last opened \(f.localizedString(for: date, relativeTo: Date()))"
    }

    private func load() async {
        guard !isDemo else { loaded = true; return }
        do {
            let res = try await APIClient.shared.guardianLinks()
            links = res.links
            if let max = res.max { maxLinks = max }
        } catch {
            toasts.show("Couldn't load parent links", error.localizedDescription, error: true)
        }
        loaded = true
    }

    private func create() async {
        let label = newLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !label.isEmpty else { return }
        creating = true
        defer { creating = false }
        do {
            let res = try await APIClient.shared.createGuardianLink(label: label, showGrades: newShowGrades)
            created = (res.link.id, res.link.label, res.url)
            copied = false
            newLabel = ""
            newShowGrades = false
            await load()
        } catch {
            toasts.show("Couldn't create the link", error.localizedDescription, error: true)
        }
    }

    private func setGrades(_ link: GuardianLink, _ on: Bool) async {
        busyId = link.id
        defer { busyId = nil }
        if let i = links.firstIndex(where: { $0.id == link.id }) { links[i].showGrades = on }
        do {
            let updated = try await APIClient.shared.updateGuardianLink(id: link.id, showGrades: on)
            if let i = links.firstIndex(where: { $0.id == link.id }) { links[i] = updated }
        } catch {
            toasts.show("Couldn't update the link", error.localizedDescription, error: true)
            await load()
        }
    }

    private func revoke(_ link: GuardianLink) async {
        busyId = link.id
        defer { busyId = nil }
        do {
            try await APIClient.shared.revokeGuardianLink(id: link.id)
            if created?.id == link.id { created = nil }
            await load()
        } catch {
            toasts.show("Couldn't turn the link off", error.localizedDescription, error: true)
        }
    }
}
