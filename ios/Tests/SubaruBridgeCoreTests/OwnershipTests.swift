import XCTest
@testable import SubaruBridgeCore

final class OwnershipTests: XCTestCase {
    let mile = OwnershipNumbers.millimetersPerMile
    let today = ISO8601DateFormatter().date(from: "2026-11-27T12:00:00Z")!
    func json(_ value: String) throws -> JSONValue { try OwnershipCore.decode(Data(value.utf8)) }
    func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("SubaruBridge-tests-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try FileManager.default.removeItem(at: url) }; return url
    }
    func store() throws -> GarageRepository { GarageRepository(file: try directory().appendingPathComponent("garage.json")) }
    @discardableResult func addCar(_ store: GarageRepository) throws -> JSONValue {
        try store.execute(json(#"{"type":"addVehicle","vehicle":{"id":"car","nickname":"QA Legacy","make":"Subaru","model":"Legacy","year":2017,"engine":"2.5","transmission":"CVT","maintenanceProfile":"subaru-us-2017-legacy-25-cvt"}}"#), at: today)
    }
    func reading(_ id: String, _ miles: Int64, at: Int64 = 100) -> JSONValue {
        .object(["id": .string(id), "vehicleId": .string("car"), "atMs": .integer(at), "distanceMm": .integer(miles * mile), "source": .string("manual"), "confidence": .string("confirmed")])
    }
    func testVehicleAndSourcedScheduleSurviveRelaunch() throws {
        let repository = try store(); let output = try addCar(repository)
        XCTAssertEqual(output["schedule"]["rules"].list.count, 18)
        XCTAssertTrue(output["summaries"].list[0]["maintenance"].list.allSatisfy { $0["status"].text == "unknownHistory" || $0["status"].text == "notApplicable" })
        let loaded = try GarageRepository(file: repository.file).view(at: today)
        XCTAssertEqual(loaded["state"], output["state"])
        XCTAssertTrue(loaded["schedule"]["sourceUrl"].text?.hasPrefix("https://contactus.subaru.com/") == true)
    }
    func testUncertainOilChangePreservesTheMileageRange() throws {
        let repository = try store(); try addCar(repository)
        _ = try repository.execute(.object(["type": .string("addReading"), "reading": reading("r", 81_000)]), at: today)
        let output = try repository.execute(json(#"{"type":"addService","service":{"id":"s","vehicleId":"car","itemIds":["engine-oil","oil-filter"],"mileage":{"lowMm":112654080000,"highMm":119091456000},"notes":"Approximately 70,000–74,000 miles"}}"#), at: today)
        let oil = try XCTUnwrap(output["summaries"].list[0]["maintenance"].list.first { $0["itemId"].text == "engine-oil" })
        XCTAssertTrue(oil["uncertain"].flag); XCTAssertEqual(oil["nextLowMm"].int, 76_000 * mile); XCTAssertEqual(oil["nextHighMm"].int, 80_000 * mile)
        XCTAssertEqual(output["state"]["services"].list[0]["date"], .null)
    }
    func testReadingCorrectionRetainsAuditAndReconcilesTrips() throws {
        let repository = try store(); try addCar(repository)
        _ = try repository.execute(.object(["type": .string("addReading"), "reading": reading("r", 81_000)]), at: today)
        _ = try repository.execute(json(#"{"type":"addTrip","trip":{"id":"t","vehicleId":"car","startedMs":200,"endedMs":100000,"distanceMm":386242560,"source":"manual"}}"#), at: today)
        XCTAssertEqual(try repository.view(at: today)["summaries"].list[0]["mileage"]["currentMm"].int, 81_240 * mile)
        let fixed = reading("fixed", 81_250, at: 110_000).changing(["supersedes": .string("r"), "notes": .string("Corrected from an odometer photograph")])
        let output = try repository.execute(.object(["type": .string("addReading"), "reading": fixed]), at: today)
        XCTAssertEqual(output["state"]["readings"].list.count, 2)
        XCTAssertEqual(output["summaries"].list[0]["mileage"]["currentMm"].int, 81_250 * mile)
        XCTAssertFalse(output["summaries"].list[0]["mileage"]["estimated"].flag)
    }
    func testFillUpTransactionAndPartialFuelEconomy() throws {
        let repository = try store(); try addCar(repository)
        for (id, time, miles, ml, full) in [("a", 1, 80000, 40000, true), ("b", 2, 80150, 20000, false), ("c", 3, 80300, 20000, true)] {
            let entry = JSONValue.object(["id": .string(id), "vehicleId": .string("car"), "atMs": .integer(Int64(time)), "distanceMm": .integer(Int64(miles) * mile), "volumeMl": .integer(Int64(ml)), "costCents": .integer(1000), "full": .bool(full)])
            _ = try repository.execute(.object(["type": .string("recordFillUp"), "entry": entry, "reading": reading("r" + id, Int64(miles), at: Int64(time))]), at: today)
        }
        let view = try repository.view(at: today), summary = view["summaries"].list[0]
        XCTAssertEqual(summary["fuel"]["completedCycles"].int, 1); XCTAssertEqual(summary["fuel"]["distanceMm"].int, 300 * mile)
        XCTAssertEqual(summary["fuel"]["volumeMl"].int, 40_000); XCTAssertEqual(summary["costs"]["fuelCents"].int, 3000)
        let before = try Data(contentsOf: repository.file)
        XCTAssertThrowsError(try repository.execute(json(#"{"type":"recordFillUp","entry":{"id":"bad","vehicleId":"car","atMs":4,"distanceMm":1,"volumeMl":100,"costCents":1,"full":true},"reading":{"id":"bad-r","vehicleId":"car","atMs":4,"distanceMm":2,"source":"manual","confidence":"confirmed"}}"#), at: today))
        XCTAssertEqual(try Data(contentsOf: repository.file), before)
    }
    func testExpensesAndServiceEditsDoNotDoubleCount() throws {
        let repository = try store(); try addCar(repository)
        _ = try repository.execute(json(#"{"type":"addService","service":{"id":"s","vehicleId":"car","itemIds":["engine-oil"],"date":"2026-11-01","costCents":5000}}"#), at: today)
        _ = try repository.execute(json(#"{"type":"updateService","service":{"id":"s","vehicleId":"car","itemIds":["engine-oil"],"date":"2026-11-01","costCents":6000}}"#), at: today)
        let output = try repository.execute(json(#"{"type":"addExpense","expense":{"id":"e","vehicleId":"car","date":"2026-11-01","category":"insurance","costCents":10000}}"#), at: today)
        XCTAssertEqual(output["state"]["services"].list.count, 1); XCTAssertEqual(output["summaries"].list[0]["costs"]["recordedOperatingCents"].int, 16000)
    }
    func testAndroidBackupRoundTripAndConflictPreservesData() throws {
        let source = try store(); try addCar(source)
        _ = try source.execute(json(#"{"type":"addCustomItem","item":{"id":"renewal","vehicleId":"car","title":"Registration renewal","action":"renew","interval":{"months":12,"miles":null}}}"#), at: today)
        _ = try source.execute(json(#"{"type":"setPreferences","preferences":{"dueSoonMm":1000000,"dueSoonDays":60}}"#), at: today)
        let bytes = try source.backup(at: today), target = try store()
        let envelope = try OwnershipCore.decode(bytes); XCTAssertEqual(envelope["format"].text, "org.subarubridge.garage")
        let restored = try target.restore(bytes, at: today)
        XCTAssertEqual(restored["state"]["customItems"].list.count, 1); XCTAssertEqual(restored["state"]["preferences"]["dueSoonDays"].int, 60)
        XCTAssertFalse(try target.restore(bytes, at: today)["changed"].flag)
        let before = try Data(contentsOf: target.file)
        let conflicting = envelope.changing(["garage": envelope["garage"].changing(["vehicles": .array([envelope["garage"]["vehicles"].list[0].changing(["nickname": .string("Conflict")])])])])
        XCTAssertThrowsError(try target.restore(OwnershipCore.encode(conflicting), at: today))
        XCTAssertEqual(try Data(contentsOf: target.file), before)
    }
    func testInvalidAndFutureDocumentsAreNeverReplaced() throws {
        for text in ["{broken", #"{"schemaVersion":999,"revision":0}"#, #"{"schemaVersion":1,"schemaVersion":2,"revision":0}"#] {
            let repository = try store(); let original = Data(text.utf8); try original.write(to: repository.file)
            XCTAssertThrowsError(try repository.view(at: today)); XCTAssertThrowsError(try addCar(repository))
            XCTAssertEqual(try Data(contentsOf: repository.file), original)
        }
    }
    func testStaleEditorRevisionDoesNotOverwriteAnotherSave() throws {
        let repository = try store(); let first = try addCar(repository)
        _ = try repository.execute(.object(["type": .string("addReading"), "reading": reading("r", 81000)]), expectedRevision: first["state"]["revision"].int, at: today)
        XCTAssertThrowsError(try repository.execute(.object(["type": .string("addReading"), "reading": reading("stale", 82000)]), expectedRevision: first["state"]["revision"].int, at: today)) { XCTAssertEqual($0 as? OwnershipError, .conflict) }
        XCTAssertEqual(try repository.view(at: today)["state"]["readings"].list.count, 1)
    }
    func testRepositoryRejectsSymlinksAndOversizeImports() throws {
        let repository = try store(), target = repository.file.deletingLastPathComponent().appendingPathComponent("target")
        try Data("private".utf8).write(to: target); try FileManager.default.createSymbolicLink(at: repository.file, withDestinationURL: target)
        XCTAssertThrowsError(try repository.view()); XCTAssertThrowsError(try repository.restore(Data(repeating: 32, count: GarageRepository.maximumState + 4097)))
        XCTAssertEqual(try String(contentsOf: target), "private")
    }
    func testExactNumbersAndLocaleInput() throws {
        let us = Locale(identifier: "en_US"), de = Locale(identifier: "de_DE")
        XCTAssertEqual(try OwnershipNumbers.money("123.45", locale: us), 12345)
        XCTAssertEqual(try OwnershipNumbers.money("123,45", locale: de), 12345)
        XCTAssertEqual(try OwnershipNumbers.distance("81000", metric: false, locale: us), 81000 * mile)
        XCTAssertEqual(try OwnershipNumbers.distance("1,234", metric: true, locale: de), 1_234_000)
        XCTAssertEqual(try OwnershipNumbers.volume("10", metric: false, locale: us), 37854)
        for bad in ["1,000", "-1", "1e4", "123.456", "NaN", "1.2.3", "1 000", "1\n2"] { XCTAssertThrowsError(try OwnershipNumbers.money(bad, locale: us), bad) }
        XCTAssertEqual(OwnershipNumbers.editMoney(12345, locale: de), "123,45")
    }
    func testCivilDateRespectsPhoneTimeZone() throws {
        let instant = ISO8601DateFormatter().date(from: "2026-11-27T01:00:00Z")!
        XCTAssertEqual(OwnershipCore.date(instant, zone: TimeZone(secondsFromGMT: -18000)!), "2026-11-26")
        XCTAssertEqual(OwnershipCore.date(instant, zone: TimeZone(secondsFromGMT: 0)!), "2026-11-27")
    }
}
