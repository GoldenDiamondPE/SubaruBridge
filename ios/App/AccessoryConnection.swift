import Foundation
import ExternalAccessory
import SubaruBridgeCore

/// Observes only what iOS exposes. Android's RFCOMM framing has not been verified
/// on this head unit's iPhone interface, so inspecting an accessory never sends bytes.
@MainActor final class AccessoryConnection: ObservableObject {
    @Published private(set) var status = "Direct Subaru connection requires accessory verification."
    var router: StarlinkHTTPServer?
    var event: ((String, [String: JSONValue]) -> Void)?
    private var observers: [NSObjectProtocol] = []
    init() {
        let center = NotificationCenter.default
        for name in [Notification.Name.EAAccessoryDidConnect, .EAAccessoryDidDisconnect] {
            observers.append(center.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.inspect() }
            })
        }
        EAAccessoryManager.shared().registerForLocalNotifications()
    }
    deinit { for observer in observers { NotificationCenter.default.removeObserver(observer) } }
    func inspect() {
        let accessories = EAAccessoryManager.shared().connectedAccessories
        let declared = Bundle.main.object(forInfoDictionaryKey: "UISupportedExternalAccessoryProtocols") as? [String] ?? []
        if accessories.isEmpty { status = "No app-accessible accessory reported. Pairing for audio does not establish an app data channel." }
        else {
            let eligible = accessories.contains { if case .ready = AccessoryPolicy.eligibility(declared: declared, advertised: $0.protocolStrings) { return true }; return false }
            status = eligible ? "An advertised protocol matches this app. Its stream framing still needs physical verification before data is sent." : "Accessory detected. No verified app protocol is configured for this Subaru."
        }
        event?("accessory_inventory", ["count": .integer(Int64(accessories.count)), "configuredProtocols": .integer(Int64(declared.count)), "scope": .string("Read-only iOS inventory; no connection or protocol exchange established")])
    }
    func stop() { router = nil }
}
