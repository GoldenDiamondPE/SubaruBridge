import Foundation
import SwiftUI
import UIKit
import SubaruBridgeCore

final class LockedPhoneSnapshot: @unchecked Sendable {
    private let lock = NSLock(); private var battery: Int?
    private let started = ProcessInfo.processInfo.systemUptime
    func update(_ value: Int?) { lock.lock(); defer { lock.unlock() }; battery = value }
    func read() -> StarlinkPhoneSnapshot {
        lock.lock(); defer { lock.unlock() }
        return StarlinkPhoneSnapshot(batteryPercent: battery, wallTimeMs: UInt64(max(0, Date().timeIntervalSince1970 * 1000)), uptimeMs: UInt64(max(0, ProcessInfo.processInfo.systemUptime - started) * 1000))
    }
}

@MainActor final class BridgeRuntime: ObservableObject {
    @Published var issue: String?
    @Published var recordings: [DiagnosticRecording] = []
    @Published var current: DiagnosticRecording?
    @Published var previewURL: URL?
    @Published var previewIssue: String?
    @Published var checkResult = "Checks run on this iPhone. Car display, input and reconnection need a separate parked-car test."
    let phone = LockedPhoneSnapshot()
    let accessory = AccessoryConnection()
    private var recorder: DiagnosticRecorder?
    private var server: PreviewServer?
    private var router: StarlinkHTTPServer?
    private var pulse: Timer?
    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("SubaruBridge/diagnostic-sessions")
        do { recorder = try DiagnosticRecorder(directory: base) } catch { issue = "Diagnostic storage unavailable: \(error.localizedDescription)" }
        UIDevice.current.isBatteryMonitoringEnabled = true
        samplePhone(); refresh()
        pulse = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in Task { @MainActor in self?.samplePhone(); self?.refresh() } }
        accessory.event = { [weak self] kind, values in Task { @MainActor in self?.record(kind, values) } }
    }
    func samplePhone() {
        let level = UIDevice.current.batteryLevel; let percent = level >= 0 ? Int(level * 100) : nil; phone.update(percent)
        record("phone_sample", ["batteryPercent": percent.map { .integer(Int64($0)) } ?? .null, "charging": .bool(UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full), "platform": .string("ios"), "scope": .string("Phone state; no vehicle telemetry")])
    }
    func refresh() { current = recorder?.current(); do { recordings = try recorder?.recordings() ?? [] } catch { issue = error.localizedDescription } }
    func record(_ kind: String, _ fields: [String: JSONValue] = [:]) {
        do { try recorder?.record(kind, fields: fields) } catch { issue = error.localizedDescription }; refresh()
    }
    func startRecording() { do { guard let recorder else { throw OwnershipError.storage }; try recorder.start(); samplePhone(); refresh() } catch { issue = error.localizedDescription } }
    func stopRecording(_ reason: String = "saved") { do { try recorder?.stop(reason); refresh() } catch { issue = error.localizedDescription } }
    func mark(_ kind: String, note: String = "") { do { try recorder?.mark(kind, note: note); refresh() } catch { issue = error.localizedDescription } }
    func delete(_ id: String) { do { try recorder?.delete(id); refresh() } catch { issue = error.localizedDescription } }
    func timeline(_ id: String) -> String { (try? recorder?.timeline(id)) ?? "This recording is unavailable." }
    func report() throws -> Data {
        guard let recorder else { throw OwnershipError.storage }
        return try recorder.report(metadata: ["product": .string("SubaruBridge"), "build": .string("0.57-ios"), "scope": .string("App metadata and private notes only. Garage, credentials, messages and raw packets excluded."), "connection": .string(accessory.status), "preview": .string(previewURL == nil ? "stopped" : "ready"), "backgroundPolicy": .string("Recording saves when the app backgrounds; no continuous general background service is claimed")])
    }
    func background() {
        stopRecording("background")
        accessory.stop(); server?.stop(); server = nil; previewURL = nil
    }
    func preparePreview() {
        guard server == nil else { return }; samplePhone()
        do {
            let sample = phone
            let logger = recorder
            let handler = try StarlinkHTTPServer(theme: UserDefaults.standard.string(forKey: "uiTheme") ?? "midnight", layout: UserDefaults.standard.string(forKey: "carLayout") ?? "grid", phone: { sample.read() }, save: { data in
                let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("SubaruBridge/check-results")
                try GarageRepository.atomicWrite(data, to: directory.appendingPathComponent(UUID().uuidString.lowercased() + ".json"))
            }, saveAppearance: { theme, layout in UserDefaults.standard.set(theme, forKey: "uiTheme"); UserDefaults.standard.set(layout, forKey: "carLayout") })
            handler.observed = { source, method, route, status, count in
                try? logger?.record("http", fields: ["source": .string(source), "method": .string(method), "route": .string(route), "status": .integer(Int64(status)), "responseBytes": .integer(Int64(count))])
            }
            router = handler
            let network = try PreviewServer(router: handler) { [weak self] result in Task { @MainActor in
                switch result { case let .success(url): self?.previewURL = url; self?.previewIssue = nil
                case .failure: self?.previewIssue = "The local dashboard could not start. Reopen the app to try again."; self?.server = nil }
            } }
            server = network; network.start(); accessory.router = handler
        } catch { previewIssue = error.localizedDescription }
    }
    func selfCheck() {
        var lines: [String] = []
        do { let core = try StarlinkTransportCore(seed: 1); _ = try core.feed(Data([0xc0]), at: 1); _ = try core.tick(at: 2); core.close(); lines.append("App connection engine: passed") }
        catch { lines.append("App connection engine: failed") }
        do { _ = try OwnershipCore.execute(OwnershipCore.request(state: OwnershipCore.empty, command: .object(["type": .string("view")]))); lines.append("Maintenance engine: passed") }
        catch { lines.append("Maintenance engine: failed") }
        preparePreview()
        if let router {
            let valid = router.handle(Data("GET /version HTTP/1.0\r\n\r\n".utf8), source: "selfcheck").status == 200
            let rejected = router.handle(Data("GET http://example.invalid/ HTTP/1.0\r\n\r\n".utf8), source: "selfcheck").status == 400
            lines.append("Local dashboard and access checks: \(valid && rejected ? "passed" : "failed")")
        } else { lines.append("Local dashboard: unavailable") }
        lines.append("Phone checks only. Subaru display, input, audio and reconnect remain unverified.")
        checkResult = lines.joined(separator: "\n\n"); record("phone_self_check", ["scope": .string("phone only")])
    }
}
