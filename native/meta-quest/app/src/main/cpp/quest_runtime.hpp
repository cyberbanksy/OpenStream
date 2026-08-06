#pragma once

#include "immersive_video_session.hpp"
#include "quest_video_layer_plan.hpp"

#include <EGL/egl.h>
#include <jni.h>
#include <openxr/openxr.h>

#include <optional>

struct android_app;

namespace openstream::quest {

class QuestRuntime {
public:
    QuestRuntime() = default;
    QuestRuntime(const QuestRuntime&) = delete;
    QuestRuntime& operator=(const QuestRuntime&) = delete;
    ~QuestRuntime();

    [[nodiscard]] bool initialize(android_app* app, JNIEnv* environment);
    void pollEvents();
    void renderFrame();
    void shutdown();

    [[nodiscard]] bool initialized() const { return instance_ != XR_NULL_HANDLE; }
    [[nodiscard]] bool sessionRunning() const { return sessionRunning_; }
    [[nodiscard]] bool exitRequested() const { return exitRequested_; }
    [[nodiscard]] const xr::RuntimeCapabilities& capabilities() const { return capabilities_; }

    [[nodiscard]] std::optional<VideoLayerPlan> prepareVideo(const xr::MediaSpec& media);

private:
    [[nodiscard]] bool createInstance(android_app* app);
    [[nodiscard]] bool createGraphicsSession();
    [[nodiscard]] bool createEglContext();
    void destroyEglContext();
    void populateDecoderCapabilities(JNIEnv* environment);
    void handleSessionState(XrSessionState state);

    XrInstance instance_ = XR_NULL_HANDLE;
    XrSystemId systemId_ = XR_NULL_SYSTEM_ID;
    XrSession session_ = XR_NULL_HANDLE;
    XrSpace localSpace_ = XR_NULL_HANDLE;
    XrSessionState sessionState_ = XR_SESSION_STATE_UNKNOWN;
    bool sessionRunning_ = false;
    bool exitRequested_ = false;

    EGLDisplay eglDisplay_ = EGL_NO_DISPLAY;
    EGLConfig eglConfig_ = nullptr;
    EGLContext eglContext_ = EGL_NO_CONTEXT;
    EGLSurface eglSurface_ = EGL_NO_SURFACE;

    xr::RuntimeCapabilities capabilities_;
    RuntimeLayerExtensions layerExtensions_;
    xr::ImmersiveVideoSession videoSession_;
};

}  // namespace openstream::quest
