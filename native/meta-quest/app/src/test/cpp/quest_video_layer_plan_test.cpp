#include "quest_video_layer_plan.hpp"

#include <cassert>

using openstream::quest::LayerKind;
using openstream::quest::RuntimeLayerExtensions;
using openstream::quest::makeVideoLayerPlan;
using openstream::xr::MediaSpec;
using openstream::xr::PresentationPlan;
using openstream::xr::Projection;

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
    assert(makeVideoLayerPlan(media, presentation, {}).kind == LayerKind::ProjectionMesh);
}
