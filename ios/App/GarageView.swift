import SwiftUI
import Charts
import UniformTypeIdentifiers
import SubaruBridgeCore

struct GarageDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json, .commaSeparatedText, .html] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct GarageView: View {
    @EnvironmentObject var model: GarageModel
    @Environment(\.scenePhase) private var phase
    @State private var page = 0
    @State private var route: GarageEditRoute?
    @State private var exportDocument: GarageDocument?
    @State private var exporting = false
    @State private var importing = false
    @State private var exportType = UTType.json
    @State private var exportName = "SubaruBridge-Garage"
    @State private var query = ""
    var body: some View {
        Form {
            if model.snapshot == .null && model.issue == nil { ProgressView("Loading your Garage…") }
            if let issue = model.issue { Section { Text(issue).foregroundStyle(.red); Button("Try again") { Task { await model.refresh() } } } }
            if model.vehicles.isEmpty {
                Section { ContentUnavailableView("Your Garage", systemImage: "car", description: Text("Track maintenance, fuel and trips locally. No car connection or account is needed."))
                    Button("Add vehicle") { route = .init(kind: .vehicle) }.accessibilityIdentifier("garage-add-vehicle") }
            } else {
                Section {
                    Picker("Vehicle", selection: Binding(get: { model.vehicleID }, set: { model.selectedID = $0 })) {
                        ForEach(model.vehicles, id: \.selfID) { vehicle in Text((vehicle["nickname"].text ?? "Vehicle") + (vehicle["archived"].flag ? " · archived" : "")).tag(vehicle["id"].text ?? "") }
                    }.accessibilityIdentifier("garage-vehicle-picker")
                    Picker("Garage section", selection: $page) { Text("Overview").tag(0); Text("Maintenance").tag(1); Text("History").tag(2); Text("More").tag(3) }.pickerStyle(.segmented)
                        .accessibilityIdentifier("garage-sections")
                }
                if page == 0 { overview }
                if page == 1 { maintenance }
                if page == 2 { history }
                if page == 3 { more }
            }
        }.navigationTitle("Garage").accessibilityIdentifier("garage-screen")
            .toolbar { ToolbarItem(placement: .primaryAction) { Menu {
                Button("Add vehicle") { route = .init(kind: .vehicle) }
                if !model.vehicleID.isEmpty { recordActions }
            } label: { Image(systemName: "plus") }.accessibilityIdentifier("garage-add-menu") } }
            .sheet(item: $route) { route in NavigationStack { GarageEditor(route: route) }.environmentObject(model) }
            .fileExporter(isPresented: $exporting, document: exportDocument, contentType: exportType, defaultFilename: exportName) { result in
                if case let .failure(error) = result { model.issue = "Export failed: \(error.localizedDescription)" }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
                Task {
                    do { let url = try result.get(); let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                        let bytes = try GarageRepository.read(url, maximum: GarageRepository.maximumState + 4096); _ = await model.restore(bytes)
                    } catch { model.issue = "Import failed: \(error.localizedDescription)" }
                }
            }
            .task { await model.refresh() }
            .onChange(of: phase) { _, value in if value == .active { Task { await model.refresh() } } }
    }
    @ViewBuilder private var recordActions: some View {
        Button("Log odometer") { route = .init(kind: .reading) }
        Button("Record service") { route = .init(kind: .service) }
        Button("Record fill-up") { route = .init(kind: .fuel) }
        Button("Add expense") { route = .init(kind: .expense) }
        Button("Log completed trip") { route = .init(kind: .trip) }
    }
    private var overview: some View {
        Group {
            Section {
                Text(model.vehicle["nickname"].text ?? "Vehicle").font(.title2.bold())
                Text("\(String(model.vehicle["year"].int ?? 0)) \(model.vehicle["make"].text ?? "") \(model.vehicle["model"].text ?? "")").foregroundStyle(.secondary)
                LabeledContent(model.summary["mileage"]["estimated"].flag ? "Estimated mileage" : "Confirmed mileage", value: model.distance(model.summary["mileage"]["currentMm"].int)).accessibilityIdentifier("garage-current-mileage")
                ForEach(model.summary["mileage"]["warnings"].list, id: \.self) { Text($0.text ?? "").font(.caption).foregroundStyle(.orange) }
            }
            Section("Quick entry") {
                Button("Log odometer") { route = .init(kind: .reading) }.accessibilityIdentifier("garage-log-odometer")
                Button("Record service") { route = .init(kind: .service) }.accessibilityIdentifier("garage-log-service")
                Button("Record fill-up") { route = .init(kind: .fuel) }.accessibilityIdentifier("garage-log-fuel")
                Button("Add expense") { route = .init(kind: .expense) }.accessibilityIdentifier("garage-log-expense")
                Button("Log completed trip") { route = .init(kind: .trip) }.accessibilityIdentifier("garage-log-trip")
            }
            Section("Maintenance") {
                let attention = model.maintenance.filter { ["dueSoon", "due", "overdue", "inspectionRecommended"].contains($0["status"].text ?? "") }
                if attention.isEmpty { Text("No confirmed due items in the recorded history. Unknown history still needs review.").foregroundStyle(.secondary) }
                ForEach(Array(attention.prefix(5)), id: \.itemID) { due in Text("\(due["title"].text ?? "Maintenance") · \(garageStatus(due["status"].text))") }
            }
            Section("Recorded ownership") {
                LabeledContent("Operating costs", value: model.money(model.summary["costs"]["recordedOperatingCents"].int))
                LabeledContent("Including financing", value: model.money(model.summary["costs"]["recordedTotalCents"].int))
                LabeledContent("Full-to-full fuel cycles", value: String(model.summary["fuel"]["completedCycles"].int ?? 0))
                Text("Totals describe saved records. They do not imply complete vehicle history or live vehicle telemetry.").font(.caption).foregroundStyle(.secondary)
            }
        }
    }
    private var maintenance: some View {
        Group {
            Section {
                Button("Add custom maintenance") { route = .init(kind: .customItem) }.accessibilityIdentifier("garage-add-custom")
                Button("Reminder lead times") { route = .init(kind: .preferences, record: model.state["preferences"]) }
                NavigationLink("Maintenance notifications") { GarageReminderSettings() }
            }
            ForEach(model.maintenance, id: \.itemID) { due in
                Section {
                    NavigationLink { MaintenanceDetail(due: due) } label: {
                        VStack(alignment: .leading, spacing: 5) { Text(due["title"].text ?? "Maintenance"); Text(garageStatus(due["status"].text)).foregroundStyle(due["status"].text == "overdue" ? .red : .secondary).font(.caption) }
                    }
                }
            }
            if model.maintenance.isEmpty { Section { Text("Choose the verified vehicle profile or add owner-defined maintenance items.") } }
            Section("Source") {
                Text(model.snapshot["schedule"]["vehicle"].text ?? "Owner-defined schedule").font(.caption)
                Text(model.snapshot["schedule"]["limitations"].text ?? "").font(.caption).foregroundStyle(.secondary)
                if let text = model.snapshot["schedule"]["sourceUrl"].text, let url = URL(string: text) { Link("Manufacturer source", destination: url) }
            }
        }
    }
    private func recordRow(_ record: JSONValue, kind: GarageEditKind, title: String, detail: String) -> some View {
        Button { route = .init(kind: kind, record: record) } label: {
            VStack(alignment: .leading, spacing: 5) { Text(title).foregroundStyle(.primary); Text(detail).font(.caption).foregroundStyle(.secondary) }
        }
    }
    private var history: some View {
        Group {
            Section { TextField("Search saved records", text: $query).accessibilityIdentifier("garage-search") }
            Section("Odometer · corrections preserve the audit trail") {
                ForEach(filtered(model.records("readings")), id: \.selfID) { record in
                    recordRow(record, kind: .reading, title: model.distance(record["distanceMm"].int), detail: "\(garageDate(record["atMs"].int)) · \(record["confidence"].text ?? "unknown")\(record["supersedes"].text == nil ? "" : " · correction")")
                }
            }
            Section("Service book") {
                ForEach(filtered(model.records("services")), id: \.selfID) { record in
                    recordRow(record, kind: .service, title: record["itemIds"].list.compactMap(\.text).map(model.title).joined(separator: ", "), detail: "\(record["date"].text ?? "Date unknown") · \(model.money(record["costCents"].int)) · \(record["provider"].text ?? "")")
                }
            }
            Section("Fuel") { ForEach(filtered(model.records("fuel")), id: \.selfID) { record in
                recordRow(record, kind: .fuel, title: model.distance(record["distanceMm"].int), detail: "\(garageDate(record["atMs"].int)) · \(model.money(record["costCents"].int)) · \(record["full"].flag ? "Full" : "Partial")")
            } }
            Section("Expenses") { ForEach(filtered(model.records("expenses")), id: \.selfID) { record in
                recordRow(record, kind: .expense, title: record["category"].text ?? "Expense", detail: "\(record["date"].text ?? "") · \(model.money(record["costCents"].int))")
            } }
            Section("Completed trips") { ForEach(filtered(model.records("trips")), id: \.selfID) { record in
                recordRow(record, kind: .trip, title: model.distance(record["distanceMm"].int), detail: "\(garageDate(record["startedMs"].int)) · \(record["source"].text ?? "manual")")
            } }
        }
    }
    private func filtered(_ values: [JSONValue]) -> [JSONValue] {
        values.reversed().filter { query.isEmpty || ((try? String(data: OwnershipCore.encode($0), encoding: .utf8)) ?? "").localizedCaseInsensitiveContains(query) }
    }
    private var more: some View {
        Group {
            Section("Vehicle") { Button("Edit vehicle") { route = .init(kind: .vehicle, record: model.vehicle) }; Button("Add another vehicle") { route = .init(kind: .vehicle) } }
            Section("Summaries") { NavigationLink("Mileage & expense charts") { GarageAnalyticsView() }.accessibilityIdentifier("garage-analytics") }
            Section("Your data") {
                Button("Export complete Garage backup") { export(.json) }.accessibilityIdentifier("garage-export")
                Button("Import Android or iOS Garage backup") { importing = true }.accessibilityIdentifier("garage-import")
                Button("Export selected vehicle CSV") { export(.commaSeparatedText) }
                Button("Printable service history") { export(.html) }
                Text("Backups contain your private vehicle records and are unencrypted. Import adds compatible records and rejects conflicting edits. Choose where to share them.").font(.caption).foregroundStyle(.secondary)
            }
        }
    }
    private func export(_ type: UTType) {
        do {
            let bytes = type == .json ? try model.repository.backup() : type == .html ? try OwnershipReport.html(state: model.state, vehicle: model.vehicle) : try OwnershipReport.csv(state: model.state, vehicle: model.vehicle)
            exportDocument = GarageDocument(data: bytes); exportType = type; exportName = type == .json ? "SubaruBridge-Garage" : "SubaruBridge-Service-History"; exporting = true
        } catch { model.issue = error.localizedDescription }
    }
}

