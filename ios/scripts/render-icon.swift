import AppKit
import Foundation

// Original SubaruBridge artwork, generated at build time. No vendor art or font files.
guard CommandLine.arguments.count == 2 else { fatalError("Usage: render-icon.swift output.png") }
let output = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: output.deletingLastPathComponent(), withIntermediateDirectories: true)
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024, bitsPerSample: 8,
    samplesPerPixel: 3, hasAlpha: false, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
NSColor(srgbRed: 0.039, green: 0.518, blue: 1, alpha: 1).setFill()
NSBezierPath(rect: NSRect(x: 0, y: 0, width: 1024, height: 1024)).fill()
let bridge = NSBezierPath(); bridge.lineWidth = 48; bridge.lineCapStyle = .round; bridge.lineJoinStyle = .round
bridge.move(to: NSPoint(x: 180, y: 380)); bridge.line(to: NSPoint(x: 844, y: 380))
bridge.move(to: NSPoint(x: 280, y: 300)); bridge.line(to: NSPoint(x: 280, y: 650))
bridge.move(to: NSPoint(x: 744, y: 300)); bridge.line(to: NSPoint(x: 744, y: 650))
bridge.move(to: NSPoint(x: 180, y: 510)); bridge.curve(to: NSPoint(x: 512, y: 590), controlPoint1: NSPoint(x: 280, y: 760), controlPoint2: NSPoint(x: 400, y: 590))
bridge.curve(to: NSPoint(x: 844, y: 510), controlPoint1: NSPoint(x: 624, y: 590), controlPoint2: NSPoint(x: 744, y: 760))
NSColor.white.setStroke(); bridge.stroke(); NSGraphicsContext.restoreGraphicsState()
try bitmap.representation(using: .png, properties: [:])!.write(to: output, options: .atomic)
