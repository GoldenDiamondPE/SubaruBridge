import XCTest
@testable import SubaruBridgeCore

private final class TestClock: @unchecked Sendable {
    private let lock = NSLock(); private var seconds: Double = 1000
    func read() -> Double { lock.lock(); defer { lock.unlock() }; return seconds }
    func advance(_ value: Double) { lock.lock(); defer { lock.unlock() }; seconds += value }
}
final class RecorderTests: XCTestCase {
    func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("SubaruBridge-recorder-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try FileManager.default.removeItem(at: url) }; return url
    }
    func testTimeLimitUsesMonotonicElapsedTimeAndExportsNoBootTime() throws {
        let clock = TestClock(), location = try directory()
        let recorder = try DiagnosticRecorder(directory: location, uptime: { clock.read() })
        try recorder.start(); let id = try XCTUnwrap(recorder.current()?.id)
        clock.advance(599); try recorder.mark("display_visible")
        XCTAssertNotNil(recorder.current()); clock.advance(1); try recorder.record("phone")
        XCTAssertNil(recorder.current()); XCTAssertEqual(try recorder.recordings().first?.status, "time_limit")
        let lines = try recorder.timeline(id); XCTAssertTrue(lines.contains("elapsedMs")); XCTAssertFalse(lines.contains("uptimeMs"))
    }
    func testNotesAreBoundedAndInterruptedRunsRecover() throws {
        let location = try directory(), recorder = try DiagnosticRecorder(directory: location)
        try recorder.start(); let id = try XCTUnwrap(recorder.current()?.id)
        try recorder.mark("note", note: "Observed\ncolor bars")
        XCTAssertThrowsError(try recorder.mark("note", note: String(repeating: "x", count: 241)))
        XCTAssertThrowsError(try recorder.mark("unverified-can-command"))
        XCTAssertThrowsError(try recorder.delete(id))
        let recovered = try DiagnosticRecorder(directory: location)
        XCTAssertNil(recovered.current()); XCTAssertEqual(try recovered.recordings().first?.status, "interrupted")
        XCTAssertTrue(try recovered.timeline(id).contains("Observed color bars"))
        XCTAssertThrowsError(try recovered.timeline("../../private"))
    }
    func testRecorderEnforcesActualFileSizeAndStopsAfterStorageFailure() throws {
        let location = try directory(), recorder = try DiagnosticRecorder(directory: location)
        try recorder.start(); let id = try XCTUnwrap(recorder.current()?.id)
        let file = location.appendingPathComponent(id + ".jsonl")
        try Data(repeating: 32, count: DiagnosticRecorder.maximumBytes).write(to: file)
        try recorder.record("sample"); XCTAssertNil(recorder.current())
        XCTAssertEqual(try recorder.recordings().first?.status, "storage_limit")
        XCTAssertEqual(try Data(contentsOf: file).count, DiagnosticRecorder.maximumBytes)
        try recorder.start(); let broken = try XCTUnwrap(recorder.current()?.id)
        try FileManager.default.removeItem(at: location.appendingPathComponent(broken + ".jsonl"))
        XCTAssertThrowsError(try recorder.record("sample")); XCTAssertNil(recorder.current())
    }
    func testRetentionAndZIPCanBeReadByStandardTools() throws {
        let location = try directory(), clock = TestClock()
        let recorder = try DiagnosticRecorder(directory: location, now: { Date(timeIntervalSince1970: clock.read()) })
        for _ in 0..<14 { try recorder.start(); try recorder.stop(); clock.advance(1) }
        XCTAssertEqual(try recorder.recordings().count, 12)
        XCTAssertEqual(StoredZip.crc32(Data("123456789".utf8)), 0xcbf43926)
        let bytes = try recorder.report(metadata: ["scope": .string("synthetic QA")]); XCTAssertTrue(bytes.starts(with: [0x50,0x4b,3,4]))
        #if os(macOS)
        let file = location.appendingPathComponent("test.zip"); try bytes.write(to: file)
        let process = Process(); process.executableURL = URL(fileURLWithPath: "/usr/bin/unzip"); process.arguments = ["-t", file.path]
        let output = Pipe(); process.standardOutput = output; process.standardError = output
        try process.run(); let result = output.fileHandleForReading.readDataToEndOfFile(); process.waitUntilExit()
        XCTAssertEqual(process.terminationStatus, 0, String(decoding: result, as: UTF8.self))
        #endif
        XCTAssertThrowsError(try StoredZip.archive([("../escape", Data())]))
    }
}
