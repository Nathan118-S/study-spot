import UIKit
import Vision

/// On-device text recognition for "Scan worksheet" (Vision, accurate level).
/// Nothing leaves the device. Lines come back top to bottom with their
/// heights, which WorksheetParser uses to spot the title.
enum WorksheetOCR {
    enum OCRError: LocalizedError {
        case unreadableImage
        var errorDescription: String? { "That image couldn't be read." }
    }

    /// Recognises the text on each page (in order) and returns all lines.
    static func recognizeLines(in images: [UIImage]) async throws -> [WorksheetParser.Line] {
        var all: [WorksheetParser.Line] = []
        for image in images {
            all += try await recognizeLines(in: image)
        }
        return all
    }

    static func recognizeLines(in image: UIImage) async throws -> [WorksheetParser.Line] {
        guard let cgImage = image.cgImage else { throw OCRError.unreadableImage }
        let orientation = CGImagePropertyOrientation(image.imageOrientation)
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let request = VNRecognizeTextRequest()
                request.recognitionLevel = .accurate
                request.usesLanguageCorrection = true
                let handler = VNImageRequestHandler(cgImage: cgImage, orientation: orientation, options: [:])
                do {
                    try handler.perform([request])
                    let observations = (request.results ?? [])
                        // Vision's boundingBox is normalised with the origin at the bottom left.
                        .sorted { $0.boundingBox.midY > $1.boundingBox.midY }
                    let lines = observations.compactMap { obs -> WorksheetParser.Line? in
                        guard let text = obs.topCandidates(1).first?.string else { return nil }
                        return WorksheetParser.Line(text: text, height: Double(obs.boundingBox.height) * 1000)
                    }
                    continuation.resume(returning: lines)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}

private extension CGImagePropertyOrientation {
    init(_ o: UIImage.Orientation) {
        switch o {
        case .up: self = .up
        case .down: self = .down
        case .left: self = .left
        case .right: self = .right
        case .upMirrored: self = .upMirrored
        case .downMirrored: self = .downMirrored
        case .leftMirrored: self = .leftMirrored
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}