extension JSONValue {
    var selfID: String { self["id"].text ?? "unknown" }
    var itemID: String { self["itemId"].text ?? "unknown" }
}

private struct MaintenanceDetail: View {
    @EnvironmentObject var model: GarageModel
    let due: JSONValue
    @State private var route: GarageEditRoute?
    private var current: JSONValue { model.maintenance.first(where: { $0.itemID == due.itemID }) ?? due }
    var body: some View {
        Form {
            Section { Text(current["title"].text ?? "Maintenance").font(.title2); LabeledContent("Status", value: garageStatus(current["status"].text)); Text(current["explanation"].text ?? "").font(.callout) }
            Section("Next service") {
                LabeledContent("Mileage", value: model.distance(current["nextLowMm"].int) + (current["nextLowMm"] != current["nextHighMm"] ? " – " + model.distance(current["nextHighMm"].int) : ""))
                LabeledContent("Date", value: current["nextDate"].text ?? "Unknown")
                if let days = current["remainingDays"].int { LabeledContent("Days remaining", value: String(days)) }
                if let distance = current["remainingLowMm"].int { LabeledContent("Distance remaining", value: model.distance(distance)) }
                if current["uncertain"].flag { Text("Includes estimated mileage or uncertain/incomplete service history.").font(.caption).foregroundStyle(.secondary) }
            }
            Section {
                if let item = model.state["customItems"].list.first(where: { $0["id"].text == current.itemID }) { Button("Edit custom maintenance") { route = .init(kind: .customItem, record: item) } }
                Button("Set owner interval") { route = .init(kind: .interval, record: current) }
                Button("Use original interval") { Task { _ = await model.apply(.object(["type": .string("resetOverride"), "vehicleId": .string(model.vehicleID), "itemId": .string(current.itemID)])) } }
                Button("Snooze reminder") { route = .init(kind: .snooze, record: current) }
                if current["reminderSnoozedUntil"].text != nil { Button("Clear snooze") { Task { _ = await model.apply(.object(["type": .string("clearSnooze"), "vehicleId": .string(model.vehicleID), "itemId": .string(current.itemID)])) } } }
            }
            Section("Evidence") { Text(current["sourceRow"].text ?? "Owner-defined item"); if let text = current["sourceUrl"].text, let url = URL(string: text), !text.isEmpty { Link("Source document", destination: url) } }
        }.navigationTitle("Maintenance").sheet(item: $route) { item in NavigationStack { GarageEditor(route: item) }.environmentObject(model) }
    }
}

