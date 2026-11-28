import SwiftUI
import UserNotifications
import SubaruBridgeCore

@MainActor enum GarageNotifications {
    static func update(_ snapshot: JSONValue) async {
        let center = UNUserNotificationCenter.current()
        let old = await center.pendingNotificationRequests()
        center.removePendingNotificationRequests(withIdentifiers: old.filter { $0.identifier == "garage-maintenance-summary" || $0.identifier.hasPrefix("garage-date-") }.map(\.identifier))
        guard UserDefaults.standard.bool(forKey: "garageReminders") else { return }
        let settings = await center.notificationSettings()
        guard [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus) else { return }
        let active = Set(snapshot["state"]["vehicles"].list.filter { !$0["archived"].flag }.compactMap { $0["id"].text })
        let items = snapshot["summaries"].list.filter { active.contains($0["vehicleId"].text ?? "") }.flatMap { $0["maintenance"].list }
        let attention = items.filter { ["dueSoon", "due", "overdue", "inspectionRecommended"].contains($0["status"].text ?? "") && $0["reminderSnoozedUntil"] == .null }
        if !attention.isEmpty {
            let content = UNMutableNotificationContent(); content.title = "Vehicle maintenance"
            content.body = "\(attention.count) recorded maintenance \(attention.count == 1 ? "item needs" : "items need") attention. Open Garage to review."
            var date = DateComponents(); date.hour = 9; date.minute = 0
            try? await center.add(UNNotificationRequest(identifier: "garage-maintenance-summary", content: content, trigger: UNCalendarNotificationTrigger(dateMatching: date, repeats: true)))
        }
        // Date-based thresholds can be scheduled without background execution or new mileage.
        var remaining = 40
        for item in items where remaining > 0 && item["status"].text != "notApplicable" && item["reminderSnoozedUntil"] == .null {
            guard let date = item["nextDate"].text, date > OwnershipCore.date(Date()) else { continue }
            let parts = date.split(separator: "-").compactMap { Int($0) }; guard parts.count == 3 else { continue }
            var when = DateComponents(); when.year = parts[0]; when.month = parts[1]; when.day = parts[2]; when.hour = 9
            guard let due = Calendar.current.date(from: when) else { continue }
            let lead = Int(snapshot["state"]["preferences"]["dueSoonDays"].int ?? 30)
            if let early = Calendar.current.date(byAdding: .day, value: -lead, to: due), early > Date() {
                let content = UNMutableNotificationContent(); content.title = "Upcoming vehicle maintenance"; content.body = "A saved maintenance date is approaching. Open Garage to review."
                try? await center.add(UNNotificationRequest(identifier: "garage-date-" + UUID().uuidString, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: early), repeats: false))); remaining -= 1
            }
            guard remaining > 0 else { break }
            let content = UNMutableNotificationContent(); content.title = "Vehicle maintenance"; content.body = "A saved maintenance date is due. Open Garage to review."
            try? await center.add(UNNotificationRequest(identifier: "garage-date-" + UUID().uuidString, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: false))); remaining -= 1
        }
    }
}

struct GarageReminderSettings: View {
    @EnvironmentObject var model: GarageModel
    @AppStorage("garageReminders") private var enabled = false
    @State private var issue: String?
    var body: some View {
        Form {
            Toggle("Local maintenance reminders", isOn: $enabled).accessibilityIdentifier("garage-reminders-enabled")
            Text("Due items use your saved mileage, service history, lead times and snoozes. Calendar reminders can arrive while the app is closed. Mileage cannot update itself without new readings or trips.").font(.callout).foregroundStyle(.secondary)
            Text("iOS controls delivery through notification permissions, Focus and power settings. Review Garage regularly; unknown history is not treated as completed or confirmed overdue.").font(.caption)
            if let issue { Text(issue).foregroundStyle(.red) }
        }.navigationTitle("Maintenance reminders").onChange(of: enabled) { _, value in Task {
            if value { do { let allowed = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]); if !allowed { issue = "Enable notifications in iPhone Settings to receive reminders." } } catch { issue = error.localizedDescription } }
            await GarageNotifications.update(model.snapshot)
        } }
    }
}
