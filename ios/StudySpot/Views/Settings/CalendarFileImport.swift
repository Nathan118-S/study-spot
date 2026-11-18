import SwiftUI
import UniformTypeIdentifiers

// One-time import of a calendar file, for when calendar links are turned off
// too: Google Calendar's export .zip, or any .ics file (server: icalImportFile
// in server/src/functions/ical.js). Opened from Settings → Google's
// "Can't connect your Google account?" section.

private let googleCalendarExportURL = URL(string: "https://calendar.google.com/calendar/r/settings/export")!
private let maxCalendarFileBytes = 10 * 1024 * 1024

/// Reads a file chosen in the document picker, with security-scoped access.
/// Kept off the main actor so a large file doesn't block the UI.
private func readPickedCalendarFile(_ url: URL) throws -> Data {
    let access = url.startAccessingSecurityScopedResource()
    defer { if access { url.stopAccessingSecurityScopedResource() } }
    return try Data(contentsOf: url)
}

/// "Import a calendar file": two numbered steps, a file picker, and the result.
struct CalendarFileImportSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthStore.self) private var auth
    @Environment(DataStore.self) private var data

    @State private var picking = false
    @State private var everyEvent = false
    @State private var importing = false
    @State private var error = ""
    @State private var result: String?

    private var isDemo: Bool { auth.user?.isDemo == true }

    /// .ics files and Google Calendar's export .zip.
    private static let fileTypes: [UTType] = {
        var types: [UTType] = [.calendarEvent, .zip]
        if let ics = UTType(filenameExtension: "ics") { types.insert(ics, at: 0) }
        return types
    }()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("For when calendar links are turned off too. It's a one-time import: new assignments won't appear automatically, so import again later to pick them up.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    FileImportExportStep()
                    FileImportNumberedStep(number: 2) {
                        Text("Choose that .zip file here (or any .ics calendar file).")
                    }
                    Toggle("Import every event", isOn: $everyEvent)
                        .disabled(importing || isDemo)
                    chooseButton
                    status
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
            }
            .appBackground()
            .navigationTitle("Import a calendar file")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(result == nil ? "Cancel" : "Done") { dismiss() }
                        .disabled(importing)
                }
            }
            .fileImporter(isPresented: $picking, allowedContentTypes: Self.fileTypes) { picked in
                switch picked {
                case .success(let url):
                    Task { await importFile(url) }
                case .failure(let err):
                    error = err.localizedDescription
                }
            }
        }
        .interactiveDismissDisabled(importing)
    }

    private var chooseButton: some View {
        HStack {
            Spacer()
            Button {
                error = ""
                picking = true
            } label: {
                if importing {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("Importing…")
                    }
                } else {
                    Label("Choose file…", systemImage: "doc")
                }
            }
            .glassButtonStyle(prominent: true)
            .disabled(importing || isDemo)
        }
    }

    @ViewBuilder private var status: some View {
        if !error.isEmpty {
            Text(verbatim: error).font(.footnote).foregroundStyle(.red)
        }
        if let result {
            Label {
                Text(verbatim: result)
            } icon: {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
            }
            .font(.subheadline)
        }
        if isDemo {
            Text("Importing calendars isn't available on the demo account.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private func importFile(_ url: URL) async {
        guard !isDemo, !importing else { return }
        error = ""
        result = nil
        importing = true
        defer { importing = false }

        let fileData: Data
        do {
            fileData = try await Task.detached(priority: .userInitiated) {
                try readPickedCalendarFile(url)
            }.value
        } catch {
            self.error = "That file couldn’t be read."
            return
        }
        guard fileData.count <= maxCalendarFileBytes else {
            error = "That file is too large (10 MB max)"
            return
        }

        let payload: [String: JSONValue] = [
            "filename": .string(url.lastPathComponent),
            "data": .string(fileData.base64EncodedString()),
            "mode": .string(everyEvent ? "all" : "auto"),
        ]
        do {
            let res = try await APIClient.shared.invoke("icalImportFile", payload)
            result = Self.summary(res)
        } catch {
            self.error = error.localizedDescription
            return
        }
        await data.reloadData()
    }

    /// "Imported N assignments from <calendar names>." or "No new assignments found."
    private static func summary(_ res: JSONValue) -> String {
        let imported = Int(res["imported"]?.doubleValue ?? 0)
        guard imported > 0 else { return "No new assignments found." }
        let calendars = res["calendars"]?.arrayValue ?? []
        let names = calendars
            .filter { ($0["imported"]?.doubleValue ?? 0) > 0 }
            .compactMap { $0["name"]?.stringValue }
        let what = "Imported \(imported) assignment\(imported == 1 ? "" : "s")"
        guard !names.isEmpty else { return what + "." }
        return what + " from " + ListFormatter.localizedString(byJoining: names) + "."
    }
}

/// Step 1: export from Google Calendar on a computer.
private struct FileImportExportStep: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        FileImportNumberedStep(number: 1) {
            Text("On a computer, open calendar.google.com → ⚙ **Settings** → **Import & export** → **Export**. Google downloads a .zip file.")
            Button {
                openURL(googleCalendarExportURL)
            } label: {
                Label("Open Google Calendar settings", systemImage: "arrow.up.right.square")
            }
            .glassButtonStyle()
        }
    }
}

/// A numbered item in the sheet's list of steps.
private struct FileImportNumberedStep<Content: View>: View {
    var number: Int
    @ViewBuilder var content: Content

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(number)")
                .font(.subheadline.bold())
                .foregroundStyle(Palette.base)
                .frame(width: 26, height: 26)
                .background(Palette.secondary, in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 10) { content }
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .contain)
    }
}
