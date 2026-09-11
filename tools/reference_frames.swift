import Foundation
import AVFoundation
import AppKit

// Read-only analysis of user-supplied video. Stores requested and actual timestamps.
let args = CommandLine.arguments
guard args.count >= 4 else { fatalError("video output_directory interval_seconds [start] [end]") }
let asset = AVURLAsset(url: URL(fileURLWithPath: args[1]))
let output = URL(fileURLWithPath: args[2], isDirectory: true)
try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
let interval = Double(args[3])!
let duration = CMTimeGetSeconds(asset.duration)
let start = args.count > 4 ? Double(args[4])! : 0
let end = min(duration, args.count > 5 ? Double(args[5])! : duration)
let track = asset.tracks(withMediaType: .video).first!
let generator = AVAssetImageGenerator(asset: asset)
generator.appliesPreferredTrackTransform = true
generator.requestedTimeToleranceBefore = .zero
generator.requestedTimeToleranceAfter = .zero
var frames: [[String: Any]] = []
for time in stride(from: start, to: end, by: interval) {
    var actual = CMTime.zero
    let cg = try generator.copyCGImage(at: CMTime(seconds: time, preferredTimescale: 60000), actualTime: &actual)
    let rep = NSBitmapImageRep(cgImage: cg)
    let filename = String(format: "%07.3f.png", time)
    try rep.representation(using: .png, properties: [:])!.write(to: output.appendingPathComponent(filename))
    frames.append(["requested_seconds": time, "actual_seconds": CMTimeGetSeconds(actual), "file": filename])
}
let metadata: [String: Any] = ["source": args[1], "duration_seconds": duration,
    "width": track.naturalSize.width, "height": track.naturalSize.height,
    "nominal_fps": track.nominalFrameRate, "frames": frames]
try JSONSerialization.data(withJSONObject: metadata, options: [.prettyPrinted, .sortedKeys])
    .write(to: output.appendingPathComponent("frames.json"))
print("\(output.path): \(frames.count) full-resolution frames, duration \(duration), fps \(track.nominalFrameRate)")
