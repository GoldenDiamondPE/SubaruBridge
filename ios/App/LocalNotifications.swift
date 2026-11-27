import SwiftUI
import UserNotifications
import UIKit

final class BasicAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}

struct LocalNotificationsView: View {
    @Environment(\.scenePhase) private var phase
    @Environment(\.openURL) private var openURL
    @State private var status: UNAuthorizationStatus = .notDetermined
    @State private var notice = ""
    @State private var busy = false
    private var allowed: Bool { status == .authorized || status == .provisional || status == .ephemeral }
    private var statusText: String {
        switch status { case .authorized, .provisional, .ephemeral: return "Enabled"
        case .denied: return "Off — change this in iPhone Settings"
        default: return "Not requested" }
    }
    var body: some View {
        Form {
            Section("SubaruBridge notifications") {
                LabeledContent("Permission", value: statusText).accessibilityIdentifier("notification-status")
                if status == .notDetermined {
                    Button("Enable notifications") {
                        busy = true
                        Task {
                            do { _ = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) }
                            catch { notice = "Notification permission could not be requested." }
                            await refresh(); busy = false
                        }
                    }.disabled(busy).accessibilityIdentifier("notification-permission")
                } else if status == .denied {
                    Button("Open iPhone Settings") { openURL(URL(string: UIApplication.openSettingsURLString)!) }
                }
                Button("Send a local test in 5 seconds") {
                    busy = true
                    Task {
                        let content = UNMutableNotificationContent(); content.title = "SubaruBridge is ready"
                        content.body = "Local notifications work on this iPhone."; content.sound = .default
                        do {
                            let request = UNNotificationRequest(identifier: "subarubridge-local-test", content: content,
                                trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false))
                            try await UNUserNotificationCenter.current().add(request)
                            notice = "Scheduled on this iPhone. Delivery follows your Focus and notification settings."
                        } catch { notice = "The notification could not be scheduled." }
                        busy = false
                    }
                }.disabled(!allowed || busy).accessibilityIdentifier("local-notification-test")
                if !notice.isEmpty { Text(notice).foregroundStyle(.secondary) }
            }
            Section("Other apps") {
                Text("This iPhone app cannot read other apps' notifications. A relay through the Pixel is a separate feature under investigation.")
                    .foregroundStyle(.secondary)
            }
        }.navigationTitle("Notifications").task { await refresh() }
            .onChange(of: phase) { _, value in if value == .active { Task { await refresh() } } }
    }
    private func refresh() async { status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus }
}
