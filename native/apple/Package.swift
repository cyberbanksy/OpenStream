// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "OpenStreamApple",
    platforms: [
        .iOS(.v18),
        .macOS(.v15),
        .tvOS(.v18),
        .visionOS(.v2),
    ],
    products: [
        .library(name: "OpenStreamApple", targets: ["OpenStreamApple"]),
    ],
    targets: [
        .target(
            name: "OpenStreamApple",
            resources: [.process("Resources")]
        ),
        .testTarget(name: "OpenStreamAppleTests", dependencies: ["OpenStreamApple"]),
    ]
)
