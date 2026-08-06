#include "quest_video_layer_plan.hpp"

#include <algorithm>

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
            kind = extensions.equirectangular
                ? LayerKind::Equirectangular
                : (extensions.cube ? LayerKind::Cube : LayerKind::ProjectionMesh);
            break;
        case xr::Projection::Fisheye180:
            kind = extensions.equirectangular
                ? LayerKind::Equirectangular
                : LayerKind::ProjectionMesh;
            break;
        case xr::Projection::WideFieldOfView:
            kind = LayerKind::Quad;
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

VideoPixelExtent compositorSwapchainExtent(const xr::MediaSpec& media) {
    if (media.projection != xr::Projection::Fisheye180 &&
        media.projection != xr::Projection::Cubemap360) {
        return {.width = media.decodedWidth, .height = media.decodedHeight};
    }
    const std::uint32_t eyeWidth = 2048;
    const std::uint32_t eyeHeight = media.projection == xr::Projection::Fisheye180
        ? 2048
        : 1024;
    return {
        .width = media.stereo == xr::StereoLayout::SideBySide ? eyeWidth * 2 : eyeWidth,
        .height = media.stereo == xr::StereoLayout::TopBottom ? eyeHeight * 2 : eyeHeight,
    };
}

float compositorQuadWidth(const xr::MediaSpec& media) {
    const float sourceWidth = static_cast<float>(media.decodedWidth) /
        (media.stereo == xr::StereoLayout::SideBySide ? 2.0F : 1.0F);
    const float sourceHeight = static_cast<float>(media.decodedHeight) /
        (media.stereo == xr::StereoLayout::TopBottom ? 2.0F : 1.0F);
    const float aspect = sourceWidth / std::max(sourceHeight, 1.0F);
    const float projectionScale = media.projection == xr::Projection::WideFieldOfView
        ? 1.65F
        : 1.0F;
    return std::clamp(2.0F * aspect * projectionScale, 2.0F, 4.5F);
}

}  // namespace openstream::quest
