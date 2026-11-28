import Foundation

public struct StarlinkPhoneSnapshot {
    public let batteryPercent: Int?
    public let wallTimeMs: UInt64
    public let uptimeMs: UInt64
    public var clock: String {
        let formatter = DateFormatter(); formatter.dateFormat = "h:mm a"; formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter.string(from: Date(timeIntervalSince1970: Double(wallTimeMs) / 1000))
    }
    public init(batteryPercent: Int?, wallTimeMs: UInt64, uptimeMs: UInt64) {
        self.batteryPercent = batteryPercent.flatMap { (0...100).contains($0) ? $0 : nil }; self.wallTimeMs = wallTimeMs; self.uptimeMs = uptimeMs
    }
}

public struct StarlinkHTTPError: Error {
    public let status: Int
    public let message: String
}

public struct StarlinkHTTPResponse {
    public let status: Int
    public let body: Data
    public let wire: Data
    public let close: Bool
    init(status: Int, type: String, body: Data, close: Bool) {
        self.status = status; self.body = body; self.close = close
        let reason = [200:"OK",400:"Bad Request",403:"Forbidden",404:"Not Found",405:"Method Not Allowed",409:"Conflict",413:"Content Too Large",429:"Too Many Requests",500:"Internal Server Error",507:"Insufficient Storage"][status] ?? "Error"
        var bytes = Data("HTTP/1.1 \(status) \(reason)\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\nConnection: \(close ? "close" : "keep-alive")\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n".utf8)
        bytes.append(body); wire = bytes
    }
}

private struct HeadUnitRequest {
    let method: String
    let path: String
    let query: [String: String]
    let headers: [String: String]
    let body: Data
    let close: Bool
    init(_ bytes: Data) throws {
        guard bytes.count <= 12_288, let range = bytes.range(of: Data([13,10,13,10])), range.lowerBound <= 8192,
              let head = String(data: bytes[..<range.lowerBound], encoding: .utf8) else { throw StarlinkHTTPError(status: 400, message: "Invalid request") }
        let lines = head.components(separatedBy: "\r\n"), first = lines[0].split(separator: " ", omittingEmptySubsequences: false)
        guard first.count == 3, ["HTTP/1.0", "HTTP/1.1"].contains(String(first[2])),
              first[0].utf8.allSatisfy({ (65...90).contains($0) }), !first[0].isEmpty else { throw StarlinkHTTPError(status: 400, message: "Invalid request line") }
        method = String(first[0])
        let target = String(first[1])
        guard target.hasPrefix("/"), !target.hasPrefix("//"), !target.contains("#"),
              target.utf8.allSatisfy({ $0 > 32 && $0 < 127 }) else { throw StarlinkHTTPError(status: 400, message: "Local request target required") }
        let parts = target.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)
        guard let decoded = String(parts[0]).removingPercentEncoding, !decoded.contains(".."),
              !decoded.contains("\\"), decoded.utf8.allSatisfy({ $0 >= 32 && $0 != 127 }) else { throw StarlinkHTTPError(status: 400, message: "Invalid local path") }
        path = decoded; query = try StarlinkHTTPServer.fields(parts.count == 2 ? String(parts[1]) : "")
        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            guard let colon = line.firstIndex(of: ":"), !line.hasPrefix(" "), !line.hasPrefix("\t") else { throw StarlinkHTTPError(status: 400, message: "Invalid header") }
            let key = String(line[..<colon]).lowercased(), value = String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
            guard !key.isEmpty, key.utf8.allSatisfy({ (97...122).contains($0) || (48...57).contains($0) || $0 == 45 }), headers[key] == nil,
                  !value.utf8.contains(where: { $0 < 32 || $0 == 127 }) else { throw StarlinkHTTPError(status: 400, message: "Duplicate or invalid header") }
            headers[key] = value
        }
        guard headers["transfer-encoding"] == nil else { throw StarlinkHTTPError(status: 400, message: "Transfer encoding unsupported") }
        body = Data(bytes[range.upperBound...])
        guard body.count <= 4096 else { throw StarlinkHTTPError(status: 413, message: "Body limit") }
        if let length = headers["content-length"] {
            guard !length.isEmpty, length.utf8.allSatisfy({ (48...57).contains($0) }), Int(length) == body.count else { throw StarlinkHTTPError(status: 400, message: "Invalid content length") }
        } else if !body.isEmpty { throw StarlinkHTTPError(status: 400, message: "Content length required") }
        self.headers = headers
        let tokens = (headers["connection"] ?? "").lowercased().split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        close = tokens.contains("close") || first[2] == "HTTP/1.0" && !tokens.contains("keep-alive")
    }
    func require(_ methods: [String]) throws {
        guard methods.contains(method) else { throw StarlinkHTTPError(status: 405, message: "Method unsupported") }
    }
    func form(limit: Int) throws -> [String: String] {
        guard !body.isEmpty, body.count <= limit else { throw StarlinkHTTPError(status: 400, message: "Bounded form required") }
        guard headers["content-type"]?.split(separator: ";").first?.trimmingCharacters(in: .whitespaces).lowercased() == "application/x-www-form-urlencoded",
              let text = String(data: body, encoding: .utf8) else { throw StarlinkHTTPError(status: 400, message: "Form encoding required") }
        return try StarlinkHTTPServer.fields(text)
    }
}

