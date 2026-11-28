import Foundation

public enum AccessoryEligibility: Equatable {
    case ready(protocolName: String)
    case appProtocolNotConfigured
    case waitingForAuthentication
    case unsupportedProtocol
}

/// Protocol names are capabilities, not an accessory identity or authorization
/// bypass. Only an advertised AND explicitly declared protocol can be selected.
public enum AccessoryPolicy {
    public static func validProtocol(_ value: String) -> Bool {
        guard (3...160).contains(value.utf8.count) else { return false }
        let parts = value.split(separator: ".", omittingEmptySubsequences: false)
        return parts.count >= 2 && parts.allSatisfy { part in
            !part.isEmpty && part.utf8.allSatisfy { byte in
                (65...90).contains(byte) || (97...122).contains(byte) || (48...57).contains(byte) || byte == 45 || byte == 95
            }
        }
    }
    public static func eligibility(declared: [String], advertised: [String]) -> AccessoryEligibility {
        let supported = declared.filter(validProtocol)
        guard !supported.isEmpty else { return .appProtocolNotConfigured }
        guard !advertised.isEmpty else { return .waitingForAuthentication }
        if let protocolName = supported.first(where: { advertised.contains($0) }) { return .ready(protocolName: protocolName) }
        return .unsupportedProtocol
    }
}

/// Partial writes and backpressure are normal for accessory streams. Never lose
/// the unwritten suffix, grow the queue without a limit, or retain it on reconnect.
public struct AccessoryWriteQueue {
    private var pending = Data()
    private var offset = 0
    public let maximumBytes: Int
    public init(maximumBytes: Int = 262_144) { precondition(maximumBytes > 0); self.maximumBytes = maximumBytes }
    public var count: Int { pending.count - offset }
    public mutating func append(_ bytes: Data) throws {
        guard bytes.count <= maximumBytes - count else { throw TransportFailure.inputLimit }
        if offset > 0 { pending = Data(pending.dropFirst(offset)); offset = 0 }
        pending.append(bytes)
    }
    public func next(maximum: Int = 8192) -> Data { Data(pending.dropFirst(offset).prefix(max(0, maximum))) }
    public mutating func consume(_ count: Int) throws {
        guard count >= 0, count <= self.count else { throw TransportFailure.malformedBatch }
        offset += count
        if offset == pending.count { clear() }
    }
    public mutating func clear() { pending.removeAll(keepingCapacity: false); offset = 0 }
}
