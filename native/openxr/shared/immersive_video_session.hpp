#pragma once

#include "immersive_video_geometry.hpp"

#include <cstdint>
#include <optional>

namespace openstream::xr {

enum class VideoCodec { H264, HEVC, AV1, VP9, Unknown };
enum class DynamicRange { SDR, HDR10, HDR10Plus, HLG, DolbyVision };
enum class AudioMode { Standard, Spatial, DolbyAtmos };

struct ProjectionCapabilities {
    bool flat = false;
    bool equirectangular180 = false;
    bool equirectangular360 = false;
    bool fisheye180 = false;
    bool cubemap360 = false;
    bool wideFieldOfView = false;
};

struct StereoCapabilities {
    bool mono = false;
    bool sideBySide = false;
    bool topBottom = false;
    bool multiview = false;
};

struct CodecCapabilities {
    bool h264 = false;
    bool hevc = false;
    bool av1 = false;
    bool vp9 = false;
};

struct DynamicRangeCapabilities {
    bool sdr = false;
    bool hdr10 = false;
    bool hdr10Plus = false;
    bool hlg = false;
    bool dolbyVision = false;
};

struct AudioCapabilities {
    bool standard = false;
    bool spatial = false;
    bool dolbyAtmos = false;
};

struct RuntimeCapabilities {
    std::uint32_t maxDecodeWidth = 0;
    std::uint32_t maxDecodeHeight = 0;
    ProjectionCapabilities projection;
    StereoCapabilities stereo;
    CodecCapabilities codec;
    DynamicRangeCapabilities dynamicRange;
    AudioCapabilities audio;
};

struct MediaSpec {
    Projection projection = Projection::Flat;
    StereoLayout stereo = StereoLayout::Mono;
    VideoCodec codec = VideoCodec::Unknown;
    DynamicRange dynamicRange = DynamicRange::SDR;
    AudioMode audio = AudioMode::Standard;
    std::uint32_t decodedWidth = 0;
    std::uint32_t decodedHeight = 0;
    double durationSeconds = 0.0;
    bool live = false;
};

struct EyeBinding {
    Eye eye = Eye::Left;
    std::uint32_t textureLayer = 0;
    Mesh mesh;
};

struct PresentationPlan {
    EyeBinding left;
    EyeBinding right;
    bool layeredTexture = false;
};

enum class RejectionReason {
    None,
    InvalidMedia,
    ResolutionExceeded,
    ProjectionUnsupported,
    StereoUnsupported,
    CodecUnsupported,
    DynamicRangeUnsupported,
    AudioUnsupported,
};

struct PreparationResult {
    RejectionReason reason = RejectionReason::InvalidMedia;
    // Owned by the session and valid until its next prepare() or stop().
    const PresentationPlan* plan = nullptr;

    [[nodiscard]] bool accepted() const {
        return reason == RejectionReason::None && plan != nullptr;
    }
};

enum class SessionState { Idle, Ready, Playing, Paused, Ended };

class ImmersiveVideoSession {
public:
    PreparationResult prepare(
        const MediaSpec& media,
        const RuntimeCapabilities& capabilities,
        std::uint32_t latitudeSegments = 48,
        std::uint32_t longitudeSegments = 96
    );

    [[nodiscard]] bool play();
    [[nodiscard]] bool pause();
    [[nodiscard]] bool seek(double positionSeconds);
    void advance(double elapsedSeconds);
    void stop();

    [[nodiscard]] SessionState state() const { return state_; }
    [[nodiscard]] double positionSeconds() const { return positionSeconds_; }
    [[nodiscard]] const std::optional<PresentationPlan>& plan() const { return plan_; }

private:
    SessionState state_ = SessionState::Idle;
    std::optional<MediaSpec> media_;
    std::optional<PresentationPlan> plan_;
    double positionSeconds_ = 0.0;
};

}  // namespace openstream::xr
