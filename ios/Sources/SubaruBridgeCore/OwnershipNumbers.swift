import Foundation

public enum OwnershipNumbers {
    public static let millimetersPerMile: Int64 = 1_609_344
    public static func decimal(_ text: String, places: Int, locale: Locale = .current) throws -> Decimal {
        let separator = locale.decimalSeparator ?? "."
        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleaned.utf8.count <= 32 else { throw OwnershipError.engine("The number is too long") }
        let canonical = separator == "." ? cleaned : cleaned.replacingOccurrences(of: separator, with: ".")
        let pattern = places == 0 ? #"^[0-9]+$"# : "^[0-9]+(?:\\.[0-9]{1,\(places)})?$"
        guard canonical.range(of: pattern, options: .regularExpression) != nil,
              let number = Decimal(string: canonical, locale: Locale(identifier: "en_US_POSIX")) else { throw OwnershipError.engine("Enter a positive number without grouping separators") }
        return number
    }
    private static func integer(_ value: Decimal, maximum: Int64) throws -> Int64 {
        var input = value, rounded = Decimal(); NSDecimalRound(&rounded, &input, 0, .plain)
        guard rounded >= 0, rounded <= Decimal(maximum) else { throw OwnershipError.engine("The number is outside the supported range") }
        return NSDecimalNumber(decimal: rounded).int64Value
    }
    public static func distance(_ text: String, metric: Bool, locale: Locale = .current) throws -> Int64 {
        try integer(decimal(text, places: 3, locale: locale) * Decimal(metric ? 1_000_000 : millimetersPerMile), maximum: 3_000_000 * millimetersPerMile)
    }
    public static func money(_ text: String, locale: Locale = .current) throws -> Int64 {
        try integer(decimal(text, places: 2, locale: locale) * 100, maximum: 100_000_000_000)
    }
    public static func volume(_ text: String, metric: Bool, locale: Locale = .current) throws -> Int64 {
        try integer(decimal(text, places: 3, locale: locale) * (metric ? 1000 : Decimal(string: "3785.411784")!), maximum: 2_000_000)
    }
    public static func displayDistance(_ value: Int64?, metric: Bool) -> String {
        guard let value else { return "Unknown" }
        return (Double(value) / (metric ? 1_000_000 : Double(millimetersPerMile))).formatted(.number.precision(.fractionLength(0...1))) + (metric ? " km" : " mi")
    }
    public static func editDistance(_ value: Int64?, metric: Bool, locale: Locale = .current) -> String {
        guard let value else { return "" }; let formatter = NumberFormatter(); formatter.locale = locale
        formatter.numberStyle = .decimal; formatter.usesGroupingSeparator = false; formatter.maximumFractionDigits = 3
        return formatter.string(from: NSDecimalNumber(decimal: Decimal(value) / Decimal(metric ? 1_000_000 : millimetersPerMile))) ?? ""
    }
    public static func editMoney(_ value: Int64, locale: Locale = .current) -> String {
        let formatter = NumberFormatter(); formatter.locale = locale; formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false; formatter.minimumFractionDigits = 2; formatter.maximumFractionDigits = 2
        return formatter.string(from: NSDecimalNumber(decimal: Decimal(value) / 100)) ?? ""
    }
    public static func displayMoney(_ value: Int64, currency: String) -> String {
        (Double(value) / 100).formatted(.currency(code: currency))
    }
}
