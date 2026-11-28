import Foundation

struct HeadUnitRenderer {
    let configuration: DashboardConfiguration
    let theme: String
    let layout: String
    static let prefix = "/subarubridge-diag/"
    static func escape(_ value: String) -> String {
        value.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;").replacingOccurrences(of: "'", with: "&#39;")
    }
    static func asset(_ file: String) throws -> Data {
        guard !file.contains(".."), !file.hasPrefix("/"),
              let url = Bundle.module.url(forResource: "HeadUnit", withExtension: nil)?.appendingPathComponent(file) else { throw CocoaError(.fileNoSuchFile) }
        let bytes = try Data(contentsOf: url)
        guard bytes.count <= 65_536 else { throw TransportFailure.inputLimit }
        return bytes
    }
    func text(_ file: String) throws -> String {
        guard let text = String(data: try Self.asset(file), encoding: .utf8) else { throw TransportFailure.malformedBatch }
        return text
    }
    func template(_ name: String, _ values: [String: String]) throws -> String {
        var result = try text("templates/" + name + ".html")
        let regex = try NSRegularExpression(pattern: "\\{\\{([A-Za-z][A-Za-z0-9_]*)\\}\\}")
        let original = result as NSString
        for match in regex.matches(in: result, range: NSRange(location: 0, length: original.length)).reversed() {
            let key = original.substring(with: match.range(at: 1))
            guard let value = values[key], let range = Range(match.range, in: result) else { throw TransportFailure.malformedBatch }
            result.replaceSubrange(range, with: value)
        }
        return result
    }
    func icon(_ name: String) throws -> String {
        guard ["home", "display", "touch", "phone", "results", "settings", "tools", "arrow", "music", "navigation", "calls", "messages", "notifications"].contains(name) else { throw TransportFailure.malformedBatch }
        return try text("icons/" + name + ".svg")
    }
    func url(_ route: String, run: String? = nil) -> String { Self.prefix + route + (run.map { "?run=" + $0 } ?? "") }
    func step(_ number: Int, run: String) -> String { Self.prefix + "basic?run=" + run + "&screen=" + String(number) }
    func urls(run: String?) -> [String: String] {
        ["homeUrl": Self.escape(url("dashboard", run: run)), "settingsUrl": Self.escape(url("settings", run: run)),
         "developerUrl": Self.escape(url("developer", run: run)), "startUrl": Self.escape(Self.prefix + "basic?" + (run.map { "run=" + $0 + "&" } ?? "") + "screen=1")]
    }
    func frame(_ title: String, body: String, selected: String, run: String? = nil) throws -> String {
        var values = urls(run: run)
        let links = [("home", "home", "Home", values["homeUrl"]!), ("test", "results", "Check", values["startUrl"]!), ("settings", "settings", "Appearance", values["settingsUrl"]!)]
        values["dock"] = try links.map { id, symbol, label, href in
            "<a class=\"dock-link\(id == selected ? " current" : "")\" href=\"\(href)\">\(try icon(symbol))<span>\(label)</span></a>"
        }.joined()
        values.merge(["pageTitle": Self.escape(title), "brand": Self.escape(configuration.brand), "theme": theme, "layout": layout,
                      "css": try text("theme-" + theme + ".css"), "body": body, "devCurrent": selected == "developer" ? "current" : "", "toolsIcon": try icon("tools")]) { _, new in new }
        return try template("shell", values)
    }
    func home(phone: StarlinkPhoneSnapshot) throws -> String {
        var values = urls(run: nil)
        values["tiles"] = try configuration.apps.map { app in
            try template("tile", ["id": Self.escape(app.id), "tone": Self.escape(app.tone), "title": Self.escape(app.title), "subtitle": Self.escape(app.subtitle),
                                  "url": url("apps/" + app.id), "icon": try icon(app.id)])
        }.joined()
        values.merge(["eyebrow": "SUBARUBRIDGE · IPHONE", "homeTitle": Self.escape(configuration.homeTitle),
                      "homeSubtitle": "Connection checks and original screens. Media and phone actions remain on the iPhone in this build.",
                      "arrowIcon": try icon("arrow"), "clock": Self.escape(phone.clock), "battery": phone.batteryPercent.map { String($0) + "%" } ?? "Unavailable"]) { _, new in new }
        return try frame("Home", body: template("home", values), selected: "home")
    }
    func basic(run: String, screen: Int, session: DiagnosticSession, phone: StarlinkPhoneSnapshot, form: String) throws -> String {
        var values = urls(run: run)
        let labels = ["Display", "Touch", "iPhone", "Results"]
        values["steps"] = (1...4).map { number in
            "<a class=\"step-link\(number == screen ? " current" : "")\" href=\"\(Self.escape(step(number, run: run)))\">\(number) \(labels[number-1])</a>"
        }.joined()
        values.merge(["title": labels[screen-1], "step": String(screen), "run": Self.escape(run), "touches": String(session.touches), "form": form,
                      "nextUrl": Self.escape(step(2, run: run)), "battery": phone.batteryPercent.map(String.init) ?? "?", "clock": Self.escape(phone.clock),
                      "uptime": String(phone.uptimeMs / 60_000), "screens": String(session.screensSeen), "reads": String(session.dataReads),
                      "saved": String(session.summarySaved), "savedLabel": session.summarySaved ? "Yes" : "No",
                      "resultTitle": session.complete ? "Check complete" : session.summarySaved ? "Saved. More checks remaining" : "Your results",
                      "resultSubtitle": session.summarySaved ? "Saved on the iPhone. This records requests received through this session." : "Save the result after checking the screen and touch input."]) { _, new in new }
        values["panel"] = try template(["display", "touch", "phone", "results"][screen-1], values)
        return try frame(labels[screen-1], body: template("basic", values), selected: "test", run: run)
    }
    func settings(notice: String) throws -> String {
        func choice(theme newTheme: String, layout newLayout: String, title: String, selected: Bool) -> String {
            "<form method=\"post\" action=\"\(url("settings"))\"><input type=\"hidden\" name=\"theme\" value=\"\(Self.escape(newTheme))\"><input type=\"hidden\" name=\"layout\" value=\"\(newLayout)\"><button class=\"choice\(selected ? " selected" : "")\" type=\"submit\">\(Self.escape(title))</button></form>"
        }
        var values = urls(run: nil)
        values["notice"] = Self.escape(notice)
        values["themes"] = configuration.themes.keys.sorted().map { choice(theme: $0, layout: layout, title: configuration.themes[$0]?["label"] ?? $0, selected: $0 == theme) }.joined()
        values["layouts"] = ["grid", "list"].map { choice(theme: theme, layout: $0, title: $0 == "grid" ? "App grid" : "Simple list", selected: $0 == layout) }.joined()
        return try frame("Appearance", body: template("settings", values), selected: "settings")
    }
    func app(_ id: String) throws -> String {
        guard let app = configuration.apps.first(where: { $0.id == id }) else { throw StarlinkHTTPError(status: 404, message: "Unknown local app") }
        var values = urls(run: nil)
        values.merge(["title": Self.escape(app.title), "subtitle": "iPhone companion", "refreshUrl": url("apps/" + id),
                      "content": "<h2>Open on your iPhone</h2><p>This connection build verifies the factory display and input path. \(Self.escape(app.title)) actions are available from the iPhone’s Apps tab; remote head-unit actions are not implemented in this build.</p>"]) { _, new in new }
        return try frame(app.title, body: template("app", values), selected: "home")
    }
}
