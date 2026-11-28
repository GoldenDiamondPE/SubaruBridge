import Foundation
import CSubaruBridgeCore

/// JSON numbers remain integers whenever possible, including money, distance and revisions.
public enum JSONValue: Codable, Hashable, Sendable {
    case null, bool(Bool), string(String), integer(Int64), number(Double)
    case array([JSONValue]), object([String: JSONValue])
    public init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer()
        if value.decodeNil() { self = .null }
        else if let item = try? value.decode(Bool.self) { self = .bool(item) }
        else if let item = try? value.decode(Int64.self) { self = .integer(item) }
        else if let item = try? value.decode(String.self) { self = .string(item) }
        else if let item = try? value.decode([String: JSONValue].self) { self = .object(item) }
        else if let item = try? value.decode([JSONValue].self) { self = .array(item) }
        else { let item = try value.decode(Double.self); guard item.isFinite else { throw OwnershipError.invalidDocument }; self = .number(item) }
    }
    public func encode(to encoder: Encoder) throws {
        var value = encoder.singleValueContainer()
        switch self {
        case .null: try value.encodeNil()
        case let .bool(item): try value.encode(item)
        case let .string(item): try value.encode(item)
        case let .integer(item): try value.encode(item)
        case let .number(item): guard item.isFinite else { throw OwnershipError.invalidDocument }; try value.encode(item)
        case let .array(item): try value.encode(item)
        case let .object(item): try value.encode(item)
        }
    }
    public subscript(_ key: String) -> JSONValue { if case let .object(value) = self { return value[key] ?? .null }; return .null }
    public var text: String? { if case let .string(value) = self { return value }; return nil }
    public var int: Int64? { if case let .integer(value) = self { return value }; return nil }
    public var flag: Bool { if case let .bool(value) = self { return value }; return false }
    public var list: [JSONValue] { if case let .array(value) = self { return value }; return [] }
    public var fields: [String: JSONValue] { if case let .object(value) = self { return value }; return [:] }
    public func changing(_ values: [String: JSONValue]) -> JSONValue { .object(fields.merging(values) { _, new in new }) }
    public static func text(_ value: String) -> JSONValue { .string(value) }
    public static func value(_ value: Int64) -> JSONValue { .integer(value) }
}

public enum OwnershipError: LocalizedError, Equatable {
    case invalidDocument, limit, engine(String), conflict, storage, unavailable
    public var errorDescription: String? {
        switch self {
        case .invalidDocument: return "The file is not supported, valid vehicle JSON. Your existing records were preserved."
        case .limit: return "The supported storage limit was reached. Export a backup before adding more records."
        case let .engine(message): return message
        case .conflict: return "Your records changed while this form was open. Reopen it to use the latest values."
        case .storage: return "Vehicle records could not be saved. The previous file was preserved."
        case .unavailable: return "The shared app engine is unavailable. Reinstall this build."
        }
    }
}

