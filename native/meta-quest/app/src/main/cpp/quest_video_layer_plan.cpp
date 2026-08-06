#include "quest_video_layer_plan.hpp"

namespace openstream::quest {

VideoLayerPlan makeVideoLayerPlan(
    const xr::MediaSpec& media,
    const xr::PresentationPlan& presentation,
    const RuntimeLayerExtensions& extensions
) {
    LayerKind kind = LayerKind::ProjectionMesh;
    switch (media.projection) {
        case xr::Projection::Flat:
            kind = LayerKind::Quad;
            break;
        case xr::Projection::Equirectangular180:
        case xr::Projection::Equirectangular360:
            kind = extensions.equirectangular
                ? LayerKind::Equirectangular
                : LayerKind::ProjectionMesh;
            break;
        case xr::Projection::Cubemap360:
            kind = extensions.cube ? LayerKind::Cube : LayerKind::ProjectionMesh;
            break;
        case xr::Projection::Fisheye180:
        case xr::Projection::WideFieldOfView:
            kind = LayerKind::ProjectionMesh;
            break;
    }
    return {.kind = kind, .presentation = presentation};
}

}  // namespace openstream::quest
