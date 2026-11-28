import SwiftUI
import WebKit
import Network
import SubaruBridgeCore

/// Loopback-only HTTP adapter: relative links and native HTML forms work with JavaScript off.
final class PreviewServer: @unchecked Sendable {
    private let queue = DispatchQueue(label: "SubaruBridge.loopback")
    private let listener: NWListener
    private let router: StarlinkHTTPServer
    private let ready: @Sendable (Result<URL, Error>) -> Void
    private var clients: [UUID: NWConnection] = [:]
    init(router: StarlinkHTTPServer, ready: @escaping @Sendable (Result<URL, Error>) -> Void) throws {
        self.router = router; self.ready = ready
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
        listener = try NWListener(using: parameters)
    }
    func start() {
        listener.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            if case .ready = state, let port = self.listener.port { self.ready(.success(URL(string: "http://127.0.0.1:\(port.rawValue)/subarubridge-diag/dashboard")!)) }
            if case let .failed(error) = state { self.ready(.failure(error)) }
        }
        listener.newConnectionHandler = { [weak self] client in
            guard let self, self.clients.count < 4 else { client.cancel(); return }
            let id = UUID(); self.clients[id] = client
            let peer = Peer(client: client, router: self.router, queue: self.queue) { [weak self] in self?.clients.removeValue(forKey: id) }
            peer.start()
        }
        listener.start(queue: queue)
    }
    func stop() { queue.async { self.listener.cancel(); for client in self.clients.values { client.cancel() }; self.clients.removeAll() } }
    private final class Peer: @unchecked Sendable {
        let client: NWConnection, router: StarlinkHTTPServer, queue: DispatchQueue
        let closed: () -> Void
        var bytes = Data(), count = 0, finished = false
        var deadline: DispatchWorkItem?
        init(client: NWConnection, router: StarlinkHTTPServer, queue: DispatchQueue, closed: @escaping () -> Void) { self.client = client; self.router = router; self.queue = queue; self.closed = closed }
        func start() { client.start(queue: queue); receive() }
        func end() { guard !finished else { return }; finished = true; deadline?.cancel(); client.cancel(); closed() }
        func receive() {
            guard !finished else { return }; deadline?.cancel()
            let timeout = DispatchWorkItem { [weak self] in self?.end() }; deadline = timeout; queue.asyncAfter(deadline: .now() + 5, execute: timeout)
            client.receive(minimumIncompleteLength: 1, maximumLength: 8192) { [self] data, _, complete, error in
                if let data { bytes.append(data) }; if bytes.count > 24_576 { end(); return }
                if error != nil || complete && bytes.isEmpty { end(); return }; process()
            }
        }
        func process() {
            guard let split = bytes.range(of: Data([13,10,13,10])) else { if bytes.count > 8192 { end() } else { receive() }; return }
            guard split.lowerBound <= 8192, let head = String(data: bytes[..<split.lowerBound], encoding: .utf8) else { end(); return }
            var length = 0, seen = false
            for line in head.components(separatedBy: "\r\n").dropFirst() where line.lowercased().hasPrefix("content-length:") {
                let text = line.dropFirst(15).trimmingCharacters(in: .whitespaces)
                guard !seen, !text.isEmpty, text.utf8.allSatisfy({ (48...57).contains($0) }), let size = Int(text), size <= 4096 else { end(); return }
                length = size; seen = true
            }
            let total = split.upperBound + length
            guard bytes.count >= total else { receive(); return }
            let request = Data(bytes.prefix(total)); bytes = Data(bytes.dropFirst(total)); count += 1
            let response = router.handle(request, source: "preview")
            client.send(content: response.wire, completion: .contentProcessed { [self] error in
                deadline?.cancel()
                if error != nil || response.close || count >= 128 { end() }
                else if !bytes.isEmpty { process() } else { receive() }
            })
        }
    }
}

struct HeadUnitPreview: View {
    @EnvironmentObject var runtime: BridgeRuntime
    var body: some View {
        VStack(spacing: 0) {
            Text("Local preview · JavaScript off · no car connection verified").font(.caption).foregroundStyle(.secondary).padding(10)
            if let url = runtime.previewURL { HeadUnitWebView(url: url).accessibilityIdentifier("headunit-preview") }
            else if let issue = runtime.previewIssue { ContentUnavailableView("Dashboard unavailable", systemImage: "exclamationmark.triangle", description: Text(issue)) }
            else { ProgressView("Preparing your dashboard…").frame(maxHeight: .infinity) }
        }.navigationTitle("Dashboard").navigationBarTitleDisplayMode(.inline).task { runtime.preparePreview() }
    }
}

private struct HeadUnitWebView: UIViewRepresentable {
    let url: URL
    func makeCoordinator() -> Coordinator { Coordinator(url: url) }
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration(); configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        let view = WKWebView(frame: .zero, configuration: configuration); view.navigationDelegate = context.coordinator
        view.isOpaque = false; view.backgroundColor = .systemBackground; view.load(URLRequest(url: url)); return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {}
    final class Coordinator: NSObject, WKNavigationDelegate {
        let url: URL
        init(url: URL) { self.url = url }
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let target = navigationAction.request.url else { decisionHandler(.cancel); return }
            decisionHandler(target.scheme == "http" && target.host == "127.0.0.1" && target.port == url.port && target.user == nil ? .allow : .cancel)
        }
    }
}
