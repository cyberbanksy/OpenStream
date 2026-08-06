#pragma once

#include "immersive_video_session.hpp"

#include <cstdint>

namespace openstream::quest {

enum class LayerKind {
    Quad,
    Equirectangular,
    Cube,
    ProjectionMesh,
};

struct RuntimeLayerExtensions {
    bool equirectangular = false;
    bool cube = false;
};

struct VideoLayerPlan {
    LayerKind kind = LayerKind::ProjectionMesh;
    xr::PresentationPlan presentation;
};

struct VideoPixelRect {
    std::int32_t x = 0;
    std::int32_t y = 0;
    std::int32_t width = 0;
    std::int32_t height = 0;
};

[[nodiscard]] VideoLayerPlan makeVideoLayerPlan(
    const xr::MediaSpec& media,
    const xr::PresentationPlan& presentation,
    const RuntimeLayerExtensions& extensions
);

[[nodiscard]] bool supportsCompositorPlayback(
    const VideoLayerPlan& plan,
    xr::StereoLayout stereo
);

[[nodiscard]] VideoPixelRect videoPixelRectForEye(
    std::uint32_t width,
    std::uint32_t height,
    xr::StereoLayout stereo,
    xr::Eye eye
);

}  // namespace openstream::quest
