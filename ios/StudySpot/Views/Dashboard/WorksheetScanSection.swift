import PhotosUI
import SwiftUI
import VisionKit

/// "Scan worksheet" on the new-assignment form: scan a handout with the
/// document camera (or pick a photo), read it on the device with Vision, and
/// hand the parsed draft to `onScanned` to prefill the form. Nothing is
/// uploaded. Mirrors the Android app's ui/dashboard/WorksheetScanCard.kt.
struct WorksheetScanSection: View {
    let classes: [SchoolClass]
    let onScanned: (WorksheetParser.Result) -> Void

    @State private var showCamera = false
    @State private var photoItem: PhotosPickerItem?
    @State private var working = false
    @State private var status: String?

    private var cameraSupported: Bool { VNDocumentCameraViewController.isSupported }

    var body: some View {
        Section {
            if cameraSupported {
                Button { showCamera = true } label: {
                    Label("Scan worksheet", systemImage: "doc.viewfinder")
                }
                .disabled(working)
            }
            PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                Label(cameraSupported ? "Choose a photo" : "Scan worksheet from a photo", systemImage: "photo.on.rectangle")
            }
            .disabled(working)
            if working {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Reading the worksheet…").foregroundStyle(.secondary)
                }
            } else if let status {
                Text(status).font(.footnote).foregroundStyle(Palette.secondary)
            }
        } header: {
            Text("Scan a worksheet")
        } footer: {
            Text("Fills in the title, due date and class from a handout. Text is read on your device; nothing is uploaded.")
        }
        .fullScreenCover(isPresented: $showCamera) {
            DocumentCameraView { images in
                showCamera = false
                read(images)
            } onCancel: {
                showCamera = false
            }
            .ignoresSafeArea()
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            photoItem = nil
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else {
                    status = "That photo couldn't be opened."
                    return
                }
                read([image])
            }
        }
    }

    private func read(_ images: [UIImage]) {
        guard !images.isEmpty else { return }
        working = true
        status = nil
        Task {
            defer { working = false }
            do {
                let lines = try await WorksheetOCR.recognizeLines(in: images)
                guard !lines.isEmpty else {
                    status = "No text found. Try again with the whole page in view and good light."
                    return
                }
                let result = WorksheetParser.parse(lines, classes: classes.map { .init(id: $0.id, name: $0.name) })
                onScanned(result)
                status = "Filled in from the worksheet. Check the details, then save."
            } catch {
                status = "Couldn't read the worksheet: \(error.localizedDescription)"
            }
        }
    }
}

/// VisionKit's document camera (edge detection, perspective correction, multiple pages).
private struct DocumentCameraView: UIViewControllerRepresentable {
    var onFinish: ([UIImage]) -> Void
    var onCancel: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish, onCancel: onCancel) }

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let controller = VNDocumentCameraViewController()
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let onFinish: ([UIImage]) -> Void
        let onCancel: () -> Void

        init(onFinish: @escaping ([UIImage]) -> Void, onCancel: @escaping () -> Void) {
            self.onFinish = onFinish
            self.onCancel = onCancel
        }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            // A worksheet's first pages hold its title and due date.
            let pages = (0..<min(scan.pageCount, 3)).map { scan.imageOfPage(at: $0) }
            onFinish(pages)
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            onCancel()
        }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            onCancel()
        }
    }
}
