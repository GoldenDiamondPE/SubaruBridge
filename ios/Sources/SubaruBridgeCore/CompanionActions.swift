import Foundation

/// Portable validation and URL construction. A prepared URL is not a completed action.
public enum CompanionActionError: Error { case invalidNumber, invalidDestination }

public enum CompanionActions {
    public static func validPhoneNumber(_ value: String) -> Bool {
        value.range(of: #"^\+?[0-9][0-9 ()-]{1,30}[0-9]$"#, options: .regularExpression) != nil
    }
    public static func dialURL(_ number: String) throws -> URL {
        guard validPhoneNumber(number) else { throw CompanionActionError.invalidNumber }
        var url = URLComponents(); url.scheme = "tel"; url.path = number
        guard let result = url.url else { throw CompanionActionError.invalidNumber }; return result
    }
    public static func mapsURL(destination: String) throws -> URL {
        guard !destination.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              destination.count <= 160, !destination.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) })
        else { throw CompanionActionError.invalidDestination }
        var url = URLComponents(string: "https://www.google.com/maps/dir/")!
        url.queryItems = [URLQueryItem(name: "api", value: "1"), URLQueryItem(name: "travelmode", value: "driving"),
                         URLQueryItem(name: "dir_action", value: "navigate"), URLQueryItem(name: "destination", value: destination)]
        return url.url!
    }
}
