#pragma once

#include <cstdint>
#include <vector>

namespace openstream::xr {

enum class Projection {
    Flat,
    Equirectangular180,
    Equirectangular360,
    Fisheye180,
    Cubemap360,
    WideFieldOfView,
};

enum class StereoLayout { Mono, SideBySide, TopBottom, Multiview };
enum class Eye { Left, Right };

struct Vertex {
    float x;
    float y;
    float z;
    float u;
    float v;
};

struct Mesh {
    std::vector<Vertex> vertices;
    std::vector<std::uint32_t> indices;
};

Mesh makeImmersiveVideoMesh(
    Projection projection,
    StereoLayout stereo,
    Eye eye,
    std::uint32_t latitudeSegments = 48,
    std::uint32_t longitudeSegments = 96
);

}  // namespace openstream::xr
