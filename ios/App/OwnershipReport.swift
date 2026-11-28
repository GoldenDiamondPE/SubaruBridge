import Foundation
import SubaruBridgeCore

enum OwnershipReport {
    private static func escaped(_ value: String) -> String { value.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;").replacingOccurrences(of: "'", with: "&#39;") }
    private static func cell(_ value: String) -> String {
        let first = value.trimmingCharacters(in: .whitespacesAndNewlines).first
        let safe = first.map { "=+-@".contains($0) } == true ? "'" + value : value
        return "\"" + safe.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }
    static func csv(state: JSONValue, vehicle: JSONValue) throws -> Data {
        let id = vehicle["id"].text
        var rows = [["type", "date", "distanceMillimeters", "costCents", "details", "notes"]]
        for group in ["readings", "services", "fuel", "expenses", "trips"] {
            for value in state[group].list where value["vehicleId"].text == id {
                let date = value["date"].text ?? (value["atMs"].int ?? value["startedMs"].int).map { ISO8601DateFormatter().string(from: Date(timeIntervalSince1970: Double($0) / 1000)) } ?? "Unknown"
                let range = value["mileage"]
                let distance = value["distanceMm"].int.map(String.init) ?? (range["lowMm"].int.map(String.init) ?? "") + (range["highMm"].int.map { "…\($0)" } ?? "")
                rows.append([group, date, distance, value["costCents"].int.map(String.init) ?? "", value["provider"].text ?? value["category"].text ?? value["itemIds"].list.compactMap(\.text).joined(separator: "; "), value["notes"].text ?? ""])
            }
        }
        return Data(rows.map { $0.map(cell).joined(separator: ",") }.joined(separator: "\r\n").utf8)
    }
    static func html(state: JSONValue, vehicle: JSONValue) throws -> Data {
        let records = state["services"].list.filter { $0["vehicleId"].text == vehicle["id"].text }
        let rows = records.map { value -> String in
            let fields = [value["date"].text ?? "Unknown date", value["itemIds"].list.compactMap(\.text).joined(separator: ", "), value["provider"].text ?? "", OwnershipNumbers.displayMoney(value["costCents"].int ?? 0, currency: vehicle["currency"].text ?? "USD"), value["notes"].text ?? ""]
            return "<tr>" + fields.map { "<td>\(escaped($0))</td>" }.joined() + "</tr>"
        }.joined()
        let document = "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>SubaruBridge service history</title><style>body{font:16px system-ui;margin:32px;color:#151515}table{border-collapse:collapse;width:100%}th,td{padding:10px;border-bottom:1px solid #ddd;text-align:left}td{white-space:pre-wrap}h1{font-size:24px}@media print{body{margin:0}}</style><h1>\(escaped(vehicle["nickname"].text ?? "Vehicle")) — recorded service history</h1><p>\(vehicle["year"].int ?? 0) \(escaped(vehicle["make"].text ?? "")) \(escaped(vehicle["model"].text ?? ""))</p><p>Owner-entered records; dates and costs may be incomplete. No unrecorded maintenance is assumed.</p><table><thead><tr><th>Date</th><th>Items</th><th>Provider</th><th>Recorded cost</th><th>Notes</th></tr></thead><tbody>\(rows)</tbody></table></html>"
        return Data(document.utf8)
    }
}
