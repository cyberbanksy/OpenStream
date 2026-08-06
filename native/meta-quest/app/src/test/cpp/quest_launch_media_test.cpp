#include "quest_launch_media.hpp"

#include <cassert>

using openstream::quest::LaunchMetadata;
using openstream::quest::makeLaunchMedia;
using openstream::xr::AudioMode;
using openstream::xr::DynamicRange;
using openstream::xr::Projection;
using openstream::xr::StereoLayout;
using openstream::xr::VideoCodec;

int main() {
    const auto immersive = makeLaunchMedia(
        "https://media.example/movie_360_sbs.mp4",
        LaunchMetadata{.codec = "hevc"}
    );
    assert(immersive.has_value());
    assert(immersive->spec.projection == Projection::Equirectangular360);
    assert(immersive->spec.stereo == StereoLayout::SideBySide);
    assert(immersive->spec.codec == VideoCodec::HEVC);
    assert(immersive->spec.dynamicRange == DynamicRange::SDR);
    assert(immersive->spec.decodedWidth == 4096);
    assert(immersive->spec.decodedHeight == 2048);
    assert(immersive->spec.live);

    const auto explicitHdr = makeLaunchMedia(
        "content://openstream/movie_hdr10.mp4",
        LaunchMetadata{
            .projection = "vr180",
            .stereo = "top-bottom",
            .codec = "av01",
            .dynamicRange = "Dolby Vision",
            .audio = "Dolby Atmos",
            .width = 7680,
            .height = 3840,
            .durationSeconds = 120.0,
        }
    );
    assert(explicitHdr.has_value());
    assert(explicitHdr->spec.projection == Projection::Fisheye180);
    assert(explicitHdr->spec.stereo == StereoLayout::TopBottom);
    assert(explicitHdr->spec.codec == VideoCodec::AV1);
    assert(explicitHdr->spec.dynamicRange == DynamicRange::DolbyVision);
    assert(explicitHdr->spec.audio == AudioMode::DolbyAtmos);
    assert(explicitHdr->spec.decodedWidth == 7680);
    assert(explicitHdr->spec.decodedHeight == 3840);
    assert(!explicitHdr->spec.live);

    const auto filenameDoesNotEnableHdr = makeLaunchMedia(
        "file:///sdcard/video_dolby_vision_hdr10.mp4",
        {}
    );
    assert(filenameDoesNotEnableHdr.has_value());
    assert(filenameDoesNotEnableHdr->spec.dynamicRange == DynamicRange::SDR);

    assert(!makeLaunchMedia("", {}).has_value());
}