public enum OwnershipCore {
    public static let maximumInput = 4 * 1024 * 1024
    public static let empty = JSONValue.object([
        "schemaVersion": .integer(1), "revision": .integer(0), "vehicles": .array([]),
        "readings": .array([]), "services": .array([]), "fuel": .array([]),
        "expenses": .array([]), "trips": .array([]), "overrides": .array([])
    ])
    public static func encode(_ value: JSONValue) throws -> Data {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(value)
    }
    public static func decode(_ bytes: Data) throws -> JSONValue {
        guard bytes.count <= maximumInput else { throw OwnershipError.limit }
        let valid = bytes.withUnsafeBytes { sb_ownership_json_valid($0.bindMemory(to: UInt8.self).baseAddress, bytes.count) }
        guard valid == 1 else { throw OwnershipError.invalidDocument }
        let data = bytes.starts(with: [0xef, 0xbb, 0xbf]) ? Data(bytes.dropFirst(3)) : bytes
        return try JSONDecoder().decode(JSONValue.self, from: data)
    }
    public static func execute(_ request: JSONValue) throws -> JSONValue {
        let bytes = try encode(request)
        guard bytes.count <= maximumInput else { throw OwnershipError.limit }
        let result = bytes.withUnsafeBytes { sb_ownership_execute($0.bindMemory(to: UInt8.self).baseAddress, bytes.count) }
        defer { sb_core_buffer_free(result) }
        guard let pointer = result.data, result.len > 0, result.len <= 16 * 1024 * 1024, result.capacity >= result.len else { throw OwnershipError.unavailable }
        // Engine output is trusted typed serde JSON, independently bounded before decoding.
        let response = try JSONDecoder().decode(JSONValue.self, from: Data(bytes: pointer, count: result.len))
        guard response["ok"].flag else { throw OwnershipError.engine(response["error"].text ?? "Vehicle validation failed") }
        return response
    }
    public static func date(_ date: Date, zone: TimeZone = .current) -> String {
        let formatter = DateFormatter(); formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX"); formatter.timeZone = zone; formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
    public static func request(state: JSONValue, command: JSONValue, date: Date = Date(), zone: TimeZone = .current) -> JSONValue {
        .object(["state": state, "today": .string(Self.date(date, zone: zone)), "nowMs": .integer(Int64(date.timeIntervalSince1970 * 1000)), "command": command])
    }
}

/// One Android-compatible file and one process-wide transaction lock. No cloud or sockets.
public final class GarageRepository: @unchecked Sendable {
    public static let maximumState = 1536 * 1024
    private static let transaction = NSRecursiveLock()
    public let file: URL
    public init(file: URL) { self.file = file }
    public func view(at date: Date = Date(), zone: TimeZone = .current) throws -> JSONValue {
        try execute(.object(["type": .string("view")]), at: date, zone: zone)
    }
    private func load() throws -> JSONValue {
        guard FileManager.default.fileExists(atPath: file.path) else { return OwnershipCore.empty }
        return try OwnershipCore.decode(Self.read(file, maximum: Self.maximumState))
    }
    public static func read(_ url: URL, maximum: Int) throws -> Data {
        let values = try url.resourceValues(forKeys: [.isSymbolicLinkKey, .fileSizeKey])
        guard values.isSymbolicLink != true else { throw OwnershipError.invalidDocument }
        guard (values.fileSize ?? 0) <= maximum else { throw OwnershipError.limit }
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        let data = try handle.read(upToCount: maximum + 1) ?? Data()
        guard data.count <= maximum else { throw OwnershipError.limit }; return data
    }
    public static func protect(_ directory: URL) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        var mutable = directory; try mutable.setResourceValues(values)
        #if os(iOS)
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: directory.path)
        #endif
    }
    public static func atomicWrite(_ data: Data, to url: URL) throws {
        try protect(url.deletingLastPathComponent())
        if FileManager.default.fileExists(atPath: url.path), try url.resourceValues(forKeys: [.isSymbolicLinkKey]).isSymbolicLink == true { throw OwnershipError.invalidDocument }
        #if os(iOS)
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        #else
        try data.write(to: url, options: .atomic)
        #endif
    }
    public func execute(_ command: JSONValue, expectedRevision: Int64? = nil, at date: Date = Date(), zone: TimeZone = .current) throws -> JSONValue {
        Self.transaction.lock(); defer { Self.transaction.unlock() }
        let state = try load()
        if let expectedRevision, expectedRevision != state["revision"].int { throw OwnershipError.conflict }
        let result = try OwnershipCore.execute(OwnershipCore.request(state: state, command: command, date: date, zone: zone))
        if result["changed"].flag {
            let encoded = try OwnershipCore.encode(result["state"])
            guard encoded.count <= Self.maximumState else { throw OwnershipError.limit }
            do { try Self.atomicWrite(encoded, to: file) } catch { throw OwnershipError.storage }
        }
        return result
    }
    public func backup(at date: Date = Date()) throws -> Data {
        Self.transaction.lock(); defer { Self.transaction.unlock() }
        let snapshot = try view(at: date)
        return try OwnershipCore.encode(.object(["format": .string("org.subarubridge.garage"), "schemaVersion": .integer(1), "exportedAt": .string(ISO8601DateFormatter().string(from: date)), "garage": snapshot["state"]]))
    }
    public func restore(_ data: Data, at date: Date = Date()) throws -> JSONValue {
        guard data.count <= Self.maximumState + 4096 else { throw OwnershipError.limit }
        let envelope = try OwnershipCore.decode(data)
        guard Set(envelope.fields.keys) == Set(["format", "schemaVersion", "exportedAt", "garage"]), envelope["format"].text == "org.subarubridge.garage", envelope["schemaVersion"].int == 1, envelope["exportedAt"].text != nil else { throw OwnershipError.invalidDocument }
        return try execute(.object(["type": .string("mergeBackup"), "backup": envelope["garage"]]), at: date)
    }
}
