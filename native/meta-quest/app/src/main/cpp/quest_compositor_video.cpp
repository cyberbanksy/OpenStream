#include "quest_compositor_video.hpp"

#include <GLES2/gl2ext.h>
#include <android/log.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <numbers>
#include <string>

namespace openstream::quest {
namespace {

constexpr const char* kLogTag = "OpenStreamQuest";

void logError(const char* message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message);
}

GLuint compileShader(GLenum type, const char* source) {
    const GLuint shader = glCreateShader(type);
    if (shader == 0) return 0;
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled != GL_TRUE) {
        GLint logLength = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &logLength);
        std::string log(static_cast<std::size_t>(std::max(logLength, 1)), '\0');
        glGetShaderInfoLog(shader, logLength, nullptr, log.data());
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Video shader failed: %s", log.c_str());
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

}  // namespace

QuestCompositorVideo::~QuestCompositorVideo() {
    shutdown();
}

bool QuestCompositorVideo::initialize(
    XrSession session,
    XrSpace space,
    const VideoLayerPlan& plan,
    const xr::MediaSpec& media
) {
    shutdown();
    if (session == XR_NULL_HANDLE || space == XR_NULL_HANDLE || media.decodedWidth == 0 ||
        media.decodedHeight == 0 || !supportsCompositorPlayback(plan, media.stereo)) {
        return false;
    }
    session_ = session;
    space_ = space;
    width_ = media.decodedWidth;
    height_ = media.decodedHeight;
    if (!createSwapchain() || !createCopyProgram()) {
        shutdown();
        return false;
    }
    configureLayers(plan, media);
    return !layerHeaders_.empty();
}

bool QuestCompositorVideo::createSwapchain() {
    std::uint32_t formatCount = 0;
    if (XR_FAILED(xrEnumerateSwapchainFormats(session_, 0, &formatCount, nullptr)) ||
        formatCount == 0) {
        return false;
    }
    std::vector<std::int64_t> formats(formatCount);
    if (XR_FAILED(xrEnumerateSwapchainFormats(
            session_,
            formatCount,
            &formatCount,
            formats.data()))) {
        return false;
    }
    constexpr std::array preferredFormats{
        static_cast<std::int64_t>(GL_SRGB8_ALPHA8),
        static_cast<std::int64_t>(GL_RGBA8),
    };
    std::int64_t selectedFormat = 0;
    for (const std::int64_t preferred : preferredFormats) {
        if (std::find(formats.begin(), formats.end(), preferred) != formats.end()) {
            selectedFormat = preferred;
            break;
        }
    }
    if (selectedFormat == 0) {
        logError("The Quest runtime does not expose an RGBA video-layer swapchain format.");
        return false;
    }

    XrSwapchainCreateInfo createInfo{};
    createInfo.type = XR_TYPE_SWAPCHAIN_CREATE_INFO;
    createInfo.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT |
        XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
    createInfo.format = selectedFormat;
    createInfo.sampleCount = 1;
    createInfo.width = width_;
    createInfo.height = height_;
    createInfo.faceCount = 1;
    createInfo.arraySize = 1;
    createInfo.mipCount = 1;
    if (XR_FAILED(xrCreateSwapchain(session_, &createInfo, &swapchain_))) return false;

    std::uint32_t imageCount = 0;
    if (XR_FAILED(xrEnumerateSwapchainImages(swapchain_, 0, &imageCount, nullptr)) ||
        imageCount == 0) {
        return false;
    }
    swapchainImages_.resize(imageCount);
    for (auto& image : swapchainImages_) image.type = XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR;
    return XR_SUCCEEDED(xrEnumerateSwapchainImages(
        swapchain_,
        imageCount,
        &imageCount,
        reinterpret_cast<XrSwapchainImageBaseHeader*>(swapchainImages_.data())
    ));
}

