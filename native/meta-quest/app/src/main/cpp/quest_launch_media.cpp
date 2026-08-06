#include "quest_launch_media.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <string_view>

namespace openstream::quest {
namespace {

std::string normalize(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char character) {
        if (character == '_' || character == '-' || character == '.') return ' ';
        return static_cast<char>(std::tolower(character));
    });
    return value;
}

bool contains(std::string_view value, std::string_view needle) {
    return value.find(needle) != std::string_view::npos;
}

xr::Projection projectionFrom(std::string_view value) {
    if (contains(value, "cubemap") || contains(value, "cube map") || contains(value, "eac")) {
        return xr::Projection::Cubemap360;
    }
    if (contains(value, "fisheye") || contains(value, "fish eye") || contains(value, "vr180")) {
        return xr::Projection::Fisheye180;
    }
    if (contains(value, "180") || contains(value, "hemisphere")) {
        return xr::Projection::Equirectangular180;
    }
    if (contains(value, "360") || contains(value, "equirect") || contains(value, "spherical")) {
        return xr::Projection::Equirectangular360;
    }
    if (contains(value, "wide fov") || contains(value, "wide field") ||
        contains(value, "panorama")) {
        return xr::Projection::WideFieldOfView;
    }
    return xr::Projection::Flat;
}

xr::StereoLayout stereoFrom(std::string_view value) {
    if (contains(value, "mv hevc") || contains(value, "multiview") ||
        contains(value, "spatial hevc")) {
        return xr::StereoLayout::Multiview;
    }
    if (contains(value, "top bottom") || contains(value, "over under") ||
        contains(value, "tb3d") || contains(value, "ou3d")) {
        return xr::StereoLayout::TopBottom;
    }
    if (contains(value, "side by side") || contains(value, "sbs") ||
        contains(value, "lr3d") || contains(value, "rl3d")) {
        return xr::StereoLayout::SideBySide;
    }
    return xr::StereoLayout::Mono;
}

xr::VideoCodec codecFrom(std::string_view value) {
    if (contains(value, "hevc") || contains(value, "h265") || contains(value, "h 265")) {
        return xr::VideoCodec::HEVC;
    }
    if (contains(value, "av1") || contains(value, "av01")) return xr::VideoCodec::AV1;
    if (contains(value, "vp9") || contains(value, "vp09")) return xr::VideoCodec::VP9;
    return xr::VideoCodec::H264;
}

xr::DynamicRange dynamicRangeFrom(std::string_view value) {
    if (contains(value, "dolby vision") || contains(value, "dovi")) {
        return xr::DynamicRange::DolbyVision;
    }
    if (contains(value, "hdr10+") || contains(value, "hdr10 plus")) {
        return xr::DynamicRange::HDR10Plus;
    }
    if (contains(value, "hlg")) return xr::DynamicRange::HLG;
    if (contains(value, "hdr10") || contains(value, "hdr")) return xr::DynamicRange::HDR10;
    return xr::DynamicRange::SDR;
}

xr::AudioMode audioFrom(std::string_view value) {
    if (contains(value, "atmos") || contains(value, "eac3 joc")) return xr::AudioMode::DolbyAtmos;
    if (contains(value, "spatial") || contains(value, "ambisonic")) {
        return xr::AudioMode::Spatial;
    }
    return xr::AudioMode::Standard;
}

std::pair<std::uint32_t, std::uint32_t> defaultDimensions(xr::Projection projection) {
    switch (projection) {
        case xr::Projection::Equirectangular360:
            return {4096, 2048};
        case xr::Projection::Equirectangular180:
        case xr::Projection::Fisheye180:
            return {3840, 1920};
        case xr::Projection::Cubemap360:
            return {3840, 2160};
        case xr::Projection::WideFieldOfView:
            return {2560, 1080};
        case xr::Projection::Flat:
            return {1920, 1080};
    }
    return {1920, 1080};
}

}  // namespace

std::optional<LaunchMedia> makeLaunchMedia(
    std::string uri,
    const LaunchMetadata& metadata
) {
    if (uri.empty() || !std::isfinite(metadata.durationSeconds) || metadata.durationSeconds < 0.0) {
        return std::nullopt;
    }

    const std::string projectionSignal = normalize(uri + " " + metadata.projection);
    const std::string stereoSignal = normalize(uri + " " + metadata.stereo);
    const xr::Projection projection = projectionFrom(projectionSignal);
    const auto [defaultWidth, defaultHeight] = defaultDimensions(projection);
    const bool live = metadata.durationSeconds > 0.0 ? metadata.live : true;
    return LaunchMedia{
        .uri = std::move(uri),
        .spec = {
            .projection = projection,
            .stereo = stereoFrom(stereoSignal),
            .codec = codecFrom(normalize(metadata.codec)),
            // Dynamic range is deliberately explicit; never infer HDR from a filename.
            .dynamicRange = dynamicRangeFrom(normalize(metadata.dynamicRange)),
            .audio = audioFrom(normalize(metadata.audio)),
            .decodedWidth = metadata.width == 0 ? defaultWidth : metadata.width,
            .decodedHeight = metadata.height == 0 ? defaultHeight : metadata.height,
            .durationSeconds = metadata.durationSeconds,
            .live = live,
        },
    };
}

}  // namespace openstream::quest
