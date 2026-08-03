import Foundation

public enum OpenStreamDynamicRange: String, Codable, Sendable, CaseIterable {
    case sdr
    case hdr10
    case hdr10Plus
    case hlg
    case dolbyVision
}

public enum OpenStreamDolbyVisionProfile: String, Codable, Sendable {
    case profile5 = "5"
    case profile7 = "7"
    case profile81 = "8.1"
    case profile82 = "8.2"
    case profile84 = "8.4"
    case unknown
}

public enum OpenStreamProjection: String, Codable, Sendable, CaseIterable {
    case flat
    case spatial
    case stereo180
    case mono180
    case stereo360
    case mono360
    case wideFOV
    case appleImmersive
    case unknown
}

public enum OpenStreamSupport: String, Codable, Sendable {
    case supported
    case runtimeCheck
    case unsupported
}

public struct OpenStreamFormat: Codable, Equatable, Sendable {
    public var dynamicRange: OpenStreamDynamicRange
    public var dolbyVisionProfile: OpenStreamDolbyVisionProfile?
    public var projection: OpenStreamProjection
    public var codec: String
    public var width: Int
    public var height: Int
    public var frameRate: Double

    public init(
        dynamicRange: OpenStreamDynamicRange,
        dolbyVisionProfile: OpenStreamDolbyVisionProfile? = nil,
        projection: OpenStreamProjection = .flat,
        codec: String,
        width: Int,
        height: Int,
        frameRate: Double
    ) {
        self.dynamicRange = dynamicRange
        self.dolbyVisionProfile = dolbyVisionProfile
        self.projection = projection
        self.codec = codec
        self.width = width
        self.height = height
        self.frameRate = frameRate
    }
}

public struct OpenStreamCapabilityReport: Codable, Equatable, Sendable {
    public var platform: String
    public var hdrEligible: Bool
    public var dynamicRange: [OpenStreamDynamicRange: OpenStreamSupport]
    public var projections: [OpenStreamProjection: OpenStreamSupport]
    public var notes: [String]

    public init(
        platform: String,
        hdrEligible: Bool,
        dynamicRange: [OpenStreamDynamicRange: OpenStreamSupport],
        projections: [OpenStreamProjection: OpenStreamSupport],
        notes: [String] = []
    ) {
        self.platform = platform
        self.hdrEligible = hdrEligible
        self.dynamicRange = dynamicRange
        self.projections = projections
        self.notes = notes
    }
}

public struct OpenStreamPlaybackDecision: Equatable, Sendable {
    public var support: OpenStreamSupport
    public var reasons: [String]

    public init(support: OpenStreamSupport, reasons: [String]) {
        self.support = support
        self.reasons = reasons
    }
}

public enum OpenStreamCapabilityNegotiator {
    private static let knownVideoCodecs: Set<String> = [
        "av01", "avc1", "avc3", "dvh1", "dvhe", "hvc1", "hev1", "mp4v", "vp09",
        "ap4h", "ap4x", "apch", "apcn", "apco", "apcs",
    ]
    private static let maximumDimension = 32_768
    private static let maximumFrameRate = 480.0

    public static func evaluate(_ format: OpenStreamFormat, against report: OpenStreamCapabilityReport) -> OpenStreamPlaybackDecision {
        let normalizedCodec = format.codec.lowercased()
        guard normalizedCodec.utf8.count == 4,
              format.width > 0,
              format.height > 0,
              format.width <= maximumDimension,
              format.height <= maximumDimension,
              format.frameRate.isFinite,
              format.frameRate > 0,
              format.frameRate <= maximumFrameRate else {
            return OpenStreamPlaybackDecision(
                support: .unsupported,
                reasons: ["The codec, media geometry, or frame rate is invalid or outside the supported decision range."]
            )
        }

        let rangeSupport = report.dynamicRange[format.dynamicRange] ?? .unsupported
        let projectionSupport = report.projections[format.projection] ?? .unsupported
        if rangeSupport == .unsupported || projectionSupport == .unsupported {
            return OpenStreamPlaybackDecision(
                support: .unsupported,
                reasons: ["The current runtime rejected the requested dynamic range or projection."]
            )
        }

        var reasons: [String] = []
        if !knownVideoCodecs.contains(normalizedCodec) {
            reasons.append("The codec requires a runtime decoder check.")
        }
        if rangeSupport == .runtimeCheck { reasons.append("Dynamic range requires an asset and decoder check.") }
        if projectionSupport == .runtimeCheck { reasons.append("Immersive projection requires an AVKit experience check.") }
        if format.dynamicRange == .dolbyVision {
            reasons.append("Dolby Vision profile support is verified with the actual asset; HDR eligibility alone is insufficient.")
        }
        return OpenStreamPlaybackDecision(
            support: reasons.isEmpty ? .supported : .runtimeCheck,
            reasons: reasons
        )
    }
}