private struct GarageAnalyticsView: View {
    @EnvironmentObject var model: GarageModel
    @State private var yearly = false
    var body: some View {
        List {
            Picker("Period", selection: $yearly) { Text("Months").tag(false); Text("Years").tag(true) }.pickerStyle(.segmented)
            Section("Recorded trip distance") {
                Chart(model.analytics, id: \.periodID) { value in BarMark(x: .value("Period", value["period"]["label"].text ?? ""), y: .value("Distance", Double(value["tripDistanceMm"].int ?? 0) / (model.metric ? 1_000_000 : Double(OwnershipNumbers.millimetersPerMile)))) }.frame(height: 200)
                Text("\(model.metric ? "Kilometers" : "Miles") from saved trips. This is not a complete measurement of all driving.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Recorded operating cost") {
                Chart(model.analytics, id: \.periodID) { value in BarMark(x: .value("Period", value["period"]["label"].text ?? ""), y: .value("Cost", Double(value["costs"]["operatingCents"].int ?? 0) / 100)) }.frame(height: 200)
            }
            ForEach(model.analytics.reversed(), id: \.periodID) { value in
                Section(value["period"]["label"].text ?? "Period") {
                    LabeledContent("Saved trips", value: String(value["recordedTrips"].int ?? 0))
                    LabeledContent("Trip distance", value: model.distance(value["tripDistanceMm"].int))
                    LabeledContent("Confirmed odometer growth", value: model.distance(value["confirmedGrowthMm"].int))
                    LabeledContent("Operating costs", value: model.money(value["costs"]["operatingCents"].int))
                    LabeledContent("Financing", value: model.money(value["costs"]["financingCents"].int))
                    if (value["undatedServiceCount"].int ?? 0) > 0 { Text("Undated service expenses are excluded from calendar periods.").font(.caption) }
                }
            }
        }.navigationTitle("Ownership summaries").task(id: yearly) { await model.analyze(yearly: yearly) }
    }
}
private extension JSONValue { var periodID: String { self["period"]["id"].text ?? "unknown" } }
