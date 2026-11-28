import SwiftUI
import SubaruBridgeCore
import UniformTypeIdentifiers

struct BridgeZipDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.zip] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct RecorderView: View {
    @EnvironmentObject var runtime: BridgeRuntime
    @AppStorage("developerMode") private var developer = false
    @State private var note = ""
    @State private var notes = false
    @State private var showMarkers = false
    @State private var timeline: String?
    @State private var exportDocument: BridgeZipDocument?
    @State private var exporting = false
    var body: some View {
        Form {
            Section("On-device recorder") {
                if let run = runtime.current { Label("Recording · \(run.events) events · \(run.bytes / 1024) KiB", systemImage: "record.circle.fill").foregroundStyle(.red).accessibilityIdentifier("recorder-status") }
                else { Label("Ready to record", systemImage: "record.circle").accessibilityIdentifier("recorder-status") }
                Text("App metadata, phone state and private observations. No raw packets, messages, GPS or Garage records are collected.").font(.callout).foregroundStyle(.secondary)
                Button("Start recording") { runtime.startRecording() }.disabled(runtime.current != nil).accessibilityIdentifier("recorder-start")
                Button("Mark car event") { showMarkers = true }.disabled(runtime.current == nil)
                Button("Add private observation") { notes = true }.disabled(runtime.current == nil)
                Button("Stop and save") { runtime.stopRecording() }.disabled(runtime.current == nil).accessibilityIdentifier("recorder-stop")
                Text("Up to 10 minutes or 1 MiB per run; the newest 12 are retained. On iOS, leaving or locking the app saves the run instead of claiming continuous background recording.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Saved recordings") {
                if runtime.recordings.isEmpty { Text("Your recordings will appear here.").foregroundStyle(.secondary) }
                ForEach(runtime.recordings) { run in
                    Button { timeline = runtime.timeline(run.id) } label: {
                        VStack(alignment: .leading) { Text(Date(timeIntervalSince1970: Double(run.startedAt) / 1000).formatted(date: .abbreviated, time: .shortened)); Text(run.status.replacingOccurrences(of: "_", with: " ")).font(.caption).foregroundStyle(.secondary) }
                    }.swipeActions { Button("Delete", role: .destructive) { runtime.delete(run.id) }.disabled(runtime.current?.id == run.id) }
                }
                Button("Export private diagnostics ZIP") { do { exportDocument = BridgeZipDocument(data: try runtime.report()); exporting = true } catch { runtime.issue = error.localizedDescription } }.accessibilityIdentifier("recorder-export")
            }
            Section("Phone checks") {
                Button("Run phone self-check") { runtime.selfCheck() }.accessibilityIdentifier("phone-self-check")
                Text(runtime.checkResult).font(.callout).accessibilityIdentifier("phone-self-check-result")
            }
            Section { Toggle("Show technical details", isOn: $developer) }
            if developer {
                Section("Connection") {
                    Text(runtime.accessory.status).font(.callout)
                    Button("Inspect connected accessories") { runtime.accessory.inspect() }
                    Text("\(runtime.recordings.count) stored runs. Local HTTP preview: \(runtime.previewURL == nil ? "stopped" : "ready").").font(.caption.monospaced())
                    Text("Build 0.57 · shared Rust transport and maintenance engine. Android RFCOMM, general notification access and HCI capture are not ordinary iOS capabilities.").font(.caption)
                }
            }
            if let issue = runtime.issue { Section { Text(issue).foregroundStyle(.red) } }
        }.navigationTitle("Record & diagnose").task { runtime.refresh() }
            .alert("Private observation", isPresented: $notes) { TextField("What happened? (240 characters)", text: $note); Button("Cancel", role: .cancel) {}; Button("Save") { runtime.mark("note", note: note); note = "" } }
            .confirmationDialog("What happened?", isPresented: $showMarkers) {
                ForEach(Array(zip(["Car Bluetooth connected", "Opened STARLINK", "Display visible", "Touch confirmed", "Error appeared", "Disconnected", "Reconnected"], ["car_connected", "starlink_opened", "display_visible", "touch_received", "error_seen", "disconnected", "reconnected"])), id: \.1) { label, key in Button(label) { runtime.mark(key) } }
            }
            .sheet(isPresented: Binding(get: { timeline != nil }, set: { if !$0 { timeline = nil } })) { NavigationStack { ScrollView { Text(timeline ?? "").font(.caption.monospaced()).textSelection(.enabled).padding() }.navigationTitle("Private event timeline").toolbar { Button("Done") { timeline = nil } } } }
            .fileExporter(isPresented: $exporting, document: exportDocument, contentType: .zip, defaultFilename: "SubaruBridge-private-diagnostics") { result in if case let .failure(error) = result { runtime.issue = error.localizedDescription } }
    }
}
