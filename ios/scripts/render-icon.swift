import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
import Foundation

// Original SubaruBridge artwork, generated at build time. No vendor art or font files.
guard CommandLine.arguments.count == 2 else { fatalError("Usage: render-icon.swift output.png") }
let output = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: output.deletingLastPathComponent(), withIntermediateDirectories: true)
guard let context = CGContext(data: nil, width: 1024, height: 1024, bitsPerComponent: 8,
    bytesPerRow: 1024 * 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { fatalError("Cannot create icon bitmap") }
context.setFillColor(red: 20.0/255, green: 106.0/255, blue: 1, alpha: 1)
context.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024))
context.translateBy(x: 0, y: 1024); context.scaleBy(x: 1024.0/72, y: -1024.0/72); context.translateBy(x: -18, y: -18)
context.setLineWidth(5.5); context.setLineCap(.round); context.setLineJoin(.round); context.setStrokeColor(gray: 1, alpha: 1)
context.move(to: .init(x: 30, y: 55)); context.addCurve(to: .init(x: 78, y: 55), control1: .init(x: 41, y: 32), control2: .init(x: 67, y: 32))
context.move(to: .init(x: 29, y: 59)); context.addLine(to: .init(x: 79, y: 59))
context.move(to: .init(x: 34, y: 49)); context.addLine(to: .init(x: 34, y: 68))
context.move(to: .init(x: 74, y: 49)); context.addLine(to: .init(x: 74, y: 68)); context.strokePath()
context.move(to: .init(x: 49, y: 51)); context.addLine(to: .init(x: 59, y: 51)); context.addLine(to: .init(x: 66, y: 77)); context.addLine(to: .init(x: 42, y: 77)); context.closePath()
context.addRect(.init(x: 53, y: 53, width: 2, height: 6)); context.addRect(.init(x: 52.5, y: 63, width: 3, height: 6)); context.addRect(.init(x: 52, y: 73, width: 4, height: 4))
context.setFillColor(gray: 1, alpha: 1); context.drawPath(using: .eoFill)
guard let image = context.makeImage(), let data = image.dataProvider?.data, CFDataGetLength(data) > 3,
      let pixel = CFDataGetBytePtr(data), pixel[0] != 0 || pixel[1] != 0 || pixel[2] != 0,
      let destination = CGImageDestinationCreateWithURL(output as CFURL, UTType.png.identifier as CFString, 1, nil) else { fatalError("Icon rendering failed") }
CGImageDestinationAddImage(destination, image, nil)
guard CGImageDestinationFinalize(destination) else { fatalError("Icon export failed") }
