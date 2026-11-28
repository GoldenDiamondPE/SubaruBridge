import SwiftUI
import MapKit
import MediaPlayer
import MessageUI
import SubaruBridgeCore

struct DashboardView: View {
    var body: some View {
        TabView {
            AppHub().tabItem { Label("Home", systemImage: "house") }
            NavigationStack { GarageView() }.tabItem { Label("Garage", systemImage: "car.side") }
            NavigationStack { HeadUnitPreview() }.tabItem { Label("Dashboard", systemImage: "display") }
            ConnectionLabView().tabItem { Label("Tools", systemImage: "wrench.and.screwdriver") }
        }.tint(.blue)
    }
}

struct AppHub: View {
    @EnvironmentObject private var garage: GarageModel
    private enum FavoriteField: Hashable { case number, destination, reply }
    @FocusState private var favoriteFocus: FavoriteField?
    @AppStorage("favoriteNumber") private var number = ""
    @AppStorage("favoriteDestination") private var destination = ""
    @AppStorage("quickReply") private var quickReply = "I'm on my way."
    @AppStorage("navigationFirst") private var navigationFirst = false
    @Environment(\.openURL) private var openURL
    @State private var composer = false
    @State private var issue: String?
    @State private var configuration: DashboardConfiguration?
    private var orderedApps: [DashboardConfiguration.App] {
        var apps = configuration?.apps ?? []
        if navigationFirst, let index = apps.firstIndex(where: { $0.id == "navigation" }) { apps.insert(apps.remove(at: index), at: 0) }
        return apps
    }
    private func symbol(_ id: String) -> String { ["music":"music.note", "navigation":"location.fill", "calls":"phone.fill", "messages":"message.fill", "notifications":"bell.fill"][id] ?? "square" }
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Your phone. Your Subaru.").font(.title2.bold())
                        Label("SubaruBridge 0.57", systemImage: "iphone").foregroundStyle(.blue).accessibilityIdentifier("unified-home")
                        Text("Your Garage, phone apps and dashboard together. Vehicle records work offline.")
                            .font(.callout).foregroundStyle(.secondary)
                    }.padding(.vertical, 8)
                }
                Section("Your workspace") {
                    NavigationLink { GarageView() } label: { Label("Garage & maintenance", systemImage: "car.side") }.accessibilityIdentifier("workspace-garage")
                    NavigationLink { HeadUnitPreview() } label: { Label("Preview your dashboard", systemImage: "display") }.accessibilityIdentifier("workspace-dashboard")
                    NavigationLink { RecorderView() } label: { Label("Record & diagnose", systemImage: "waveform.path.ecg") }.accessibilityIdentifier("workspace-recorder")
                    if garage.vehicle != .null { LabeledContent(garage.vehicle["nickname"].text ?? "Your vehicle", value: garage.distance(garage.summary["mileage"]["currentMm"].int)) }
                }
                Section("Your apps") {
                    ForEach(orderedApps) { app in
                        NavigationLink { appPage(app.id) } label: { Label(app.title, systemImage: symbol(app.id)) }.accessibilityIdentifier("app-" + app.id)
                    }
                }
                Section("Favorites") {
                    TextField("Phone number", text: $number).keyboardType(.phonePad).focused($favoriteFocus, equals: .number).accessibilityIdentifier("favorite-number")
                    TextField("Google Maps destination", text: $destination).focused($favoriteFocus, equals: .destination).accessibilityIdentifier("favorite-destination")
                    TextField("Quick reply", text: $quickReply).focused($favoriteFocus, equals: .reply).accessibilityIdentifier("favorite-reply")
                    Toggle("Navigation first", isOn: $navigationFirst)
                }
                Section("Widgets") {
                    TimelineView(.periodic(from: .now, by: 60)) { context in
                        LabeledContent("Phone time", value: context.date.formatted(date: .omitted, time: .shortened))
                    }
                    LabeledContent("Connection", value: "Local phone only")
                }
            }.navigationTitle(configuration?.brand ?? "SubaruBridge")
                .scrollDismissesKeyboard(.interactively)
                .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("Done") { favoriteFocus = nil } } }
                .task { do { configuration = try DashboardConfiguration.bundled() } catch { issue = "Dashboard configuration could not be loaded." } }
                .sheet(isPresented: $composer) { MessageComposer(number: number, text: quickReply) }
                .alert("Phone action", isPresented: Binding(get: { issue != nil }, set: { if !$0 { issue = nil } })) {
                    Button("OK") { issue = nil }
                } message: { Text(issue ?? "") }
        }
    }
    @ViewBuilder private func appPage(_ id: String) -> some View {
        switch id {
        case "music": NativeMusicView()
        case "navigation":
            Form {
                Section("Google Maps") {
                    Text(destination.isEmpty ? "Save a destination in Favorites first." : destination)
                    Button("Open directions on iPhone") {
                        do { openURL(try CompanionActions.mapsURL(destination: destination)) { accepted in if !accepted { issue = "No app could open these directions." } } }
                        catch { issue = "Enter a destination up to 160 characters." }
                    }.disabled(destination.isEmpty)
                }
                Text("Google Maps opens on this iPhone. Directions are not being relayed to the Subaru.").foregroundStyle(.secondary)
            }.navigationTitle("Maps")
        case "calls":
            Form {
                Text(number.isEmpty ? "Save a number in Favorites first." : number)
                Button("Open iPhone calling prompt") {
                    do { openURL(try CompanionActions.dialURL(number)) { accepted in if !accepted { issue = "Calling is unavailable on this device." } } }
                    catch { issue = "Enter a valid favorite phone number." }
                }.disabled(!CompanionActions.validPhoneNumber(number))
                Text("Call audio and answering use normal iPhone and Subaru hands-free controls.").foregroundStyle(.secondary)
            }.navigationTitle("Phone")
        case "notifications": LocalNotificationsView()
        default:
            Form {
                Text("Messages stay in the system composer. This app cannot read the iPhone's system-wide message inbox.").foregroundStyle(.secondary)
                Button("Compose message") {
                    guard MFMessageComposeViewController.canSendText() else { issue = "SMS composition is unavailable on this device."; return }
                    composer = true
                }.disabled(!CompanionActions.validPhoneNumber(number))
                Text("Review the recipient and text, then choose Send or Cancel in the iPhone composer.")
            }.navigationTitle("Messages")
        }
    }
}

