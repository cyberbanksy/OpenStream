#pragma once

#include "immersive_video_session.hpp"

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

[[nodiscard]] VideoLayerPlan makeVideoLayerPlan(
    const xr::MediaSpec& media,
    const xr::PresentationPlan& presentation,
    const RuntimeLayerExtensions& extensions
);

}  // namespace openstream::quest