bool QuestCompositorVideo::createCopyProgram() {
    constexpr const char* vertexSource = R"glsl(#version 300 es
out vec2 videoUv;
const vec2 positions[4] = vec2[4](
    vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(-1.0, 1.0), vec2(1.0, 1.0)
);
const vec2 textureCoordinates[4] = vec2[4](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0)
);
void main() {
    gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
    videoUv = textureCoordinates[gl_VertexID];
}
)glsl";
    constexpr const char* fragmentSource = R"glsl(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
in vec2 videoUv;
layout(location = 0) out vec4 outputColor;
uniform samplerExternalOES videoTexture;
uniform mat4 videoTransform;
void main() {
    vec2 transformedUv = (videoTransform * vec4(videoUv, 0.0, 1.0)).xy;
    outputColor = texture(videoTexture, transformedUv);
}
)glsl";

    const GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
    const GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (vertexShader == 0 || fragmentShader == 0) {
        if (vertexShader != 0) glDeleteShader(vertexShader);
        if (fragmentShader != 0) glDeleteShader(fragmentShader);
        return false;
    }

    copyProgram_ = glCreateProgram();
    glAttachShader(copyProgram_, vertexShader);
    glAttachShader(copyProgram_, fragmentShader);
    glLinkProgram(copyProgram_);
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    GLint linked = GL_FALSE;
    glGetProgramiv(copyProgram_, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE) {
        logError("The Quest video texture copy program could not be linked.");
        return false;
    }
    transformUniform_ = glGetUniformLocation(copyProgram_, "videoTransform");
    const GLint textureUniform = glGetUniformLocation(copyProgram_, "videoTexture");
    glUseProgram(copyProgram_);
    glUniform1i(textureUniform, 0);
    glUseProgram(0);
    glGenFramebuffers(1, &framebuffer_);
    return transformUniform_ >= 0 && textureUniform >= 0 && framebuffer_ != 0;
}

XrSwapchainSubImage QuestCompositorVideo::subImageForEye(
    xr::StereoLayout stereo,
    xr::Eye eye
) const {
    const VideoPixelRect crop = videoPixelRectForEye(width_, height_, stereo, eye);
    const XrRect2Di rectangle{
        .offset = {crop.x, crop.y},
        .extent = {crop.width, crop.height},
    };
    return {
        .swapchain = swapchain_,
        .imageRect = rectangle,
        .imageArrayIndex = 0,
    };
}

void QuestCompositorVideo::configureLayers(
    const VideoLayerPlan& plan,
    const xr::MediaSpec& media
) {
    layerHeaders_.clear();
    const bool stereo = media.stereo != xr::StereoLayout::Mono;
    const std::uint32_t layerCount = stereo ? 2U : 1U;
    const float sourceWidth = static_cast<float>(width_) /
        (media.stereo == xr::StereoLayout::SideBySide ? 2.0F : 1.0F);
    const float sourceHeight = static_cast<float>(height_) /
        (media.stereo == xr::StereoLayout::TopBottom ? 2.0F : 1.0F);
    const float aspect = sourceWidth / std::max(sourceHeight, 1.0F);

    for (std::uint32_t index = 0; index < layerCount; ++index) {
        const xr::Eye eye = index == 0 ? xr::Eye::Left : xr::Eye::Right;
        const XrEyeVisibility visibility = stereo
            ? (eye == xr::Eye::Left ? XR_EYE_VISIBILITY_LEFT : XR_EYE_VISIBILITY_RIGHT)
            : XR_EYE_VISIBILITY_BOTH;
        if (plan.kind == LayerKind::Quad) {
            XrCompositionLayerQuad& layer = quadLayers_[index];
            layer = {};
            layer.type = XR_TYPE_COMPOSITION_LAYER_QUAD;
            layer.space = space_;
            layer.eyeVisibility = visibility;
            layer.subImage = subImageForEye(media.stereo, eye);
            layer.pose.orientation.w = 1.0F;
            layer.pose.position.z = -2.5F;
            layer.size.height = 2.0F;
            layer.size.width = std::clamp(2.0F * aspect, 2.0F, 4.5F);
            layerHeaders_.push_back(
                reinterpret_cast<const XrCompositionLayerBaseHeader*>(&layer)
            );
        } else {
            XrCompositionLayerEquirect2KHR& layer = equirectLayers_[index];
            layer = {};
            layer.type = XR_TYPE_COMPOSITION_LAYER_EQUIRECT2_KHR;
            layer.space = space_;
            layer.eyeVisibility = visibility;
            layer.subImage = subImageForEye(media.stereo, eye);
            layer.pose.orientation.w = 1.0F;
            layer.radius = 10.0F;
            layer.centralHorizontalAngle = media.projection == xr::Projection::Equirectangular180
                ? std::numbers::pi_v<float>
                : 2.0F * std::numbers::pi_v<float>;
            layer.upperVerticalAngle = std::numbers::pi_v<float> / 2.0F;
            layer.lowerVerticalAngle = -std::numbers::pi_v<float> / 2.0F;
            layerHeaders_.push_back(
                reinterpret_cast<const XrCompositionLayerBaseHeader*>(&layer)
            );
        }
    }
}

