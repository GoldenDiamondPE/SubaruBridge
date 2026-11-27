import Foundation

/// Bounded SLIP framing only. Does not provide Bluetooth, IP, TCP or a car session.
public struct SlipDecoder {
    private var buffer: [UInt8] = []
    private var escaped = false
    private var dropping = false
    public let maximumFrame: Int
    public init(maximumFrame: Int = 8192) { precondition(maximumFrame > 0); self.maximumFrame = maximumFrame }
    public mutating func feed(_ bytes: [UInt8]) -> [[UInt8]] {
        var frames: [[UInt8]] = []
        for byte in bytes {
            if byte == 0xc0 {
                if !dropping && !escaped && !buffer.isEmpty { frames.append(buffer) }
                buffer.removeAll(keepingCapacity: true); escaped = false; dropping = false
            } else if !dropping {
                if escaped {
                    escaped = false
                    if byte == 0xdc { buffer.append(0xc0) }
                    else if byte == 0xdd { buffer.append(0xdb) }
                    else { dropping = true; buffer.removeAll(keepingCapacity: true) }
                } else if byte == 0xdb { escaped = true }
                else { buffer.append(byte) }
                if buffer.count > maximumFrame { dropping = true; buffer.removeAll(keepingCapacity: true) }
            }
        }
        return frames
    }
    public static func encode(_ bytes: [UInt8]) -> [UInt8] {
        var encoded: [UInt8] = [0xc0]
        for byte in bytes {
            if byte == 0xc0 { encoded += [0xdb, 0xdc] }
            else if byte == 0xdb { encoded += [0xdb, 0xdd] }
            else { encoded.append(byte) }
        }
        encoded.append(0xc0); return encoded
    }
}
