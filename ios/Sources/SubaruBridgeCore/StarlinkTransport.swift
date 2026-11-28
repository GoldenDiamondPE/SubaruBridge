import Foundation
import CSubaruBridgeCore

public enum TransportFailure: Error, Equatable {
    case unavailable, closed, inputLimit, malformedBatch
}

public struct TransportRequest: Equatable {
    public let connection: UInt64
    public let bytes: Data
}

public struct TransportBatch: Equatable {
    public let wire: Data
    public let requests: [TransportRequest]

    /// Decode the same bounded, big-endian C ABI batch used on Android.
    public init(encoded: Data) throws {
        guard encoded.count >= 6, encoded.count <= 1_048_576 else { throw TransportFailure.malformedBatch }
        let bytes = [UInt8](encoded)
        var position = 0
        func number(_ size: Int) throws -> UInt64 {
            guard size <= bytes.count - position else { throw TransportFailure.malformedBatch }
            var value: UInt64 = 0
            for _ in 0..<size { value = value << 8 | UInt64(bytes[position]); position += 1 }
            return value
        }
        func block(_ length: Int) throws -> Data {
            guard length >= 0, length <= bytes.count - position else { throw TransportFailure.malformedBatch }
            defer { position += length }
            return Data(bytes[position..<(position + length)])
        }
        let length = try number(4)
        guard length <= UInt64(bytes.count - position - 2) else { throw TransportFailure.malformedBatch }
        wire = try block(Int(length))
        let count = try number(2)
        guard count <= 16 else { throw TransportFailure.malformedBatch }
        var requests: [TransportRequest] = []
        for _ in 0..<count {
            let connection = try number(8), size = try number(4)
            guard size <= 12_288 else { throw TransportFailure.malformedBatch }
            requests.append(TransportRequest(connection: connection, bytes: try block(Int(size))))
        }
        guard position == bytes.count else { throw TransportFailure.malformedBatch }
        self.requests = requests
    }
}

/// Owns the original Rust SLIP/IP/TCP engine. No sockets, accessory permissions or
/// hardware assertions live here; platform code supplies bytes and monotonic time.
public final class StarlinkTransportCore {
    private let lock = NSLock()
    private var handle: UInt64

    public init(seed: UInt64 = UInt64.random(in: UInt64.min...UInt64.max)) throws {
        handle = sb_core_create(seed)
        guard handle != 0 else { throw TransportFailure.unavailable }
    }
    deinit { close() }
    public func close() {
        lock.lock(); defer { lock.unlock() }
        if handle != 0 { sb_core_destroy(handle); handle = 0 }
    }
    private func copied(_ buffer: CoreBuffer) throws -> TransportBatch {
        defer { sb_core_buffer_free(buffer) }
        guard buffer.len >= 6, buffer.len <= 1_048_576, buffer.capacity >= buffer.len,
              let pointer = buffer.data else { throw TransportFailure.malformedBatch }
        return try TransportBatch(encoded: Data(bytes: pointer, count: buffer.len))
    }
    public func feed(_ data: Data, at milliseconds: UInt64) throws -> TransportBatch {
        lock.lock(); defer { lock.unlock() }
        guard handle != 0 else { throw TransportFailure.closed }
        guard data.count <= 65_536 else { throw TransportFailure.inputLimit }
        return try data.withUnsafeBytes { raw in
            try copied(sb_core_feed(handle, raw.bindMemory(to: UInt8.self).baseAddress, data.count, milliseconds))
        }
    }
    public func respond(to connection: UInt64, bytes: Data, close: Bool, at milliseconds: UInt64) throws -> TransportBatch {
        lock.lock(); defer { lock.unlock() }
        guard handle != 0 else { throw TransportFailure.closed }
        guard bytes.count <= 65_536 else { throw TransportFailure.inputLimit }
        return try bytes.withUnsafeBytes { raw in
            try copied(sb_core_respond(handle, connection, raw.bindMemory(to: UInt8.self).baseAddress, bytes.count, close ? 1 : 0, milliseconds))
        }
    }
    public func tick(at milliseconds: UInt64) throws -> TransportBatch {
        lock.lock(); defer { lock.unlock() }
        guard handle != 0 else { throw TransportFailure.closed }
        return try copied(sb_core_tick(handle, milliseconds))
    }
}
