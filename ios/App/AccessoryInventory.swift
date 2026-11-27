import ExternalAccessory
import Foundation
import Combine

struct AccessorySummary: Identifiable {
    let id: Int
    let name: String
    let model: String
    let protocols: [String]
}

@MainActor final class AccessoryInventory: ObservableObject {
    @Published private(set) var accessories: [AccessorySummary] = []
    @Published private(set) var inspected = false
    func inspect() {
        accessories = EAAccessoryManager.shared().connectedAccessories.map {
            AccessorySummary(id: $0.connectionID, name: $0.name, model: $0.modelNumber, protocols: $0.protocolStrings)
        }
        inspected = true
    }
}
