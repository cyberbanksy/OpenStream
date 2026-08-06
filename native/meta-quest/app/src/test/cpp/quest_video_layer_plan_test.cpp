#include "quest_video_layer_plan.hpp"

#include <cassert>

using openstream::quest::LayerKind;
using openstream::quest::RuntimeLayerExtensions;
using openstream::quest::makeVideoLayerPlan;
using openstream::quest::supportsCompositorPlayback;
using openstream::quest::videoPixelRectForEye;
using openstream::xr::Eye;
using openstream::xr::MediaSpec;
using openstream::xr::PresentationPlan;
using openstream::xr::Projection;
using openstream::xr::StereoLayout;

int main() {
    PresentationPlan presentation;
    MediaSpec media;

    media.projection = Projection::Flat;
    assert(makeVideoLayerPlan(media, presentation, {}).kind == LayerKind::Quad);

    media.projection = Projection::Equirectangular180;
    assert(makeVideoLayerPlan(media, presentation, {}).kind == LayerKind::ProjectionMesh);
    assert(makeVideoLayerPlan(media, presentation, {.equirectangular = true}).kind ==
        LayerKind::Equirectangular);

    media.projection = Projection::Equirectangular360;
    assert(makeVideoLayerPlan(media, presentation, {.equirectangular = true}).kind ==
        LayerKind::Equirectangular);

    media.projection = Projection::Cubemap360;
    assert(makeVideoLayerPlan(media, presentation, {}).kind == LayerKind::ProjectionMesh);
    assert(makeVideoLayerPlan(media, presentation, {.cube = true}).kind == LayerKind::Cube);

    media.projection = Projection::Fisheye180;
    assert(makeVideoLayerPlan(media, presentation, {.equirectangular = true, .cube = true}).kind ==
        LayerKind::ProjectionMesh);

    media.projection = Projection::WideFieldOfView;
    const auto widePlan = makeVideoLayerPlan(media, presentation, {});
    assert(widePlan.kind == LayerKind::ProjectionMesh);
    assert(!supportsCompositorPlayback(widePlan, StereoLayout::Mono));

    media.projection = Projection::Flat;
    const auto flatPlan = makeVideoLayerPlan(media, presentation, {});
    assert(supportsCompositorPlayback(flatPlan, StereoLayout::Mono));
    assert(!supportsCompositorPlayback(flatPlan, StereoLayout::Multiview));

    const auto mono = videoPixelRectForEye(1920, 1080, StereoLayout::Mono, Eye::Left);
    assert(mono.x == 0 && mono.y == 0 && mono.width == 1920 && mono.height == 1080);
    const auto sbsLeft = videoPixelRectForEye(3841, 1920, StereoLayout::SideBySide, Eye::Left);
    const auto sbsRight = videoPixelRectForEye(3841, 1920, StereoLayout::SideBySide, Eye::Right);
    assert(sbsLeft.x == 0 && sbsLeft.width == 1920);
    assert(sbsRight.x == 1920 && sbsRight.width == 1921);
    const auto top = videoPixelRectForEye(3840, 1921, StereoLayout::TopBottom, Eye::Left);
    const auto bottom = videoPixelRectForEye(3840, 1921, StereoLayout::TopBottom, Eye::Right);
    assert(top.y == 0 && top.height == 960);
    assert(bottom.y == 960 && bottom.height == 961);
}
