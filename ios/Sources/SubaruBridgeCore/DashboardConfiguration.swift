import Foundation

public struct DashboardConfiguration: Decodable {
    public struct App: Decodable, Identifiable {
        public let id: String
        public let title: String
        public let subtitle: String
        public let tone: String
    }
    public struct Tile: Decodable, Identifiable {
        public let id: String
        public let title: String
        public let subtitle: String
        public let tone: String
        public let screen: Int
    }
    public let brand: String
    public let homeTitle: String
    public let homeSubtitle: String
    public let defaultTheme: String
    public let tiles: [Tile]
    public let apps: [App]
    public let themes: [String: [String: String]]
    public static func bundled() throws -> DashboardConfiguration {
        guard let url = Bundle.module.url(forResource: "dashboard", withExtension: "json") else { throw CocoaError(.fileNoSuchFile) }
        return try JSONDecoder().decode(Self.self, from: Data(contentsOf: url))
    }
}
