#pragma once

#include "immersive_video_session.hpp"

#include <cstdint>
#include <optional>
#include <string>

namespace openstream::quest {

struct LaunchMetadata {
    std::string projection{};
    std::string stereo{};
    std::string codec{};
    std::string dynamicRange{};
    std::string audio{};
    std::uint32_t width = 0;
    std::uint32_t height = 0;
    double durationSeconds = 0.0;
    bool live = false;
};

struct LaunchMedia {
    std::string uri;
    xr::MediaSpec spec;
};

[[nodiscard]] std::optional<LaunchMedia> makeLaunchMedia(
    std::string uri,
    const LaunchMetadata& metadata
);

}  // namespace openstream::quest