private struct MessageComposer: UIViewControllerRepresentable {
    let number: String, text: String
    @Environment(\.dismiss) private var dismiss
    func makeCoordinator() -> Coordinator { Coordinator(dismiss: { dismiss() }) }
    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let controller = MFMessageComposeViewController(); controller.recipients = [number]; controller.body = text
        controller.messageComposeDelegate = context.coordinator; return controller
    }
    func updateUIViewController(_ controller: MFMessageComposeViewController, context: Context) {}
    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        let dismiss: () -> Void
        init(dismiss: @escaping () -> Void) { self.dismiss = dismiss }
        func messageComposeViewController(_ controller: MFMessageComposeViewController, didFinishWith result: MessageComposeResult) { dismiss() }
    }
}

@MainActor private final class LibraryPlayer: ObservableObject {
    let player = MPMusicPlayerController.applicationMusicPlayer
    @Published var title = "Choose music on this iPhone"
    @Published var artist = ""
    @Published var playing = false
    @Published var hasTrack = false
    init() {
        player.beginGeneratingPlaybackNotifications()
        NotificationCenter.default.addObserver(self, selector: #selector(update), name: .MPMusicPlayerControllerNowPlayingItemDidChange, object: player)
        NotificationCenter.default.addObserver(self, selector: #selector(update), name: .MPMusicPlayerControllerPlaybackStateDidChange, object: player)
        update()
    }
    deinit {
        NotificationCenter.default.removeObserver(self)
        player.endGeneratingPlaybackNotifications()
    }
    @objc func update() {
        guard MPMediaLibrary.authorizationStatus() == .authorized else {
            title = "Choose music on this iPhone"; artist = ""; playing = false; hasTrack = false; return
        }
        title = player.nowPlayingItem?.title ?? "Choose music on this iPhone"; artist = player.nowPlayingItem?.artist ?? ""
        playing = player.playbackState == .playing; hasTrack = player.nowPlayingItem != nil
    }
    func choose(_ items: MPMediaItemCollection) { player.setQueue(with: items); player.play(); update() }
}

private struct NativeMusicView: View {
    @StateObject private var music = LibraryPlayer()
    @Environment(\.openURL) private var openURL
    @State private var picker = false
    @State private var issue: String?
    var body: some View {
        Form {
            Section("Apple Music library player") {
                Text(music.title).font(.headline); Text(music.artist).foregroundStyle(.secondary)
                Button("Choose library music") {
                    MPMediaLibrary.requestAuthorization { status in
                        DispatchQueue.main.async { if status == .authorized { music.update(); picker = true } else { issue = "Music library access was not granted." } }
                    }
                }
                HStack {
                    Button("Previous") { music.player.skipToPreviousItem() }
                    Button(music.playing ? "Pause" : "Play") { if music.playing { music.player.pause() } else { music.player.play() }; music.update() }
                    Button("Next") { music.player.skipToNextItem() }
                }.buttonStyle(.bordered).disabled(!music.hasTrack)
            }
            Section("Spotify") {
                Button("Open Spotify on iPhone") { openURL(URL(string: "https://open.spotify.com/")!) }
                Text("Controlling Spotify from this companion needs a separate authorized Spotify integration. The controls above operate this app's library player.").font(.callout).foregroundStyle(.secondary)
            }
        }.navigationTitle("Music")
            .sheet(isPresented: $picker) { LibraryPicker { items in if let items { music.choose(items) }; picker = false } }
            .alert("Music access", isPresented: Binding(get: { issue != nil }, set: { if !$0 { issue = nil } })) { Button("OK") { issue = nil } } message: { Text(issue ?? "") }
    }
}

private struct LibraryPicker: UIViewControllerRepresentable {
    let selected: (MPMediaItemCollection?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(selected) }
    func makeUIViewController(context: Context) -> MPMediaPickerController {
        let controller = MPMediaPickerController(mediaTypes: .music); controller.allowsPickingMultipleItems = true; controller.delegate = context.coordinator; return controller
    }
    func updateUIViewController(_ controller: MPMediaPickerController, context: Context) {}
    final class Coordinator: NSObject, MPMediaPickerControllerDelegate {
        let selected: (MPMediaItemCollection?) -> Void
        init(_ selected: @escaping (MPMediaItemCollection?) -> Void) { self.selected = selected }
        func mediaPicker(_ mediaPicker: MPMediaPickerController, didPickMediaItems mediaItemCollection: MPMediaItemCollection) { selected(mediaItemCollection) }
        func mediaPickerDidCancel(_ mediaPicker: MPMediaPickerController) { selected(nil) }
    }
}
