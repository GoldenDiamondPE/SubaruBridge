import Foundation
import SwiftUI
import SubaruBridgeCore

@MainActor final class GarageModel: ObservableObject {
    @Published var snapshot = JSONValue.null
    @Published var analytics: [JSONValue] = []
    @Published var busy = false
    @Published var issue: String?
    @Published var selectedID = UserDefaults.standard.string(forKey: "garageSelectedVehicle") ?? "" {
        didSet { UserDefaults.standard.set(selectedID, forKey: "garageSelectedVehicle") }
    }
    let repository: GarageRepository
    private var generation = 0
    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var path = base.appendingPathComponent("SubaruBridge/garage/garage-v1.json")
        #if DEBUG && targetEnvironment(simulator)
        if ProcessInfo.processInfo.arguments.contains("--garage-ui-test") {
            path = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("SubaruBridge-QA/garage-v1.json")
            if ProcessInfo.processInfo.arguments.contains("--garage-reset") { try? FileManager.default.removeItem(at: path) }
        }
        #endif
        repository = GarageRepository(file: path)
    }
    var state: JSONValue { snapshot["state"] }
    var vehicles: [JSONValue] { state["vehicles"].list }
    var vehicle: JSONValue { vehicles.first(where: { $0["id"].text == selectedID }) ?? vehicles.first(where: { !$0["archived"].flag }) ?? vehicles.first ?? .null }
    var vehicleID: String { vehicle["id"].text ?? "" }
    var metric: Bool { vehicle["units"].text == "metric" }
    var currency: String { vehicle["currency"].text ?? "USD" }
    var summary: JSONValue { snapshot["summaries"].list.first(where: { $0["vehicleId"].text == vehicleID }) ?? .null }
    var maintenance: [JSONValue] { summary["maintenance"].list }
    func records(_ name: String) -> [JSONValue] { state[name].list.filter { $0["vehicleId"].text == vehicleID } }
    func distance(_ value: Int64?) -> String { OwnershipNumbers.displayDistance(value, metric: metric) }
    func money(_ value: Int64?) -> String { OwnershipNumbers.displayMoney(value ?? 0, currency: currency) }
    func title(_ item: String) -> String { maintenance.first(where: { $0["itemId"].text == item })?["title"].text ?? item }
    func refresh() async {
        guard !busy else { return }; generation += 1; let request = generation; let store = repository
        do { let result = try await Task.detached { try store.view() }.value; if request == generation { snapshot = result; issue = nil; await GarageNotifications.update(result) } }
        catch { if request == generation { issue = error.localizedDescription } }
    }
    @discardableResult func apply(_ command: JSONValue, revision: Int64? = nil) async -> Bool {
        guard !busy else { return false }; busy = true; generation += 1; defer { busy = false }; let store = repository
        do {
            snapshot = try await Task.detached { try store.execute(command, expectedRevision: revision) }.value
            issue = nil; await GarageNotifications.update(snapshot); return true
        } catch { issue = error.localizedDescription; return false }
    }
    func restore(_ data: Data) async -> Bool {
        guard !busy else { return false }; busy = true; generation += 1; defer { busy = false }; let store = repository
        do { snapshot = try await Task.detached { try store.restore(data) }.value; issue = nil; await GarageNotifications.update(snapshot); return true }
        catch { issue = error.localizedDescription; return false }
    }
    func analyze(yearly: Bool) async {
        let calendar = Calendar.current; let now = Date(); let component: Calendar.Component = yearly ? .year : .month
        guard let start = calendar.dateInterval(of: component, for: now)?.start else { return }
        var periods: [JSONValue] = []
        for offset in (-11...0) {
            guard let from = calendar.date(byAdding: component, value: offset, to: start), let to = calendar.date(byAdding: component, value: 1, to: from) else { continue }
            periods.append(.object(["id": .string("period\(offset)"), "label": .string(from.formatted(yearly ? .dateTime.year() : .dateTime.month(.abbreviated).year(.twoDigits))),
                "startMs": .integer(Int64(from.timeIntervalSince1970 * 1000)), "endMs": .integer(Int64(to.timeIntervalSince1970 * 1000)),
                "startDate": .string(OwnershipCore.date(from)), "endDate": .string(OwnershipCore.date(to))]))
        }
        let store = repository; let id = vehicleID
        let command = JSONValue.object(["type": .string("analyze"), "periods": .array(periods)])
        do {
            let result = try await Task.detached { try store.execute(command) }.value
            analytics = result["summaries"].list.first(where: { $0["vehicleId"].text == id })?["analytics"].list ?? []
        } catch { issue = error.localizedDescription }
    }
}

enum GarageEditKind: String { case vehicle, reading, service, fuel, expense, trip, customItem, interval, preferences, snooze }
struct GarageEditRoute: Identifiable {
    let id = UUID(); let kind: GarageEditKind; var record = JSONValue.null
}

func garageStatus(_ value: String?) -> String {
    ["upToDate": "Up to date", "dueSoon": "Due soon", "due": "Due", "overdue": "Overdue", "inspectionRecommended": "Inspection recommended", "unknownHistory": "Unknown history", "notApplicable": "Not applicable"][value ?? ""] ?? "Unknown"
}

func garageDate(_ milliseconds: Int64?) -> String {
    guard let milliseconds else { return "Date unknown" }
    return Date(timeIntervalSince1970: Double(milliseconds) / 1000).formatted(date: .abbreviated, time: .shortened)
}
