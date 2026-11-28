import SwiftUI

@main struct SubaruBridgeApp: App {
    @UIApplicationDelegateAdaptor(BasicAppDelegate.self) private var appDelegate
    @StateObject private var garage = GarageModel()
    @StateObject private var runtime = BridgeRuntime()
    @Environment(\.scenePhase) private var phase
    var body: some Scene { WindowGroup {
        DashboardView().environmentObject(garage).environmentObject(runtime)
            .task { await garage.refresh() }
            .onChange(of: phase) { _, value in
                if value == .background { runtime.background() }
                if value == .active { Task { await garage.refresh() } }
            }
    } }
}
