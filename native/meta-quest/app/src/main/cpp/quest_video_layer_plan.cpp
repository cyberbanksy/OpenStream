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

bool supportsCompositorPlayback(const VideoLayerPlan& plan, xr::StereoLayout stereo) {
    return stereo != xr::StereoLayout::Multiview &&
        (plan.kind == LayerKind::Quad || plan.kind == LayerKind::Equirectangular);
}

VideoPixelRect videoPixelRectForEye(
    std::uint32_t width,
    std::uint32_t height,
    xr::StereoLayout stereo,
    xr::Eye eye
) {
    VideoPixelRect result{
        .x = 0,
        .y = 0,
        .width = static_cast<std::int32_t>(width),
        .height = static_cast<std::int32_t>(height),
    };
    if (stereo == xr::StereoLayout::SideBySide) {
        const std::int32_t leftWidth = result.width / 2;
        if (eye == xr::Eye::Left) {
            result.width = leftWidth;
        } else {
            result.x = leftWidth;
            result.width -= leftWidth;
        }
    } else if (stereo == xr::StereoLayout::TopBottom) {
        const std::int32_t topHeight = result.height / 2;
        if (eye == xr::Eye::Left) {
            result.height = topHeight;
        } else {
            result.y = topHeight;
            result.height -= topHeight;
        }
    }
    return result;
}

}  // namespace openstream::quest
