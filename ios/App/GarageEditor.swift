import SwiftUI
import SubaruBridgeCore

@MainActor private final class GarageDraft: ObservableObject {
    @Published var text: [String: String] = [:]
    @Published var flags: [String: Bool] = [:]
    @Published var items: Set<String> = []
    @Published var date = Date()
    @Published var start = Date().addingTimeInterval(-1800)
    @Published var end = Date()
    let zone = TimeZone.current
    let revision: Int64?
    let route: GarageEditRoute
    let vehicle: JSONValue
    var initial: [String: String] = [:]
    var metric: Bool { vehicle["units"].text == "metric" }
    init(route: GarageEditRoute, model: GarageModel) {
        self.route = route; vehicle = model.vehicle; revision = model.state["revision"].int
        let record = route.record
        let amount: (Int64?) -> String = { OwnershipNumbers.editDistance($0, metric: model.metric) }
        let money: (Int64?) -> String = { OwnershipNumbers.editMoney($0 ?? 0) }
        func fromDate(_ text: String?) -> Date? { guard let text else { return nil }; let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = .current; f.dateFormat = "yyyy-MM-dd"; return f.date(from: text) }
        switch route.kind {
        case .vehicle:
            for key in ["nickname", "make", "model", "trim", "engine", "transmission", "drivetrain", "vin", "plate", "notes", "currency", "units"] { text[key] = record[key].text ?? "" }
            if record == .null { text.merge(["nickname": "My Legacy", "make": "Subaru", "model": "Legacy", "year": "2017", "trim": "2.5i Premium", "engine": "2.5", "transmission": "CVT", "drivetrain": "AWD", "currency": "USD", "units": "us"]) { _, n in n } }
            else { text["year"] = String(record["year"].int ?? 2017) }
            text["purchaseMileage"] = amount(record["purchaseMileageMm"].int); text["purchasePrice"] = record["purchasePriceCents"].int.map { money($0) } ?? ""
            flags["profile"] = record == .null || record["maintenanceProfile"].text != nil; flags["severe"] = record["severe"].flag; flags["archived"] = record["archived"].flag
            flags["dateKnown"] = record["inServiceDate"].text != nil; date = fromDate(record["inServiceDate"].text) ?? Date()
        case .reading:
            text["distance"] = amount(record["distanceMm"].int ?? model.summary["mileage"]["currentMm"].int)
            flags["approximate"] = record["confidence"].text == "approximate"
            if let at = record["atMs"].int { date = Date(timeIntervalSince1970: Double(at) / 1000) }
        case .service:
            text["low"] = amount(record["mileage"]["lowMm"].int); text["high"] = amount(record["mileage"]["highMm"].int)
            flags["range"] = record["mileage"]["lowMm"] != record["mileage"]["highMm"]
            text["cost"] = money(record["costCents"].int); text["provider"] = record["provider"].text ?? ""
            flags["dateKnown"] = record == .null || record["date"].text != nil; date = fromDate(record["date"].text) ?? Date()
            items = Set(record["itemIds"].list.compactMap(\.text)); flags["diy"] = record["diy"].flag
            text["partNumbers"] = record["partNumbers"].list.compactMap(\.text).joined(separator: ", "); text["warranty"] = record["warranty"].text ?? ""
            flags["breakdown"] = record["costBreakdown"] != .null
            for key in ["parts", "labor", "tax"] { text[key] = money(record["costBreakdown"][key + "Cents"].int) }
        case .fuel:
            text["distance"] = amount(record["distanceMm"].int ?? model.summary["mileage"]["currentMm"].int)
            if let ml = record["volumeMl"].int {
                let formatter = NumberFormatter(); formatter.numberStyle = .decimal; formatter.usesGroupingSeparator = false; formatter.maximumFractionDigits = 3
                text["volume"] = formatter.string(from: NSNumber(value: Double(ml) / (model.metric ? 1000 : 3785.411784))) ?? ""
            }
            text["cost"] = money(record["costCents"].int); flags["full"] = record == .null || record["full"].flag; flags["missed"] = record["missedFill"].flag
            if let at = record["atMs"].int { date = Date(timeIntervalSince1970: Double(at) / 1000) }
        case .expense:
            text["cost"] = money(record["costCents"].int); text["category"] = record["category"].text ?? "repair"; flags["financing"] = record["financing"].flag; date = fromDate(record["date"].text) ?? Date()
        case .trip:
            text["distance"] = amount(record["distanceMm"].int)
            if let at = record["startedMs"].int { start = Date(timeIntervalSince1970: Double(at) / 1000) }
            if let at = record["endedMs"].int { end = Date(timeIntervalSince1970: Double(at) / 1000) }
        case .customItem, .interval:
            let original = route.kind == .interval ? model.state["overrides"].list.first(where: { $0["vehicleId"].text == model.vehicleID && $0["itemId"].text == record.itemID }) ?? .null : record
            let rule = model.snapshot["schedule"]["rules"].list.first(where: { $0["id"].text == record.itemID }) ?? .null
            let interval = original["interval"] != .null ? original["interval"] : rule["normal"]
            text["title"] = record["title"].text ?? ""; text["miles"] = interval["miles"].int.map(String.init) ?? ""; text["months"] = interval["months"].int.map(String.init) ?? ""
            text["action"] = record["action"].text ?? "replace"; flags["disabled"] = original["disabled"].flag
        case .preferences:
            text["distance"] = amount(record["dueSoonMm"].int ?? 500 * OwnershipNumbers.millimetersPerMile); text["days"] = String(record["dueSoonDays"].int ?? 30)
        case .snooze: date = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
        }
        text["notes"] = record["notes"].text ?? ""; initial = text
    }
    func string(_ key: String) -> String { text[key] ?? "" }
    func flag(_ key: String) -> Bool { flags[key] ?? false }
    private func requiredInt(_ key: String) throws -> Int64 {
        guard string(key).range(of: #"^[0-9]{1,10}$"#, options: .regularExpression) != nil, let value = Int64(string(key)) else { throw OwnershipError.engine("Enter a whole number for \(key)") }; return value
    }
    private func distance(_ key: String, original: JSONValue = .null) throws -> Int64 {
        if string(key) == initial[key], let value = original.int { return value }
        return try OwnershipNumbers.distance(string(key), metric: metric)
    }
    private func cost(_ key: String) throws -> Int64 { string(key).isEmpty ? 0 : try OwnershipNumbers.money(string(key)) }
    func command() throws -> JSONValue {
        let old = route.record; let editing = old["id"].text != nil
        let id = old["id"].text ?? UUID().uuidString.lowercased()
        let car = vehicle["id"].text ?? ""
        func make(_ type: String, _ key: String, _ value: JSONValue) -> JSONValue { .object(["type": .string(type), key: value]) }
        func ms(_ date: Date) -> JSONValue { .integer(Int64(date.timeIntervalSince1970 * 1000)) }
        let notes = JSONValue.string(string("notes"))
        switch route.kind {
        case .vehicle:
            var values: [String: JSONValue] = ["id": .string(id), "year": .integer(try requiredInt("year")), "severe": .bool(flag("severe")), "archived": .bool(flag("archived"))]
            for key in ["nickname", "make", "model", "trim", "engine", "transmission", "drivetrain", "notes", "units"] { values[key] = .string(string(key)) }
            values["currency"] = .string(string("currency").uppercased())
            for key in ["vin", "plate"] { values[key] = string(key).isEmpty ? .null : .string(string(key)) }
            values["maintenanceProfile"] = flag("profile") ? .string("subaru-us-2017-legacy-25-cvt") : .null
            values["inServiceDate"] = flag("dateKnown") ? .string(OwnershipCore.date(date, zone: zone)) : .null
            values["purchaseMileageMm"] = string("purchaseMileage").isEmpty ? .null : .integer(try distance("purchaseMileage", original: old["purchaseMileageMm"]))
            values["purchasePriceCents"] = string("purchasePrice").isEmpty ? .null : .integer(try cost("purchasePrice"))
            return make(editing ? "updateVehicle" : "addVehicle", "vehicle", old.changing(values))
        case .reading:
            return make("addReading", "reading", .object(["id": .string(UUID().uuidString.lowercased()), "vehicleId": .string(car), "atMs": ms(date), "distanceMm": .integer(try distance("distance", original: old["distanceMm"])), "source": .string("manual"), "confidence": .string(flag("approximate") ? "approximate" : "confirmed"), "supersedes": editing ? old["id"] : .null, "notes": notes]))
        case .service:
            var values: [String: JSONValue] = ["id": .string(id), "vehicleId": .string(car), "itemIds": .array(items.sorted().map(JSONValue.string)), "date": flag("dateKnown") ? .string(OwnershipCore.date(date, zone: zone)) : .null, "provider": .string(string("provider")), "notes": notes, "diy": .bool(flag("diy")), "warranty": .string(string("warranty")), "partNumbers": .array(string("partNumbers").split(separator: ",").map { .string($0.trimmingCharacters(in: .whitespaces)) })]
            if string("low").isEmpty { values["mileage"] = .null }
            else { let low = try distance("low", original: old["mileage"]["lowMm"]); let high = flag("range") ? try distance("high", original: old["mileage"]["highMm"]) : low; values["mileage"] = .object(["lowMm": .integer(low), "highMm": .integer(high)]) }
            if flag("breakdown") {
                let parts = try cost("parts"), labor = try cost("labor"), tax = try cost("tax")
                values["costCents"] = .integer(parts + labor + tax); values["costBreakdown"] = .object(["partsCents": .integer(parts), "laborCents": .integer(labor), "taxCents": .integer(tax)])
            } else { values["costCents"] = .integer(try cost("cost")); values["costBreakdown"] = .null }
            return make(editing ? "updateService" : "addService", "service", old.changing(values))
        case .fuel:
            let mm = try distance("distance", original: old["distanceMm"])
            let volume = string("volume") == initial["volume"] && old["volumeMl"].int != nil ? old["volumeMl"].int! : try OwnershipNumbers.volume(string("volume"), metric: metric)
            let entry = old.changing(["id": .string(id), "vehicleId": .string(car), "atMs": ms(date), "distanceMm": .integer(mm), "volumeMl": .integer(volume), "costCents": .integer(try cost("cost")), "full": .bool(flag("full")), "missedFill": .bool(flag("missed")), "notes": notes])
            if editing { return make("updateFuel", "entry", entry) }
            let reading = JSONValue.object(["id": .string(UUID().uuidString.lowercased()), "vehicleId": .string(car), "atMs": ms(date), "distanceMm": .integer(mm), "source": .string("manual"), "confidence": .string("confirmed"), "notes": .string("Confirmed at this fill-up")])
            return .object(["type": .string("recordFillUp"), "entry": entry, "reading": reading])
        case .expense:
            return make(editing ? "updateExpense" : "addExpense", "expense", old.changing(["id": .string(id), "vehicleId": .string(car), "date": .string(OwnershipCore.date(date, zone: zone)), "category": .string(string("category")), "costCents": .integer(try cost("cost")), "financing": .bool(flag("financing")), "notes": notes]))
        case .trip:
            return make(editing ? "updateTrip" : "addTrip", "trip", old.changing(["id": .string(id), "vehicleId": .string(car), "startedMs": ms(start), "endedMs": ms(end), "distanceMm": .integer(try distance("distance", original: old["distanceMm"])), "source": old["source"] == .null ? .string("manual") : old["source"], "notes": notes]))
        case .customItem, .interval:
            let interval = JSONValue.object(["miles": string("miles").isEmpty ? .null : .integer(try requiredInt("miles")), "months": string("months").isEmpty ? .null : .integer(try requiredInt("months"))])
            if route.kind == .interval { return make("setOverride", "value", .object(["vehicleId": .string(car), "itemId": .string(old.itemID), "interval": interval, "disabled": .bool(flag("disabled"))])) }
            return make(editing ? "updateCustomItem" : "addCustomItem", "item", .object(["id": .string(id), "vehicleId": .string(car), "title": .string(string("title")), "action": .string(string("action")), "interval": interval, "notes": notes]))
        case .preferences: return make("setPreferences", "preferences", .object(["dueSoonMm": .integer(try distance("distance")), "dueSoonDays": .integer(try requiredInt("days"))]))
        case .snooze: return make("snoozeReminder", "value", .object(["vehicleId": .string(car), "itemId": .string(old.itemID), "until": .string(OwnershipCore.date(date, zone: zone))]))
        }
    }
}

struct GarageEditor: View {
    @EnvironmentObject var model: GarageModel
    @Environment(\.dismiss) private var dismiss
    let route: GarageEditRoute
    @State private var draft: GarageDraft?
    var body: some View {
        Group { if let draft { GarageEditorForm(draft: draft) } else { ProgressView("Preparing form…") } }
            .task { if draft == nil { draft = GarageDraft(route: route, model: model) } }
    }
}

private struct GarageEditorForm: View {
    @EnvironmentObject var model: GarageModel
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var draft: GarageDraft
    @State private var issue: String?
    @State private var saving = false
    @FocusState private var focused: String?
    private var unit: String { draft.metric ? "km" : "miles" }
    private func field(_ label: String, _ key: String, number: Bool = false) -> some View {
        TextField(label, text: Binding(get: { draft.string(key) }, set: { draft.text[key] = $0 }))
            .keyboardType(number ? .decimalPad : .default).focused($focused, equals: key)
            .accessibilityIdentifier("garage-field-" + key)
    }
    private func toggle(_ label: String, _ key: String) -> some View { Toggle(label, isOn: Binding(get: { draft.flag(key) }, set: { draft.flags[key] = $0 })).accessibilityIdentifier("garage-toggle-" + key) }
    private var date: some View { DatePicker("Date", selection: $draft.date, displayedComponents: draft.route.kind == .reading || draft.route.kind == .fuel ? [.date, .hourAndMinute] : [.date]) }
    var body: some View {
        Form {
            if let issue { Section { Text(issue).foregroundStyle(.red).accessibilityIdentifier("garage-form-error") } }
            fields
            if ![GarageEditKind.preferences, .snooze, .interval].contains(draft.route.kind) { Section("Notes") { field("Optional notes", "notes") } }
        }.navigationTitle(title).navigationBarTitleDisplayMode(.inline).environment(\.timeZone, draft.zone)
            .scrollDismissesKeyboard(.interactively)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() }.disabled(saving).accessibilityIdentifier("garage-save") }
                ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("Done") { focused = nil } }
            }
    }
    private var title: String { [GarageEditKind.vehicle: "Vehicle", .reading: "Odometer", .service: "Service record", .fuel: "Fuel fill-up", .expense: "Expense", .trip: "Completed trip", .customItem: "Custom maintenance", .interval: "Owner interval", .preferences: "Reminder lead times", .snooze: "Snooze reminder"][draft.route.kind] ?? "Garage" }
    @ViewBuilder private var fields: some View {
        switch draft.route.kind {
        case .vehicle:
            Section("Vehicle profile") { field("Nickname", "nickname"); field("Make", "make"); field("Model", "model"); field("Year", "year", number: true); field("Trim", "trim"); field("Engine", "engine"); field("Transmission", "transmission"); field("Drivetrain", "drivetrain") }
            Section("Local identifiers · optional") { field("VIN", "vin"); field("License plate", "plate") }
            Section("Units") {
                Picker("Distance and fuel", selection: Binding(get: { draft.string("units") }, set: { draft.text["units"] = $0 })) { Text("Miles / US gallons").tag("us"); Text("Kilometers / liters").tag("metric") }
                field("Currency code (USD)", "currency")
                Text("Existing distances retain their original physical units. Currency changes do not convert recorded prices.").font(.caption).foregroundStyle(.secondary)
            }
            Section("History") { field("Purchase odometer (\(unit), optional)", "purchaseMileage", number: true); field("Purchase price (optional)", "purchasePrice", number: true); toggle("Known in-service date", "dateKnown"); if draft.flag("dateKnown") { date }; toggle("Archived", "archived") }
            Section("Maintenance profile") { toggle("US 2017 Legacy 2.5 CVT schedule", "profile"); toggle("Severe driving conditions", "severe"); Text("The manufacturer profile is limited to the verified configuration. Unverified severe-service exceptions stay unknown unless you set an owner interval.").font(.caption).foregroundStyle(.secondary) }
        case .reading:
            Section { field("Odometer (\(unit))", "distance", number: true); date; toggle("Approximate reading", "approximate"); if draft.route.record != .null { Text("Saving creates an audited correction. The original reading is retained.").font(.caption) } }
        case .service:
            Section("Completed maintenance") {
                NavigationLink("Choose maintenance (\(draft.items.count) selected)") {
                    List { ForEach(model.maintenance, id: \.itemID) { item in Toggle(item["title"].text ?? item.itemID, isOn: Binding(get: { draft.items.contains(item.itemID) }, set: { if $0 { draft.items.insert(item.itemID) } else { draft.items.remove(item.itemID) } })).accessibilityIdentifier("garage-item-" + item.itemID) } }.navigationTitle("Completed items")
                }.accessibilityIdentifier("garage-choose-items")
            }
            Section("When") { toggle("Known service date", "dateKnown"); if draft.flag("dateKnown") { date }; field("Service odometer · optional (\(unit))", "low", number: true); toggle("Approximate mileage range", "range"); if draft.flag("range") { field("Upper odometer (\(unit))", "high", number: true) }; Text("Leave unknown dates or mileage blank. A range is preserved instead of inventing an exact reading.").font(.caption).foregroundStyle(.secondary) }
            Section("Service details") { field("Service provider", "provider"); toggle("DIY service", "diy"); field("Part numbers (comma separated)", "partNumbers"); field("Warranty information", "warranty") }
            Section("Cost (\(model.currency))") { toggle("Parts / labor / tax breakdown", "breakdown"); if draft.flag("breakdown") { field("Parts", "parts", number: true); field("Labor", "labor", number: true); field("Tax", "tax", number: true) } else { field("Total paid", "cost", number: true) } }
        case .fuel:
            Section { field("Odometer (\(unit))", "distance", number: true); field(draft.metric ? "Liters" : "US gallons", "volume", number: true); field("Total paid (\(model.currency))", "cost", number: true); date; toggle("Full tank", "full"); toggle("A fill-up was missed", "missed"); Text("Full-to-full economy includes partial fills. Marking a missed fill starts a new measurement cycle.").font(.caption).foregroundStyle(.secondary) }
        case .expense:
            Section { field("Category", "category"); field("Amount (\(model.currency))", "cost", number: true); date; toggle("Financing / purchase cost", "financing"); Text("Fuel and service costs already contribute to totals. Do not add them a second time here.").font(.caption).foregroundStyle(.secondary) }
        case .trip:
            Section { DatePicker("Started", selection: $draft.start); DatePicker("Ended", selection: $draft.end); field("Distance (\(unit))", "distance", number: true); Text("This is a manually recorded, completed trip. It contributes to an estimated odometer after the latest reading; it is not an automatic GPS recording.").font(.caption).foregroundStyle(.secondary) }
        case .customItem, .interval:
            Section { if draft.route.kind == .customItem { field("Maintenance name", "title"); Picker("Action", selection: Binding(get: { draft.string("action") }, set: { draft.text["action"] = $0 })) { Text("Replace").tag("replace"); Text("Inspect").tag("inspect") } }
                field("Interval miles · source unit", "miles", number: true); field("Interval months", "months", number: true)
                if draft.route.kind == .interval { toggle("Disable this item", "disabled") }
                Text("Set mileage, months or both. The first applicable threshold determines when service is due. These are owner-defined intervals.").font(.caption).foregroundStyle(.secondary)
            }
        case .preferences: Section { field("Due soon within (\(unit))", "distance", number: true); field("Due soon within days", "days", number: true) }
        case .snooze: Section { date; Text("The service status stays accurate while this reminder is snoozed.").font(.caption) }
        }
    }
    private func save() {
        focused = nil
        do { let command = try draft.command(); saving = true; Task { let saved = await model.apply(command, revision: draft.revision); saving = false; if saved { dismiss() } else { issue = model.issue ?? "Save could not finish. Please try again." } } }
        catch { issue = error.localizedDescription }
    }
}
