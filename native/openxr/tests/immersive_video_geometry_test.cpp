#include "immersive_video_geometry.hpp"

#include <cassert>
#include <cmath>

using namespace openstream::xr;

int main() {
    const auto sphere = makeImmersiveVideoMesh(
        Projection::Equirectangular360, StereoLayout::Mono, Eye::Left, 8, 16
    );
    assert(sphere.vertices.size() == 9 * 17);
    assert(sphere.indices.size() == 8 * 16 * 6);
    for (const auto& vertex : sphere.vertices) {
        assert(vertex.u >= 0.0F && vertex.u <= 1.0F);
        assert(vertex.v >= 0.0F && vertex.v <= 1.0F);
        const float length = std::sqrt(vertex.x * vertex.x + vertex.y * vertex.y + vertex.z * vertex.z);
        assert(std::abs(length - 1.0F) < 0.0001F);
    }

    const auto left = makeImmersiveVideoMesh(
        Projection::Equirectangular180, StereoLayout::SideBySide, Eye::Left, 6, 12
    );
    const auto right = makeImmersiveVideoMesh(
        Projection::Equirectangular180, StereoLayout::SideBySide, Eye::Right, 6, 12
    );
    for (const auto& vertex : left.vertices) assert(vertex.u <= 0.5F);
    for (const auto& vertex : right.vertices) assert(vertex.u >= 0.5F);

    const auto top = makeImmersiveVideoMesh(
        Projection::Fisheye180, StereoLayout::TopBottom, Eye::Left, 6, 12
    );
    const auto bottom = makeImmersiveVideoMesh(
        Projection::Fisheye180, StereoLayout::TopBottom, Eye::Right, 6, 12
    );
    for (const auto& vertex : top.vertices) assert(vertex.v <= 0.5F);
    for (const auto& vertex : bottom.vertices) assert(vertex.v >= 0.5F);

    const auto cube = makeImmersiveVideoMesh(
        Projection::Cubemap360, StereoLayout::Mono, Eye::Left
    );
    assert(cube.vertices.size() == 24);
    assert(cube.indices.size() == 36);

    const auto flat = makeImmersiveVideoMesh(Projection::Flat, StereoLayout::Mono, Eye::Left);
    assert(flat.vertices.size() == 4);
    assert(flat.indices.size() == 6);
}
