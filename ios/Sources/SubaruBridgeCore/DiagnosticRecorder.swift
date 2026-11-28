import Foundation

public struct DiagnosticRecording: Codable, Identifiable, Equatable, Sendable {
    public var schema = 1
    public var product = "SubaruBridge"
    public var build = "0.57-ios"
    public let id: String
    public let startedAt: Int64
    public var endedAt: Int64?
    public var status = "recording"
    public var events = 0
    public var bytes = 0
}

/// Android-compatible metadata sessions. Platform code supplies lifecycle and samples.
public final class DiagnosticRecorder: @unchecked Sendable {
    public static let maximumBytes = 1024 * 1024, maximumSessions = 12
    private let lock = NSRecursiveLock()
    public let directory: URL
    private var active: DiagnosticRecording?
    private var beganUptime: TimeInterval = 0
    private let now: @Sendable () -> Date
    private let uptime: @Sendable () -> TimeInterval
    public init(directory: URL, now: @escaping @Sendable () -> Date = { Date() }, uptime: @escaping @Sendable () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }) throws {
        self.directory = directory; self.now = now; self.uptime = uptime
        try GarageRepository.protect(directory)
        for var run in try recordings() where run.status == "recording" {
            run.status = "interrupted"; run.endedAt = milliseconds(); try save(run)
        }
    }
    private func milliseconds() -> Int64 { Int64(now().timeIntervalSince1970 * 1000) }
    private func url(_ id: String, _ suffix: String) throws -> URL {
        guard UUID(uuidString: id) != nil, id == id.lowercased() else { throw OwnershipError.invalidDocument }
        let url = directory.appendingPathComponent(id + suffix)
        if FileManager.default.fileExists(atPath: url.path), try url.resourceValues(forKeys: [.isSymbolicLinkKey]).isSymbolicLink == true { throw OwnershipError.invalidDocument }
        return url
    }
    private func save(_ run: DiagnosticRecording) throws { try GarageRepository.atomicWrite(JSONEncoder().encode(run), to: url(run.id, ".json")) }
    public func recordings() throws -> [DiagnosticRecording] {
        lock.lock(); defer { lock.unlock() }
        let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.fileSizeKey])
        return files.filter { $0.pathExtension == "json" }.compactMap { file in
            guard let data = try? GarageRepository.read(file, maximum: 8192), let run = try? JSONDecoder().decode(DiagnosticRecording.self, from: data), run.schema == 1,
                  file.lastPathComponent == run.id + ".json", (try? url(run.id, ".json")) != nil else { return nil }; return run
        }.sorted { $0.startedAt > $1.startedAt }
    }
    public func current() -> DiagnosticRecording? { lock.lock(); defer { lock.unlock() }; return active }
    public func start() throws {
        lock.lock(); defer { lock.unlock() }
        guard active == nil else { throw OwnershipError.engine("A recording is already running") }
        let old = try recordings(); for run in old.dropFirst(Self.maximumSessions - 1) { try delete(run.id) }
        let run = DiagnosticRecording(id: UUID().uuidString.lowercased(), startedAt: milliseconds())
        try GarageRepository.atomicWrite(Data(), to: url(run.id, ".jsonl")); try save(run)
        active = run; beganUptime = uptime()
        try record("start", fields: ["scope": .string("App metadata only; no raw Bluetooth, GPS, messages or Garage records")])
    }
    public func record(_ kind: String, fields: [String: JSONValue] = [:]) throws {
        lock.lock(); defer { lock.unlock() }
        guard var run = active else { return }
        if uptime() - beganUptime >= 600 { try stop("time_limit"); return }
        var event = fields; event["schema"] = .integer(1); event["event"] = .string(kind)
        event["timeMs"] = .integer(milliseconds()); event["elapsedMs"] = .integer(Int64(max(0, uptime() - beganUptime) * 1000))
        var data = try OwnershipCore.encode(.object(event)); data.append(10)
        do {
            let handle = try FileHandle(forWritingTo: url(run.id, ".jsonl")); defer { try? handle.close() }
            let size = try handle.seekToEnd()
            guard data.count <= 8192, size <= UInt64(Self.maximumBytes - data.count) else { try stop("storage_limit"); return }
            try handle.write(contentsOf: data); try handle.synchronize()
            run.events += 1; run.bytes = Int(size) + data.count; try save(run); active = run
        } catch {
            run.status = "storage_error"; run.endedAt = milliseconds(); active = nil
            try? save(run)
            throw OwnershipError.storage
        }
    }
    public func mark(_ kind: String, note: String = "") throws {
        lock.lock(); defer { lock.unlock() }
        guard active != nil else { throw OwnershipError.engine("Start a recording first") }
        guard ["car_connected", "starlink_opened", "display_visible", "touch_received", "error_seen", "disconnected", "reconnected", "note"].contains(kind), note.count <= 240 else { throw OwnershipError.invalidDocument }
        let clean = String(note.unicodeScalars.map { CharacterSet.controlCharacters.contains($0) ? " " : String($0) }.joined())
        try record("observation", fields: ["kind": .string(kind), "note": .string(clean)])
    }
    public func stop(_ reason: String = "saved") throws {
        lock.lock(); defer { lock.unlock() }
        guard var run = active else { return }; run.status = reason; run.endedAt = milliseconds()
        try save(run); active = nil
    }
    public func delete(_ id: String) throws {
        lock.lock(); defer { lock.unlock() }
        guard active?.id != id else { throw OwnershipError.engine("Stop the recording first") }
        for suffix in [".json", ".jsonl"] { let file = try url(id, suffix); if FileManager.default.fileExists(atPath: file.path) { try FileManager.default.removeItem(at: file) } }
    }
    public func timeline(_ id: String) throws -> String {
        lock.lock(); defer { lock.unlock() }
        let bytes = try GarageRepository.read(url(id, ".jsonl"), maximum: Self.maximumBytes)
        guard let text = String(data: bytes, encoding: .utf8) else { throw OwnershipError.invalidDocument }
        return text.split(separator: "\n").suffix(60).joined(separator: "\n\n")
    }
    public func report(metadata: [String: JSONValue]) throws -> Data {
        lock.lock(); defer { lock.unlock() }
        var files = [("report.json", try OwnershipCore.encode(.object(metadata)))]
        for run in try recordings() { for suffix in [".json", ".jsonl"] { files.append(("recordings/" + run.id + suffix, try GarageRepository.read(url(run.id, suffix), maximum: Self.maximumBytes))) } }
        return try StoredZip.archive(files)
    }
}

