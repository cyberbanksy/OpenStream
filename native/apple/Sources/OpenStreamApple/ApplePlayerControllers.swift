import AVFoundation

#if os(tvOS)
import AVKit

@MainActor
@available(tvOS 18.0, *)
public final class OpenStreamTVPlayerController {
    public let viewController: AVPlayerViewController

    public init(url: URL) {
        let controller = AVPlayerViewController()
        controller.player = AVPlayer(url: url)
        controller.allowsPictureInPicturePlayback = true
        viewController = controller
    }
}
#endif

#if os(visionOS)
import AVKit

@MainActor
@available(visionOS 26.0, *)
public final class OpenStreamVisionPlayerController {
    public let viewController: AVPlayerViewController

    public init(url: URL) {
        let controller = AVPlayerViewController()
        controller.player = AVPlayer(url: url)
        var configuration = controller.experienceController.configuration
        configuration.expanded.automaticTransitionToImmersive = .none
        controller.experienceController.configuration = configuration
        controller.experienceController.allowedExperiences = .recommended(including: [AVExperienceController.Experience.immersive])
        viewController = controller
    }

    @discardableResult
    public func enterImmersive() async -> Bool {
        let controller = viewController.experienceController
        guard controller.availableExperiences.contains(.immersive) else { return false }
        return await controller.transition(to: .immersive) == .completed
    }

    public func leaveImmersive() async {
        _ = await viewController.experienceController.transition(to: .expanded)
    }
}
#endif