bool QuestCompositorVideo::renderExternalTexture(
    GLuint externalTexture,
    const std::array<float, 16>& transform
) {
    if (!ready() || externalTexture == 0 || copyProgram_ == 0 || framebuffer_ == 0) return false;

    std::uint32_t imageIndex = 0;
    XrSwapchainImageAcquireInfo acquireInfo{};
    acquireInfo.type = XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO;
    if (XR_FAILED(xrAcquireSwapchainImage(swapchain_, &acquireInfo, &imageIndex))) return false;

    XrSwapchainImageWaitInfo waitInfo{};
    waitInfo.type = XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO;
    waitInfo.timeout = XR_INFINITE_DURATION;
    if (XR_FAILED(xrWaitSwapchainImage(swapchain_, &waitInfo))) {
        XrSwapchainImageReleaseInfo releaseInfo{};
        releaseInfo.type = XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO;
        (void) xrReleaseSwapchainImage(swapchain_, &releaseInfo);
        return false;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER,
        GL_COLOR_ATTACHMENT0,
        GL_TEXTURE_2D,
        swapchainImages_[imageIndex].image,
        0
    );
    const bool framebufferComplete = glCheckFramebufferStatus(GL_FRAMEBUFFER) ==
        GL_FRAMEBUFFER_COMPLETE;
    if (framebufferComplete) {
        glViewport(0, 0, static_cast<GLsizei>(width_), static_cast<GLsizei>(height_));
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glUseProgram(copyProgram_);
        glUniformMatrix4fv(transformUniform_, 1, GL_FALSE, transform.data());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalTexture);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
        glUseProgram(0);
        glFlush();
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    XrSwapchainImageReleaseInfo releaseInfo{};
    releaseInfo.type = XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO;
    const bool released = XR_SUCCEEDED(xrReleaseSwapchainImage(swapchain_, &releaseInfo));
    return framebufferComplete && released && glGetError() == GL_NO_ERROR;
}

void QuestCompositorVideo::shutdown() {
    layerHeaders_.clear();
    if (framebuffer_ != 0) glDeleteFramebuffers(1, &framebuffer_);
    if (copyProgram_ != 0) glDeleteProgram(copyProgram_);
    framebuffer_ = 0;
    copyProgram_ = 0;
    transformUniform_ = -1;
    swapchainImages_.clear();
    if (swapchain_ != XR_NULL_HANDLE) xrDestroySwapchain(swapchain_);
    swapchain_ = XR_NULL_HANDLE;
    session_ = XR_NULL_HANDLE;
    space_ = XR_NULL_HANDLE;
    width_ = 0;
    height_ = 0;
}

}  // namespace openstream::quest
