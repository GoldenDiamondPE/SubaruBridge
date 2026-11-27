import Foundation

public enum CheckStage: Int, CaseIterable, Codable, Hashable {
    case display = 1, touch, phone, results
    public var title: String { ["Display", "Touch", "iPhone", "Results"][rawValue - 1] }
}

public enum ObservationAction: String, Codable { case page, touch, data, summary }

public struct Observation: Codable, Equatable {
    public let sequence: Int
    public let stage: CheckStage
    public let action: ObservationAction
    public init(sequence: Int, stage: CheckStage, action: ObservationAction) {
        self.sequence = sequence; self.stage = stage; self.action = action
    }
}

public enum SessionFailure: Error, Equatable { case invalidSequence, wrongStage, conflictingRetry, limitReached }

public struct DiagnosticSession: Codable {
    public private(set) var observations: [Observation] = []
    public init() {}
    public var touches: Int { observations.filter { $0.action == .touch }.count }
    public var dataReads: Int { observations.filter { $0.action == .data }.count }
    public var screensSeen: Int { Set(observations.filter { $0.action == .page }.map(\.stage)).count }
    public var summarySaved: Bool { observations.contains { $0.action == .summary } }
    public var complete: Bool { screensSeen == 4 && touches >= 3 && dataReads > 0 && summarySaved }
    public var nextSequence: Int { (observations.map(\.sequence).max() ?? 0) + 1 }

    /// A local observation is not evidence of a head-unit request or acknowledgement.
    @discardableResult public mutating func accept(_ event: Observation) throws -> Bool {
        guard (1...9999).contains(event.sequence) else { throw SessionFailure.invalidSequence }
        if (event.action == .touch && event.stage != .touch)
            || (event.action == .data && event.stage != .phone)
            || (event.action == .summary && event.stage != .results) { throw SessionFailure.wrongStage }
        if let prior = observations.first(where: { $0.sequence == event.sequence }) {
            guard prior == event else { throw SessionFailure.conflictingRetry }
            return false
        }
        guard observations.count < 128 else { throw SessionFailure.limitReached }
        observations.append(event)
        return true
    }
}
