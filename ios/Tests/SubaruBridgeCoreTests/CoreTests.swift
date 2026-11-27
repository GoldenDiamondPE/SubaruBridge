import XCTest
@testable import SubaruBridgeCore

final class CoreTests: XCTestCase {
    func testPhoneNumbersCannotInjectOtherURLSchemes() throws {
        XCTAssertEqual(try CompanionActions.dialURL("+12025550100").scheme, "tel")
        for invalid in ["", "tel:1234", "123;456", "123#456", "https://example.invalid", "123\n456", "123\n"] {
            XCTAssertFalse(CompanionActions.validPhoneNumber(invalid))
            XCTAssertThrowsError(try CompanionActions.dialURL(invalid))
        }
    }
    func testMapsDestinationIsOneEncodedQueryValue() throws {
        let destination = "Main St & Second?api=evil"
        let url = try CompanionActions.mapsURL(destination: destination)
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.host, "www.google.com")
        XCTAssertEqual(components.queryItems?.filter { $0.name == "api" }.map(\.value), ["1"])
        XCTAssertEqual(components.queryItems?.first { $0.name == "destination" }?.value, destination)
        for invalid in ["", "   ", "x\ny", String(repeating: "x", count: 161)] {
            XCTAssertThrowsError(try CompanionActions.mapsURL(destination: invalid))
        }
    }
    func testChunkedSlipAndEscapes() {
        let packet: [UInt8] = [0x45, 0xc0, 0xdb, 0x01]
        let encoded = SlipDecoder.encode(packet)
        for split in 0...encoded.count {
            var decoder = SlipDecoder()
            XCTAssertEqual(decoder.feed(Array(encoded.prefix(split))) + decoder.feed(Array(encoded.dropFirst(split))), [packet])
        }
    }
    func testMalformedEscapeAndOversizeRecoverAtDelimiter() {
        var decoder = SlipDecoder(maximumFrame: 3)
        XCTAssertEqual(decoder.feed([0xc0, 1, 0xdb, 0, 0xc0, 1, 2, 3, 4, 0xc0, 9, 0xc0]), [[9]])
    }
    func testIncompleteFrameIsNotDelivered() {
        var decoder = SlipDecoder()
        XCTAssertTrue(decoder.feed([0xc0, 1, 2, 0xdb]).isEmpty)
        XCTAssertTrue(decoder.feed([0xc0]).isEmpty)
    }
    func testRetryDoesNotDuplicateTouch() throws {
        var session = DiagnosticSession()
        let touch = Observation(sequence: 1, stage: .touch, action: .touch)
        XCTAssertTrue(try session.accept(touch)); XCTAssertFalse(try session.accept(touch))
        XCTAssertEqual(session.touches, 1)
        XCTAssertThrowsError(try session.accept(.init(sequence: 1, stage: .display, action: .page))) { XCTAssertEqual($0 as? SessionFailure, .conflictingRetry) }
    }
    func testWrongStageAndSequenceDoNotCommit() {
        var session = DiagnosticSession()
        XCTAssertThrowsError(try session.accept(.init(sequence: 1, stage: .display, action: .touch)))
        XCTAssertThrowsError(try session.accept(.init(sequence: 0, stage: .touch, action: .touch)))
        XCTAssertTrue(session.observations.isEmpty)
    }
    func testCompletionRequiresEveryCheckAndSave() throws {
        var session = DiagnosticSession()
        for stage in CheckStage.allCases { try session.accept(.init(sequence: session.nextSequence, stage: stage, action: .page)) }
        for _ in 0..<3 { try session.accept(.init(sequence: session.nextSequence, stage: .touch, action: .touch)) }
        try session.accept(.init(sequence: session.nextSequence, stage: .phone, action: .data))
        XCTAssertFalse(session.complete)
        try session.accept(.init(sequence: session.nextSequence, stage: .results, action: .summary))
        XCTAssertTrue(session.complete)
        let restored = try JSONDecoder().decode(DiagnosticSession.self, from: JSONEncoder().encode(session))
        XCTAssertEqual(restored.observations, session.observations)
    }
    func testSessionLimit() throws {
        var session = DiagnosticSession()
        for sequence in 1...128 { try session.accept(.init(sequence: sequence, stage: .display, action: .page)) }
        XCTAssertThrowsError(try session.accept(.init(sequence: 129, stage: .display, action: .page))) { XCTAssertEqual($0 as? SessionFailure, .limitReached) }
    }
    func testSharedDashboardConfiguration() throws {
        let ui = try DashboardConfiguration.bundled()
        XCTAssertEqual(Set(ui.tiles.map(\.screen)), Set(1...4))
        XCTAssertNotNil(ui.themes[ui.defaultTheme])
        XCTAssertEqual(ui.brand, "SubaruBridge")
        XCTAssertEqual(Set(ui.apps.map(\.id)), Set(["music", "navigation", "calls", "messages", "notifications"]))
    }
}
