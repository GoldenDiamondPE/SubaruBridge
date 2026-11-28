// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SubaruBridgeCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "SubaruBridgeCore", targets: ["SubaruBridgeCore"])],
    targets: [
        .systemLibrary(name: "CSubaruBridgeCore"),
        .target(name: "SubaruBridgeCore", dependencies: ["CSubaruBridgeCore"], resources: [.process("Resources/dashboard.json"), .copy("Resources/HeadUnit")]),
        .testTarget(name: "SubaruBridgeCoreTests", dependencies: ["SubaruBridgeCore"])
    ]
)
