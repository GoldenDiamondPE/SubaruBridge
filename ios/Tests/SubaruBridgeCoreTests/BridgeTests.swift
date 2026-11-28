import XCTest
@testable import SubaruBridgeCore

final class BridgeTests: XCTestCase {
    func router(save: @escaping (Data) throws -> Void = { _ in }) throws -> StarlinkHTTPServer {
        try StarlinkHTTPServer(phone: { StarlinkPhoneSnapshot(batteryPercent: 75, wallTimeMs: 1795777200000, uptimeMs: 5000) }, save: save)
    }
    func get(_ path: String, _ router: StarlinkHTTPServer) -> StarlinkHTTPResponse { router.handle(Data("GET \(path) HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".utf8)) }
    func post(_ path: String, _ body: String, _ router: StarlinkHTTPServer) -> StarlinkHTTPResponse {
        router.handle(Data("POST \(path) HTTP/1.1\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: \(body.utf8.count)\r\n\r\n\(body)".utf8))
    }
    func testOriginalDashboardRendersAndSettingsPersistThroughForms() throws {
        let server = try router()
        let home = get("/subarubridge-diag/dashboard", server)
        XCTAssertEqual(home.status, 200); XCTAssertTrue(String(decoding: home.body, as: UTF8.self).contains("Music"))
        XCTAssertEqual(post("/subarubridge-diag/settings", "theme=daylight&layout=list", server).status, 200)
        XCTAssertEqual(server.theme, "daylight"); XCTAssertEqual(server.layout, "list")
        XCTAssertEqual(post("/subarubridge-diag/settings", "theme=evil&layout=list", server).status, 400)
        XCTAssertEqual(server.theme, "daylight")
        XCTAssertEqual(get("/version", server).status, 200)
    }
    func testJavaScriptFreeTouchRetryIsIdempotent() throws {
        let server = try router(); XCTAssertEqual(get("/subarubridge-diag/basic?run=qa&screen=2", server).status, 200)
        let form = "run=qa&seq=2&screen=2&action=touch&ok=1"
        XCTAssertEqual(post("/subarubridge-diag/basic", form, server).status, 200)
        XCTAssertEqual(post("/subarubridge-diag/basic", form, server).status, 200)
        let state = try OwnershipCore.decode(get("/subarubridge-diag/status?run=qa", server).body)
        XCTAssertEqual(state["touches"].int, 1)
        XCTAssertEqual(post("/subarubridge-diag/basic", "run=qa&seq=2&screen=3&action=data&ok=1", server).status, 409)
    }
    func testFailedResultSaveCanRetryWithoutLosingSequence() throws {
        var shouldFail = true, saves = 0
        let server = try router { _ in if shouldFail { throw OwnershipError.storage }; saves += 1 }
        _ = get("/subarubridge-diag/basic?run=qa&screen=4", server)
        let form = "run=qa&seq=2&screen=4&action=summary&ok=1"
        XCTAssertEqual(post("/subarubridge-diag/basic", form, server).status, 507)
        shouldFail = false
        XCTAssertEqual(post("/subarubridge-diag/basic", form, server).status, 200)
        XCTAssertEqual(post("/subarubridge-diag/basic", form, server).status, 200); XCTAssertEqual(saves, 1)
    }
    func testRequestBoundsAndPrivateMetadata() throws {
        let server = try router(); var routes: [String] = []
        server.observed = { _, _, route, _, _ in routes.append(route) }
        for path in ["http://example.invalid/", "//example.invalid/", "/%2e%2e/private", "/x#secret", "/x?private=one&private=two"] { XCTAssertEqual(get(path, server).status, 400) }
        XCTAssertEqual(server.handle(Data("POST / HTTP/1.1\r\nContent-Length: 0\r\nContent-Length: 0\r\n\r\n".utf8)).status, 400)
        XCTAssertEqual(server.handle(Data("GET / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n".utf8)).status, 400)
        XCTAssertEqual(get("/private-personal-path", server).status, 404)
        XCTAssertTrue(routes.allSatisfy { ["other", "dashboard", "bootstrap"].contains($0) })
    }
    func testProtocolMatchingNeverInventsAProtocol() {
        XCTAssertEqual(AccessoryPolicy.eligibility(declared: [], advertised: ["com.example.car"]), .appProtocolNotConfigured)
        XCTAssertEqual(AccessoryPolicy.eligibility(declared: ["com.example.car"], advertised: []), .waitingForAuthentication)
        XCTAssertEqual(AccessoryPolicy.eligibility(declared: ["com.example.car"], advertised: ["com.other.car"]), .unsupportedProtocol)
        XCTAssertEqual(AccessoryPolicy.eligibility(declared: ["com.example.car"], advertised: ["com.example.car"]), .ready(protocolName: "com.example.car"))
        for value in ["", "a", "com..car", "com.car\n", "../../secret"] { XCTAssertFalse(AccessoryPolicy.validProtocol(value)) }
    }
    func testPartialWritesPreserveTheSuffixAndRejectOverflow() throws {
        var queue = AccessoryWriteQueue(maximumBytes: 8)
        try queue.append(Data([1,2,3,4])); try queue.consume(2); try queue.append(Data([5,6]))
        XCTAssertEqual(queue.next(), Data([3,4,5,6])); XCTAssertThrowsError(try queue.append(Data(repeating: 1, count: 5)))
        XCTAssertThrowsError(try queue.consume(5)); queue.clear(); XCTAssertEqual(queue.count, 0)
    }
    func testTransportLifetimeAndMalformedBatches() throws {
        for _ in 0..<64 {
            let core = try StarlinkTransportCore(seed: 1); XCTAssertTrue(try core.feed(Data([0xc0]), at: 1).requests.isEmpty)
            _ = try core.tick(at: 2); core.close(); core.close()
            XCTAssertThrowsError(try core.tick(at: 3)) { XCTAssertEqual($0 as? TransportFailure, .closed) }
        }
        for bytes in [Data(), Data([0,0,0,1,0,0]), Data([0,0,0,0,0,17]), Data([0,0,0,0,0,0,1])] { XCTAssertThrowsError(try TransportBatch(encoded: bytes)) }
        XCTAssertEqual(try TransportBatch(encoded: Data([0,0,0,0,0,0])).wire, Data())
    }
}