/// Small bounded ZIP writer using stored entries; no runtime or proprietary dependency.
public enum StoredZip {
    public static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xffff_ffff
        for byte in data { crc ^= UInt32(byte); for _ in 0..<8 { crc = (crc >> 1) ^ (crc & 1 == 1 ? 0xedb8_8320 : 0) } }
        return ~crc
    }
    public static func archive(_ files: [(String, Data)]) throws -> Data {
        var output = Data(), central = Data(); var count = 0
        func word(_ value: UInt64, _ length: Int) -> Data { Data((0..<length).map { UInt8(truncatingIfNeeded: value >> ($0 * 8)) }) }
        for (name, bytes) in files {
            let path = Data(name.utf8)
            guard !name.hasPrefix("/"), !name.contains(".."), !name.contains("\\"), path.count < 256, bytes.count <= DiagnosticRecorder.maximumBytes, output.count + bytes.count < 16 * 1024 * 1024 else { throw OwnershipError.limit }
            let crc = UInt64(crc32(bytes)), size = UInt64(bytes.count), offset = UInt64(output.count)
            for (value, length) in [(UInt64(0x04034b50),4),(20,2),(0x800,2),(0,2),(0,2),(0x21,2),(crc,4),(size,4),(size,4),(UInt64(path.count),2),(0,2)] { output.append(word(value, length)) }
            output.append(path); output.append(bytes)
            for (value, length) in [(UInt64(0x02014b50),4),(20,2),(20,2),(0x800,2),(0,2),(0,2),(0x21,2),(crc,4),(size,4),(size,4),(UInt64(path.count),2),(0,2),(0,2),(0,2),(0,2),(0,4),(offset,4)] { central.append(word(value, length)) }
            central.append(path); count += 1
        }
        guard count <= 32 else { throw OwnershipError.limit }
        let start = output.count; output.append(central)
        for (value, length) in [(UInt64(0x06054b50),4),(0,2),(0,2),(UInt64(count),2),(UInt64(count),2),(UInt64(central.count),4),(UInt64(start),4),(0,2)] { output.append(word(value, length)) }
        return output
    }
}
