import SwiftUI

@main struct SubaruBridgeApp: App {
    @UIApplicationDelegateAdaptor(BasicAppDelegate.self) private var appDelegate
    var body: some Scene { WindowGroup { DashboardView() } }
}
