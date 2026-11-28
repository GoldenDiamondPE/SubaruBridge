import AppKit
import Foundation

// Original SubaruBridge artwork, generated at build time. No vendor art or font files.
guard CommandLine.arguments.count == 2 else { fatalError("Usage: render-icon.swift output.png") }
let output = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: output.deletingLastPathComponent(), withIntermediateDirectories: true)
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024, bitsPerSample: 8,
    samplesPerPixel: 3, hasAlpha: false, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
NSColor(srgbRed: 20.0/255, green: 106.0/255, blue: 1, alpha: 1).setFill()
NSBezierPath(rect: NSRect(x: 0, y: 0, width: 1024, height: 1024)).fill()
let transform = NSAffineTransform(); transform.translateX(by: 0, yBy: 1024); transform.scaleX(by: 1024.0/72, yBy: -1024.0/72); transform.translateX(by: -18, yBy: -18); transform.concat()
let bridge = NSBezierPath(); bridge.lineWidth = 5.5; bridge.lineCapStyle = .round; bridge.lineJoinStyle = .round
bridge.move(to: .init(x: 30, y: 55)); bridge.curve(to: .init(x: 78, y: 55), controlPoint1: .init(x: 41, y: 32), controlPoint2: .init(x: 67, y: 32))
bridge.move(to: .init(x: 29, y: 59)); bridge.line(to: .init(x: 79, y: 59))
bridge.move(to: .init(x: 34, y: 49)); bridge.line(to: .init(x: 34, y: 68))
bridge.move(to: .init(x: 74, y: 49)); bridge.line(to: .init(x: 74, y: 68))
NSColor.white.setStroke(); bridge.stroke()
let road = NSBezierPath(); road.windingRule = .evenOdd
road.move(to: .init(x: 49, y: 51)); road.line(to: .init(x: 59, y: 51)); road.line(to: .init(x: 66, y: 77)); road.line(to: .init(x: 42, y: 77)); road.close()
road.appendRect(.init(x: 53, y: 53, width: 2, height: 6)); road.appendRect(.init(x: 52.5, y: 63, width: 3, height: 6)); road.appendRect(.init(x: 52, y: 73, width: 4, height: 4))
NSColor.white.setFill(); road.fill(); NSGraphicsContext.restoreGraphicsState()
try bitmap.representation(using: .png, properties: [:])!.write(to: output, options: .atomic)
