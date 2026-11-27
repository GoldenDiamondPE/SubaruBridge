// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SubaruBridgeCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "SubaruBridgeCore", targets: ["SubaruBridgeCore"])],
    targets: [
        .target(name: "SubaruBridgeCore", resources: [.process("Resources")]),
        .testTarget(name: "SubaruBridgeCoreTests", dependencies: ["SubaruBridgeCore"])
    ]
)