/// Original local HTTP routes behind the shared packet engine. No public network
/// listener, external URLs, media credentials, raw capture or vehicle commands.
public final class StarlinkHTTPServer {
    public static let entry = "/smartaccess/launcher/subarubridge-demo.html"
    public static let build = "0.57-ios"
    public var observed: ((String, String, String, Int, Int) -> Void)?
    private let configuration: DashboardConfiguration
    private let lock = NSRecursiveLock()
    private let phone: () -> StarlinkPhoneSnapshot
    private let save: (Data) throws -> Void
    private let saveAppearance: (String, String) throws -> Void
    private var sessions: [String: DiagnosticSession] = [:]
    private var order: [String] = []
    public private(set) var theme: String
    public private(set) var layout: String
    public private(set) var requestCount = 0
    public private(set) var savedCount = 0

    public init(theme: String = "midnight", layout: String = "grid", phone: @escaping () -> StarlinkPhoneSnapshot,
                save: @escaping (Data) throws -> Void = { _ in }, saveAppearance: @escaping (String, String) throws -> Void = { _,_ in }) throws {
        configuration = try DashboardConfiguration.bundled()
        self.theme = configuration.themes[theme] == nil ? configuration.defaultTheme : theme
        self.layout = ["grid", "list"].contains(layout) ? layout : "grid"
        self.phone = phone; self.save = save; self.saveAppearance = saveAppearance
    }
    private var ui: HeadUnitRenderer { HeadUnitRenderer(configuration: configuration, theme: theme, layout: layout) }
    public static func fields(_ text: String) throws -> [String: String] {
        guard text.utf8.count <= 4096 else { throw StarlinkHTTPError(status: 413, message: "Field limit") }
        if text.isEmpty { return [:] }
        var result: [String: String] = [:]
        for item in text.split(separator: "&", omittingEmptySubsequences: false) {
            let pair = item.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard pair.count == 2, !pair[0].isEmpty, result.count < 16,
                  let key = String(pair[0]).replacingOccurrences(of: "+", with: " ").removingPercentEncoding,
                  let value = String(pair[1]).replacingOccurrences(of: "+", with: " ").removingPercentEncoding,
                  !key.isEmpty, !key.utf8.contains(where: { $0 < 32 || $0 == 127 }), !value.utf8.contains(where: { $0 < 32 || $0 == 127 }), result[key] == nil else { throw StarlinkHTTPError(status: 400, message: "Malformed fields") }
            result[key] = value
        }
        return result
    }
    private func run(_ value: String?) throws -> String? {
        guard let value else { return nil }
        guard (1...64).contains(value.utf8.count), value.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 45 || $0 == 95 }) else { throw StarlinkHTTPError(status: 400, message: "Invalid run") }
        return value
    }
    private func response(_ text: String, type: String = "text/html; charset=utf-8", status: Int = 200, close: Bool) -> StarlinkHTTPResponse {
        StarlinkHTTPResponse(status: status, type: type, body: Data(text.utf8), close: close)
    }
    private func json(_ value: [String: Any], status: Int = 200, close: Bool) -> StarlinkHTTPResponse {
        let bytes = (try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])) ?? Data("{}".utf8)
        return StarlinkHTTPResponse(status: status, type: "application/json; charset=utf-8", body: bytes, close: close)
    }
    private func snapshot(_ session: DiagnosticSession, run: String, sample: StarlinkPhoneSnapshot) -> [String: Any] {
        ["schema":1, "build":Self.build, "run":run, "touches":session.touches, "screensSeen":session.screensSeen,
         "dataChecks":session.dataReads, "summarySaved":session.summarySaved, "complete":session.complete,
         "nextSeq":session.nextSequence, "batteryPercent":sample.batteryPercent ?? -1, "serverTimeMs":sample.wallTimeMs,
         "uptimeMs":sample.uptimeMs, "scope":"Requests received by this app; physical connection type must be verified separately"]
    }
    public func handle(_ raw: Data, source: String = "local") -> StarlinkHTTPResponse {
        let result = dispatch(raw)
        // Record categories only: query strings, unknown paths and form contents may be private.
        let request = try? HeadUnitRequest(raw)
        let path = request?.path ?? "invalid"
        let category = path.hasPrefix(HeadUnitRenderer.prefix) ? "dashboard" :
            ["/", Self.entry, "/version", "/version/", "/timestamp/", "/kvs/launcher_url_vehicle/"].contains(path) ? "bootstrap" : "other"
        observed?(["preview", "selfcheck", "accessory"].contains(source) ? source : "local",
                  ["GET", "POST"].contains(request?.method ?? "") ? request!.method : "other", category, result.status, result.body.count)
        return result
    }
    private func dispatch(_ raw: Data) -> StarlinkHTTPResponse {
        lock.lock(); defer { lock.unlock() }
        requestCount += 1
        do {
            let request = try HeadUnitRequest(raw), prefix = HeadUnitRenderer.prefix
            let sample = phone()
            if ["/", Self.entry, prefix + "dashboard"].contains(request.path) {
                try request.require(request.path == prefix + "dashboard" ? ["GET"] : ["GET", "POST"])
                return response(try ui.home(phone: sample), close: request.close)
            }
            if request.path == "/kvs/launcher_url_vehicle/" {
                try request.require(["GET"]); return response("http://192.168.1.1" + Self.entry, type: "text/plain; charset=utf-8", close: request.close)
            }
            if request.path == "/timestamp/" {
                try request.require(["GET"]); let format = DateFormatter(); format.locale = Locale(identifier: "en_US_POSIX"); format.dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ"
                return response(format.string(from: Date(timeIntervalSince1970: Double(sample.wallTimeMs) / 1000)), type: "text/plain; charset=utf-8", close: request.close)
            }
            if ["/version", "/version/"].contains(request.path) {
                try request.require(["GET"]); return json(["product":"SubaruBridge", "build":Self.build, "transport":"original-rust-apple-accessory", "hardwareVerified":false], close: request.close)
            }
            if request.path == prefix + "pattern.png" {
                try request.require(["GET"]); return StarlinkHTTPResponse(status: 200, type: "image/png", body: try HeadUnitRenderer.asset("pattern.png"), close: request.close)
            }
            if request.path == prefix + "settings" {
                try request.require(["GET", "POST"])
                if request.method == "POST" {
                    let fields = try request.form(limit: 512)
                    guard fields.count == 2, let theme = fields["theme"], configuration.themes[theme] != nil,
                          let layout = fields["layout"], ["grid", "list"].contains(layout) else { throw StarlinkHTTPError(status: 400, message: "Unknown appearance choice") }
                    do { try saveAppearance(theme, layout) } catch { throw StarlinkHTTPError(status: 507, message: "Appearance could not be saved") }
                    self.theme = theme; self.layout = layout
                }
                return response(try ui.settings(notice: request.method == "POST" ? "Saved on your iPhone." : "Choose the head-unit appearance."), close: request.close)
            }
            if request.path == prefix + "status" {
                try request.require(["GET"])
                guard let name = try run(request.query["run"]) else { throw StarlinkHTTPError(status: 400, message: "Run required") }
                return json(snapshot(sessions[name] ?? DiagnosticSession(), run: name, sample: sample), close: request.close)
            }
            if request.path == prefix + "basic" { return try basic(request, sample: sample) }
            if request.path.hasPrefix(prefix + "apps/") {
                try request.require(["GET"]); return response(try ui.app(String(request.path.dropFirst((prefix + "apps/").count))), close: request.close)
            }
            if request.path == prefix + "developer" {
                try request.require(["GET"])
                var values = ui.urls(run: nil); values["state"] = HeadUnitRenderer.escape("Build: \(Self.build)\nHTTP requests: \(requestCount)\nSaved results: \(savedCount)\nApple accessory stream. Physical Bluetooth / Subaru verification remains separate.")
                let body = try ui.template("developer", values).replacingOccurrences(of: "SubaruBridge 0.52-dev", with: "SubaruBridge " + Self.build)
                return response(try ui.frame("Tools", body: body, selected: "developer"), close: request.close)
            }
            throw StarlinkHTTPError(status: 404, message: "Unknown local route")
        } catch let error as StarlinkHTTPError { return json(["error":error.message, "build":Self.build], status: error.status, close: true) }
        catch { return json(["error":"Local handler unavailable", "build":Self.build], status: 500, close: true) }
    }
    private func basic(_ request: HeadUnitRequest, sample: StarlinkPhoneSnapshot) throws -> StarlinkHTTPResponse {
        try request.require(["GET", "POST"])
        let name: String, screen: Int
        var session: DiagnosticSession
        if request.method == "POST" {
            let fields = try request.form(limit: 1024)
            guard fields.count == 5, fields["ok"] == "1", let validRun = try run(fields["run"]),
                  let seqText = fields["seq"], let sequence = Int(seqText), seqText == String(sequence),
                  let stageText = fields["screen"], let index = Int(stageText), stageText == String(index), let stage = CheckStage(rawValue: index),
                  let actionText = fields["action"], ["touch", "data", "summary"].contains(actionText), let action = ObservationAction(rawValue: actionText) else { throw StarlinkHTTPError(status: 400, message: "Invalid action form") }
            name = validRun; screen = index
            guard let prior = sessions[name] else { throw StarlinkHTTPError(status: 409, message: "Open a connection check first") }
            session = prior
            do {
                let changed = try session.accept(Observation(sequence: sequence, stage: stage, action: action))
                if action == .summary && changed {
                    var state = snapshot(session, run: name, sample: sample); state["recordedAt"] = sample.wallTimeMs
                    do { try save(JSONSerialization.data(withJSONObject: state, options: [.sortedKeys])); savedCount += 1 }
                    catch { throw StarlinkHTTPError(status: 507, message: "Result could not be saved on iPhone") }
                }
            } catch let error as StarlinkHTTPError { throw error }
            catch SessionFailure.conflictingRetry { throw StarlinkHTTPError(status: 409, message: "Sequence already used") }
            catch SessionFailure.limitReached { throw StarlinkHTTPError(status: 429, message: "Run event limit") }
            catch { throw StarlinkHTTPError(status: 400, message: "Invalid check event") }
        } else {
            name = try run(request.query["run"]) ?? "ios-" + UUID().uuidString.lowercased()
            let stageText = request.query["screen"] ?? "1"
            guard let index = Int(stageText), String(index) == stageText, let stage = CheckStage(rawValue: index) else { throw StarlinkHTTPError(status: 400, message: "Invalid screen") }
            screen = index; session = sessions[name] ?? DiagnosticSession()
            do { try session.accept(Observation(sequence: session.nextSequence, stage: stage, action: .page)) }
            catch { throw StarlinkHTTPError(status: 429, message: "Run event limit") }
        }
        if sessions[name] == nil {
            if order.count == 8 { sessions.removeValue(forKey: order.removeFirst()) }
            order.append(name)
        }
        sessions[name] = session
        var form = ""
        if screen > 1 {
            let action = screen == 2 ? "touch" : screen == 3 ? "data" : "summary"
            let title = screen == 2 ? "Send touch to iPhone" : screen == 3 ? "Read live iPhone data" : "Save result on iPhone"
            form = "<form method=\"post\" action=\"\(HeadUnitRenderer.prefix)basic\">"
            for (key, value) in [("run", name), ("seq", String(session.nextSequence)), ("screen", String(screen)), ("action", action), ("ok", "1")] {
                form += "<input type=\"hidden\" name=\"\(key)\" value=\"\(HeadUnitRenderer.escape(value))\">"
            }
            form += "<button type=\"submit\" class=\"primary-button\">\(title)</button></form>"
        }
        return response(try ui.basic(run: name, screen: screen, session: session, phone: sample, form: form), close: request.close)
    }
}
