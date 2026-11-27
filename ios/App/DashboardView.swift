import SwiftUI
import UIKit
import SubaruBridgeCore

@MainActor final class Workspace: ObservableObject {
    @Published var session = DiagnosticSession()
    @Published var error: String?
    @Published var exportURL: URL?
    @Published var battery: Int?
    func record(_ stage: CheckStage, _ action: ObservationAction) {
        do { try session.accept(.init(sequence: session.nextSequence, stage: stage, action: action)) }
        catch { self.error = "Could not record this local check: \(error)" }
    }
    func readPhone() {
        let original = UIDevice.current.isBatteryMonitoringEnabled
        UIDevice.current.isBatteryMonitoringEnabled = true
        let level = UIDevice.current.batteryLevel
        battery = level >= 0 ? Int(level * 100) : nil
        UIDevice.current.isBatteryMonitoringEnabled = original
        record(.phone, .data)
    }
    func save() {
        do {
            var saved = session
            try saved.accept(.init(sequence: saved.nextSequence, stage: .results, action: .summary))
            let directory = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            let destination = directory.appendingPathComponent("subarubridge-local-\(UUID().uuidString).json")
            let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            try encoder.encode(saved).write(to: destination, options: .atomic)
            session = saved
            exportURL = destination
        } catch { self.error = "The local result was not exported: \(error)" }
    }
}

struct ConnectionLabView: View {
    @StateObject private var workspace = Workspace()
    @StateObject private var inventory = AccessoryInventory()
    @AppStorage("uiTheme") private var theme = "midnight"
    @State private var configuration: DashboardConfiguration?
    @State private var configurationError: String?
    private var background: Color { color("background", fallback: .black) }
    private func color(_ key: String, fallback: Color) -> Color {
        guard let hex = configuration?.themes[theme]?[key], hex.count == 7,
              let rgb = UInt32(hex.dropFirst(), radix: 16) else { return fallback }
        return Color(red: Double((rgb >> 16) & 255)/255, green: Double((rgb >> 8) & 255)/255, blue: Double(rgb & 255)/255)
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text(configuration?.homeTitle ?? "SubaruBridge").font(.largeTitle.bold())
                    Label("Local iPhone demo Â· No vehicle transport connected", systemImage: "info.circle")
                        .font(.callout).foregroundStyle(.secondary)
                    if let configurationError { Text(configurationError).foregroundStyle(.red) }
                    if let configuration {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                            ForEach(configuration.tiles) { tile in
                                if let stage = CheckStage(rawValue: tile.screen) {
                                    NavigationLink(value: stage) {
                                        VStack(spacing: 12) {
                                            Image(systemName: symbol(stage)).font(.largeTitle).frame(height: 52)
                                            Text(stage == .phone ? "iPhone" : tile.title).font(.headline)
                                        }.frame(maxWidth: .infinity).padding(20)
                                            .background(color("surface", fallback: .gray.opacity(0.2)), in: RoundedRectangle(cornerRadius: 24))
                                    }.buttonStyle(.plain).accessibilityIdentifier("tool-\(stage.rawValue)")
                                }
                            }
                        }
                    }
                    Picker("Appearance", selection: $theme) {
                        Text("Midnight").tag("midnight"); Text("Daylight").tag("daylight"); Text("Coast").tag("coast")
                    }.pickerStyle(.segmented)
                    GroupBox("Accessory investigation") {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("List accessories exposed to this app by iOS. An empty list does not establish that the Subaru is incompatible.").font(.callout)
                            Button("Inspect connected accessories") { inventory.inspect() }
                            if inventory.inspected && inventory.accessories.isEmpty { Text("No app-accessible accessories were reported.").font(.caption) }
                            ForEach(inventory.accessories) { accessory in
                                VStack(alignment: .leading) {
                                    Text(accessory.name).font(.headline)
                                    Text(accessory.model).font(.caption)
                                    Text(accessory.protocols.joined(separator: "\n")).font(.caption.monospaced())
                                }
                            }
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Text("Car transport is a separate milestone. This starter does not relay calls, messages or music, and has no server credentials or accessory identity material.").font(.caption).foregroundStyle(.secondary)
                }.padding()
            }
            .background(background).foregroundStyle(color("text", fallback: .primary))
            .navigationTitle(configuration?.brand ?? "SubaruBridge").navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: CheckStage.self) { stage in stageView(stage) }
            .task { do { configuration = try DashboardConfiguration.bundled() } catch { configurationError = "Dashboard configuration unavailable: \(error)" } }
            .alert("Local check", isPresented: Binding(get: { workspace.error != nil }, set: { if !$0 { workspace.error = nil } })) {
                Button("OK") { workspace.error = nil }
            } message: { Text(workspace.error ?? "") }
        }
    }
    private func symbol(_ stage: CheckStage) -> String {
        switch stage { case .display: return "display"; case .touch: return "hand.tap.fill"; case .phone: return "iphone"; case .results: return "checkmark.rectangle.fill" }
    }
    @ViewBuilder private func stageView(_ stage: CheckStage) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Local demo â€” this is not a car acknowledgement").font(.caption).foregroundStyle(.secondary)
                switch stage {
                case .display:
                    Text("A clear beginning.").font(.title.bold())
                    HStack(spacing: 0) { ForEach([Color.green, .yellow, .blue, .pink], id: \.self) { $0.frame(height: 120) } }.clipShape(RoundedRectangle(cornerRadius: 20))
                    Text("Original local color test. No cloud request is made.")
                case .touch:
                    Text("\(workspace.session.touches)").font(.system(size: 80, weight: .light))
                    Button("Record local touch") { workspace.record(.touch, .touch) }.buttonStyle(.borderedProminent)
                case .phone:
                    Text("Battery: \(workspace.battery.map { "\($0)%" } ?? "Not read")")
                    Text("Local time: \(Date.now.formatted(date: .abbreviated, time: .shortened))")
                    Button("Read this iPhone") { workspace.readPhone() }.buttonStyle(.borderedProminent)
                case .results:
                    Text(workspace.session.complete ? "Local checks complete" : "Local check progress").font(.title.bold())
                    Text("Screens \(workspace.session.screensSeen)/4 Â· Touches \(workspace.session.touches) Â· Data reads \(workspace.session.dataReads)")
                    Button("Save local result") { workspace.save() }.buttonStyle(.borderedProminent)
                    if let exportURL = workspace.exportURL { ShareLink("Share this local test result", item: exportURL) }
                }
            }.frame(maxWidth: .infinity, alignment: .leading).padding()
        }.navigationTitle(stage.title).onAppear { workspace.record(stage, .page) }
    }
}
