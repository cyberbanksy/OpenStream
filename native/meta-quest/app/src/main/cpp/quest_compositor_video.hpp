#pragma once

#include "immersive_video_session.hpp"
#include "quest_video_layer_plan.hpp"

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <jni.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#include <array>
#include <cstdint>
#include <vector>

namespace openstream::quest {

class QuestCompositorVideo {
public:
    QuestCompositorVideo() = default;
    QuestCompositorVideo(const QuestCompositorVideo&) = delete;
    QuestCompositorVideo& operator=(const QuestCompositorVideo&) = delete;
    ~QuestCompositorVideo();

    [[nodiscard]] bool initialize(
        XrSession session,
        XrSpace space,
        const VideoLayerPlan& plan,
        const xr::MediaSpec& media
    );
    [[nodiscard]] bool renderExternalTexture(
        GLuint externalTexture,
        const std::array<float, 16>& transform
    );
    void shutdown();

    [[nodiscard]] const std::vector<const XrCompositionLayerBaseHeader*>& layers() const {
        return layerHeaders_;
    }
    [[nodiscard]] bool ready() const { return swapchain_ != XR_NULL_HANDLE; }

private:
    [[nodiscard]] bool createSwapchain();
    [[nodiscard]] bool createCopyProgram();
    void configureLayers(const VideoLayerPlan& plan, const xr::MediaSpec& media);
    [[nodiscard]] XrSwapchainSubImage subImageForEye(
        xr::StereoLayout stereo,
        xr::Eye eye
    ) const;

    XrSession session_ = XR_NULL_HANDLE;
    XrSpace space_ = XR_NULL_HANDLE;
    XrSwapchain swapchain_ = XR_NULL_HANDLE;
    std::uint32_t width_ = 0;
    std::uint32_t height_ = 0;
    std::vector<XrSwapchainImageOpenGLESKHR> swapchainImages_;

    GLuint framebuffer_ = 0;
    GLuint copyProgram_ = 0;
    GLint transformUniform_ = -1;

    std::array<XrCompositionLayerQuad, 2> quadLayers_{};
    std::array<XrCompositionLayerEquirect2KHR, 2> equirectLayers_{};
    std::vector<const XrCompositionLayerBaseHeader*> layerHeaders_;
};

}  // namespace openstream::quest
