#include "immersive_video_session.hpp"

#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace openstream::xr {
namespace {

bool supports(Projection projection, const ProjectionCapabilities& capabilities) {
    switch (projection) {
        case Projection::Flat:
            return capabilities.flat;
        case Projection::Equirectangular180:
            return capabilities.equirectangular180;
        case Projection::Equirectangular360:
            return capabilities.equirectangular360;
        case Projection::Fisheye180:
            return capabilities.fisheye180;
        case Projection::Cubemap360:
            return capabilities.cubemap360;
        case Projection::WideFieldOfView:
            return capabilities.wideFieldOfView;
    }
    return false;
}

bool supports(StereoLayout stereo, const StereoCapabilities& capabilities) {
    switch (stereo) {
        case StereoLayout::Mono:
            return capabilities.mono;
        case StereoLayout::SideBySide:
            return capabilities.sideBySide;
        case StereoLayout::TopBottom:
            return capabilities.topBottom;
        case StereoLayout::Multiview:
            return capabilities.multiview;
    }
    return false;
}

bool supports(VideoCodec codec, const CodecCapabilities& capabilities) {
    switch (codec) {
        case VideoCodec::H264:
            return capabilities.h264;
        case VideoCodec::HEVC:
            return capabilities.hevc;
        case VideoCodec::AV1:
            return capabilities.av1;
        case VideoCodec::VP9:
            return capabilities.vp9;
        case VideoCodec::Unknown:
            return false;
    }
    return false;
}

bool supports(DynamicRange range, const DynamicRangeCapabilities& capabilities) {
    switch (range) {
        case DynamicRange::SDR:
            return capabilities.sdr;
        case DynamicRange::HDR10:
            return capabilities.hdr10;
        case DynamicRange::HDR10Plus:
            return capabilities.hdr10Plus;
        case DynamicRange::HLG:
            return capabilities.hlg;
        case DynamicRange::DolbyVision:
            return capabilities.dolbyVision;
    }
    return false;
}

bool supports(AudioMode audio, const AudioCapabilities& capabilities) {
    switch (audio) {
        case AudioMode::Standard:
            return capabilities.standard;
        case AudioMode::Spatial:
            return capabilities.spatial;
        case AudioMode::DolbyAtmos:
            return capabilities.dolbyAtmos;
    }
    return false;
}

PreparationResult rejected(RejectionReason reason) {
    return {.reason = reason, .plan = nullptr};
}

}  // namespace

PreparationResult ImmersiveVideoSession::prepare(
    const MediaSpec& media,
    const RuntimeCapabilities& capabilities,
    std::uint32_t latitudeSegments,
    std::uint32_t longitudeSegments
) {
    stop();

    if (media.decodedWidth == 0 || media.decodedHeight == 0 ||
        !std::isfinite(media.durationSeconds) || media.durationSeconds < 0.0 ||
        (!media.live && media.durationSeconds == 0.0)) {
        return rejected(RejectionReason::InvalidMedia);
    }
    if (media.decodedWidth > capabilities.maxDecodeWidth ||
        media.decodedHeight > capabilities.maxDecodeHeight) {
        return rejected(RejectionReason::ResolutionExceeded);
    }
    if (!supports(media.projection, capabilities.projection)) {
        return rejected(RejectionReason::ProjectionUnsupported);
    }
    if (!supports(media.stereo, capabilities.stereo)) {
        return rejected(RejectionReason::StereoUnsupported);
    }
    if (!supports(media.codec, capabilities.codec)) {
        return rejected(RejectionReason::CodecUnsupported);
    }
    if (!supports(media.dynamicRange, capabilities.dynamicRange)) {
        return rejected(RejectionReason::DynamicRangeUnsupported);
    }
    if (!supports(media.audio, capabilities.audio)) {
        return rejected(RejectionReason::AudioUnsupported);
    }

    try {
        PresentationPlan presentation{
            .left = {
                .eye = Eye::Left,
                .textureLayer = 0,
                .mesh = makeImmersiveVideoMesh(
                    media.projection, media.stereo, Eye::Left, latitudeSegments, longitudeSegments
                ),
            },
            .right = {
                .eye = Eye::Right,
                .textureLayer = media.stereo == StereoLayout::Multiview ? 1U : 0U,
                .mesh = makeImmersiveVideoMesh(
                    media.projection, media.stereo, Eye::Right, latitudeSegments, longitudeSegments
                ),
            },
            .layeredTexture = media.stereo == StereoLayout::Multiview,
        };
        media_ = media;
        plan_ = std::move(presentation);
        state_ = SessionState::Ready;
        return {.reason = RejectionReason::None, .plan = &plan_.value()};
    } catch (const std::invalid_argument&) {
        stop();
        return rejected(RejectionReason::InvalidMedia);
    }
}

bool ImmersiveVideoSession::play() {
    if (state_ == SessionState::Ended) {
        positionSeconds_ = 0.0;
        state_ = SessionState::Playing;
        return true;
    }
    if (state_ != SessionState::Ready && state_ != SessionState::Paused) return false;
    state_ = SessionState::Playing;
    return true;
}

bool ImmersiveVideoSession::pause() {
    if (state_ != SessionState::Playing) return false;
    state_ = SessionState::Paused;
    return true;
}

bool ImmersiveVideoSession::seek(double positionSeconds) {
    if (!media_.has_value() || media_->live || !std::isfinite(positionSeconds)) return false;
    positionSeconds_ = std::clamp(positionSeconds, 0.0, media_->durationSeconds);
    if (positionSeconds_ >= media_->durationSeconds) {
        state_ = SessionState::Ended;
    } else if (state_ == SessionState::Ended) {
        state_ = SessionState::Paused;
    }
    return true;
}

void ImmersiveVideoSession::advance(double elapsedSeconds) {
    if (state_ != SessionState::Playing || !std::isfinite(elapsedSeconds) || elapsedSeconds <= 0.0) {
        return;
    }
    positionSeconds_ += elapsedSeconds;
    if (!media_->live && positionSeconds_ >= media_->durationSeconds) {
        positionSeconds_ = media_->durationSeconds;
        state_ = SessionState::Ended;
    }
}

void ImmersiveVideoSession::stop() {
    state_ = SessionState::Idle;
    media_.reset();
    plan_.reset();
    positionSeconds_ = 0.0;
}

}  // namespace openstream::xr
