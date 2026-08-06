#include "quest_runtime.hpp"

#include <android/log.h>
#include <android_native_app_glue.h>
#include <openxr/openxr_platform.h>

#include <algorithm>
#include <array>
#include <cstring>
#include <string>
#include <vector>

namespace openstream::quest {
namespace {

constexpr const char* kLogTag = "OpenStreamQuest";

void logError(const char* message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message);
}

void logInfo(const char* message) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s", message);
}

bool hasExtension(const std::vector<XrExtensionProperties>& extensions, const char* name) {
    return std::any_of(extensions.begin(), extensions.end(), [name](const auto& property) {
        return std::strcmp(property.extensionName, name) == 0;
    });
}

bool clearPendingException(JNIEnv* environment) {
    if (!environment->ExceptionCheck()) return false;
    environment->ExceptionClear();
    return true;
}

void deleteLocalReference(JNIEnv* environment, jobject reference) {
    if (reference != nullptr) environment->DeleteLocalRef(reference);
}

bool decoderAvailable(
    JNIEnv* environment,
    const char* mime,
    int width,
    int height
) {
    jclass formatClass = environment->FindClass("android/media/MediaFormat");
    jclass listClass = environment->FindClass("android/media/MediaCodecList");
    const bool classLookupFailed = clearPendingException(environment) ||
        formatClass == nullptr || listClass == nullptr;
    if (classLookupFailed) {
        deleteLocalReference(environment, listClass);
        deleteLocalReference(environment, formatClass);
        return false;
    }

    jmethodID createFormat = environment->GetStaticMethodID(
        formatClass,
        "createVideoFormat",
        "(Ljava/lang/String;II)Landroid/media/MediaFormat;"
    );
    jmethodID listConstructor = environment->GetMethodID(listClass, "<init>", "(I)V");
    jmethodID findDecoder = environment->GetMethodID(
        listClass,
        "findDecoderForFormat",
        "(Landroid/media/MediaFormat;)Ljava/lang/String;"
    );
    const bool methodLookupFailed = clearPendingException(environment) ||
        createFormat == nullptr || listConstructor == nullptr || findDecoder == nullptr;
    if (methodLookupFailed) {
        deleteLocalReference(environment, listClass);
        deleteLocalReference(environment, formatClass);
        return false;
    }

    jstring mimeString = environment->NewStringUTF(mime);
    jobject format = environment->CallStaticObjectMethod(
        formatClass,
        createFormat,
        mimeString,
        width,
        height
    );
    jobject codecList = environment->NewObject(listClass, listConstructor, 1);
    jobject decoderName = nullptr;
    if (!clearPendingException(environment) && format != nullptr && codecList != nullptr) {
        decoderName = environment->CallObjectMethod(codecList, findDecoder, format);
    }
    const bool available = !clearPendingException(environment) && decoderName != nullptr;

    deleteLocalReference(environment, decoderName);
    deleteLocalReference(environment, codecList);
    deleteLocalReference(environment, format);
    deleteLocalReference(environment, mimeString);
    deleteLocalReference(environment, listClass);
    deleteLocalReference(environment, formatClass);
    return available;
}

bool audioDecoderAvailable(JNIEnv* environment, const char* mime) {
    jclass formatClass = environment->FindClass("android/media/MediaFormat");
    jclass listClass = environment->FindClass("android/media/MediaCodecList");
    const bool classLookupFailed = clearPendingException(environment) ||
        formatClass == nullptr || listClass == nullptr;
    if (classLookupFailed) {
        deleteLocalReference(environment, listClass);
        deleteLocalReference(environment, formatClass);
        return false;
    }

    jmethodID createFormat = environment->GetStaticMethodID(
        formatClass,
        "createAudioFormat",
        "(Ljava/lang/String;II)Landroid/media/MediaFormat;"
    );
    jmethodID listConstructor = environment->GetMethodID(listClass, "<init>", "(I)V");
    jmethodID findDecoder = environment->GetMethodID(
        listClass,
        "findDecoderForFormat",
        "(Landroid/media/MediaFormat;)Ljava/lang/String;"
    );
    const bool methodLookupFailed = clearPendingException(environment) ||
        createFormat == nullptr || listConstructor == nullptr || findDecoder == nullptr;
    if (methodLookupFailed) {
        deleteLocalReference(environment, listClass);
        deleteLocalReference(environment, formatClass);
        return false;
    }

    jstring mimeString = environment->NewStringUTF(mime);
    jobject format = environment->CallStaticObjectMethod(formatClass, createFormat, mimeString, 48000, 8);
    jobject codecList = environment->NewObject(listClass, listConstructor, 1);
    jobject decoderName = nullptr;
    if (!clearPendingException(environment) && format != nullptr && codecList != nullptr) {
        decoderName = environment->CallObjectMethod(codecList, findDecoder, format);
    }
    const bool available = !clearPendingException(environment) && decoderName != nullptr;

    deleteLocalReference(environment, decoderName);
    deleteLocalReference(environment, codecList);
    deleteLocalReference(environment, format);
    deleteLocalReference(environment, mimeString);
    deleteLocalReference(environment, listClass);
    deleteLocalReference(environment, formatClass);
    return available;
}

}  // namespace

QuestRuntime::~QuestRuntime() {
    shutdown();
}

bool QuestRuntime::initialize(android_app* app, JNIEnv* environment) {
    if (app == nullptr || environment == nullptr) return false;

    PFN_xrInitializeLoaderKHR initializeLoader = nullptr;
    const XrResult loaderFunctionResult = xrGetInstanceProcAddr(
        XR_NULL_HANDLE,
        "xrInitializeLoaderKHR",
        reinterpret_cast<PFN_xrVoidFunction*>(&initializeLoader)
    );
    if (XR_FAILED(loaderFunctionResult) || initializeLoader == nullptr) {
        logError("The OpenXR Android loader could not be initialized.");
        return false;
    }
    XrLoaderInitInfoAndroidKHR loaderInfo{};
    loaderInfo.type = XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR;
    loaderInfo.applicationVM = app->activity->vm;
    loaderInfo.applicationContext = app->activity->clazz;
    if (XR_FAILED(initializeLoader(reinterpret_cast<const XrLoaderInitInfoBaseHeaderKHR*>(&loaderInfo)))) {
        logError("The active runtime rejected OpenXR loader initialization.");
        return false;
    }
    if (!createInstance(app)) return false;

    XrSystemGetInfo systemInfo{};
    systemInfo.type = XR_TYPE_SYSTEM_GET_INFO;
    systemInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    if (XR_FAILED(xrGetSystem(instance_, &systemInfo, &systemId_))) {
        logError("No OpenXR head-mounted display runtime is available.");
        shutdown();
        return false;
    }

    XrSystemProperties properties{};
    properties.type = XR_TYPE_SYSTEM_PROPERTIES;
    if (XR_FAILED(xrGetSystemProperties(instance_, systemId_, &properties))) {
        logError("OpenXR system capabilities could not be queried.");
        shutdown();
        return false;
    }

    capabilities_.projection = {
        .flat = true,
        .equirectangular180 = true,
        .equirectangular360 = true,
        .fisheye180 = true,
        .cubemap360 = true,
        .wideFieldOfView = true,
    };
    capabilities_.stereo = {
        .mono = true,
        .sideBySide = true,
        .topBottom = true,
        .multiview = true,
    };
    populateDecoderCapabilities(environment);

    if (!createGraphicsSession()) {
        shutdown();
        return false;
    }
    logInfo("OpenStream Quest OpenXR session is ready.");
    return true;
}

bool QuestRuntime::createInstance(android_app* app) {
    std::uint32_t extensionCount = 0;
    if (XR_FAILED(xrEnumerateInstanceExtensionProperties(nullptr, 0, &extensionCount, nullptr))) {
        return false;
    }
    std::vector<XrExtensionProperties> extensions(extensionCount);
    for (XrExtensionProperties& property : extensions) {
        property.type = XR_TYPE_EXTENSION_PROPERTIES;
    }
    if (XR_FAILED(xrEnumerateInstanceExtensionProperties(
            nullptr,
            extensionCount,
            &extensionCount,
            extensions.data()))) {
        return false;
    }

    constexpr std::array requiredExtensions{
        XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,
        XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME,
    };
    for (const char* extension : requiredExtensions) {
        if (!hasExtension(extensions, extension)) {
            logError("The OpenXR runtime is missing a required Android graphics extension.");
            return false;
        }
    }

    std::vector<const char*> enabledExtensions(requiredExtensions.begin(), requiredExtensions.end());
    layerExtensions_.equirectangular = hasExtension(
        extensions,
        XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME
    );
    layerExtensions_.cube = hasExtension(extensions, XR_KHR_COMPOSITION_LAYER_CUBE_EXTENSION_NAME);
    if (layerExtensions_.equirectangular) {
        enabledExtensions.push_back(XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME);
    }
    if (layerExtensions_.cube) {
        enabledExtensions.push_back(XR_KHR_COMPOSITION_LAYER_CUBE_EXTENSION_NAME);
    }

    XrInstanceCreateInfoAndroidKHR androidInfo{};
    androidInfo.type = XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR;
    androidInfo.applicationVM = app->activity->vm;
    androidInfo.applicationActivity = app->activity->clazz;

    XrInstanceCreateInfo createInfo{};
    createInfo.type = XR_TYPE_INSTANCE_CREATE_INFO;
    createInfo.next = &androidInfo;
    std::strncpy(
        createInfo.applicationInfo.applicationName,
        "OpenStream Quest",
        XR_MAX_APPLICATION_NAME_SIZE - 1
    );
    std::strncpy(
        createInfo.applicationInfo.engineName,
        "OpenStream Native",
        XR_MAX_ENGINE_NAME_SIZE - 1
    );
    createInfo.applicationInfo.applicationVersion = 1;
    createInfo.applicationInfo.engineVersion = 1;
    createInfo.applicationInfo.apiVersion = XR_API_VERSION_1_0;
    createInfo.enabledExtensionCount = static_cast<std::uint32_t>(enabledExtensions.size());
    createInfo.enabledExtensionNames = enabledExtensions.data();
    return XR_SUCCEEDED(xrCreateInstance(&createInfo, &instance_));
}

bool QuestRuntime::createEglContext() {
    eglDisplay_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay_ == EGL_NO_DISPLAY || !eglInitialize(eglDisplay_, nullptr, nullptr)) return false;

    constexpr EGLint renderableEs3Bit = 0x0040;
    constexpr std::array<EGLint, 17> configAttributes{
        EGL_RENDERABLE_TYPE, renderableEs3Bit,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE,
    };
    EGLint configCount = 0;
    if (!eglChooseConfig(
            eglDisplay_,
            configAttributes.data(),
            &eglConfig_,
            1,
            &configCount) || configCount < 1) {
        return false;
    }

    constexpr std::array<EGLint, 5> pbufferAttributes{
        EGL_WIDTH, 16,
        EGL_HEIGHT, 16,
        EGL_NONE,
    };
    eglSurface_ = eglCreatePbufferSurface(eglDisplay_, eglConfig_, pbufferAttributes.data());
    constexpr std::array<EGLint, 3> contextAttributes{
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE,
    };
    eglContext_ = eglCreateContext(
        eglDisplay_,
        eglConfig_,
        EGL_NO_CONTEXT,
        contextAttributes.data()
    );
    return eglSurface_ != EGL_NO_SURFACE && eglContext_ != EGL_NO_CONTEXT &&
        eglMakeCurrent(eglDisplay_, eglSurface_, eglSurface_, eglContext_);
}

bool QuestRuntime::createGraphicsSession() {
    PFN_xrGetOpenGLESGraphicsRequirementsKHR getRequirements = nullptr;
    if (XR_FAILED(xrGetInstanceProcAddr(
            instance_,
            "xrGetOpenGLESGraphicsRequirementsKHR",
            reinterpret_cast<PFN_xrVoidFunction*>(&getRequirements))) || getRequirements == nullptr) {
        return false;
    }
    XrGraphicsRequirementsOpenGLESKHR requirements{};
    requirements.type = XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR;
    if (XR_FAILED(getRequirements(instance_, systemId_, &requirements)) || !createEglContext()) {
        return false;
    }

    XrGraphicsBindingOpenGLESAndroidKHR graphicsBinding{};
    graphicsBinding.type = XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR;
    graphicsBinding.display = eglDisplay_;
    graphicsBinding.config = eglConfig_;
    graphicsBinding.context = eglContext_;

    XrSessionCreateInfo sessionInfo{};
    sessionInfo.type = XR_TYPE_SESSION_CREATE_INFO;
    sessionInfo.next = &graphicsBinding;
    sessionInfo.systemId = systemId_;
    if (XR_FAILED(xrCreateSession(instance_, &sessionInfo, &session_))) return false;

    XrReferenceSpaceCreateInfo spaceInfo{};
    spaceInfo.type = XR_TYPE_REFERENCE_SPACE_CREATE_INFO;
    spaceInfo.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
    spaceInfo.poseInReferenceSpace.orientation.w = 1.0f;
    return XR_SUCCEEDED(xrCreateReferenceSpace(session_, &spaceInfo, &localSpace_));
}

void QuestRuntime::populateDecoderCapabilities(JNIEnv* environment) {
    constexpr int probeWidth = 4096;
    constexpr int probeHeight = 2048;
    capabilities_.codec = {
        .h264 = decoderAvailable(environment, "video/avc", probeWidth, probeHeight),
        .hevc = decoderAvailable(environment, "video/hevc", probeWidth, probeHeight),
        .av1 = decoderAvailable(environment, "video/av01", probeWidth, probeHeight),
        .vp9 = decoderAvailable(environment, "video/x-vnd.on2.vp9", probeWidth, probeHeight),
    };

    constexpr std::array resolutionProbes{
        std::pair{8192, 4096},
        std::pair{5760, 2880},
        std::pair{4096, 2048},
        std::pair{1920, 1080},
    };
    for (const auto& [width, height] : resolutionProbes) {
        if (decoderAvailable(environment, "video/hevc", width, height) ||
            decoderAvailable(environment, "video/av01", width, height) ||
            decoderAvailable(environment, "video/avc", width, height)) {
            capabilities_.maxDecodeWidth = static_cast<std::uint32_t>(width);
            capabilities_.maxDecodeHeight = static_cast<std::uint32_t>(height);
            break;
        }
    }
    capabilities_.dynamicRange = {
        .sdr = true,
        .hdr10 = false,
        .hdr10Plus = false,
        .hlg = false,
        .dolbyVision = false,
    };
    capabilities_.audio = {
        .standard = true,
        .spatial = true,
        .dolbyAtmos = audioDecoderAvailable(environment, "audio/eac3-joc"),
    };
}

std::optional<VideoLayerPlan> QuestRuntime::prepareVideo(const xr::MediaSpec& media) {
    const xr::PreparationResult result = videoSession_.prepare(media, capabilities_);
    if (!result.accepted()) return std::nullopt;
    return makeVideoLayerPlan(media, *result.plan, layerExtensions_);
}

void QuestRuntime::handleSessionState(XrSessionState state) {
    sessionState_ = state;
    if (state == XR_SESSION_STATE_READY && !sessionRunning_) {
        XrSessionBeginInfo beginInfo{};
        beginInfo.type = XR_TYPE_SESSION_BEGIN_INFO;
        beginInfo.primaryViewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
        sessionRunning_ = XR_SUCCEEDED(xrBeginSession(session_, &beginInfo));
    } else if (state == XR_SESSION_STATE_STOPPING && sessionRunning_) {
        xrEndSession(session_);
        sessionRunning_ = false;
    } else if (state == XR_SESSION_STATE_EXITING || state == XR_SESSION_STATE_LOSS_PENDING) {
        exitRequested_ = true;
    }
}

void QuestRuntime::pollEvents() {
    if (instance_ == XR_NULL_HANDLE) return;
    XrEventDataBuffer event{};
    event.type = XR_TYPE_EVENT_DATA_BUFFER;
    while (xrPollEvent(instance_, &event) == XR_SUCCESS) {
        if (event.type == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
            const auto* changed = reinterpret_cast<const XrEventDataSessionStateChanged*>(&event);
            handleSessionState(changed->state);
        } else if (event.type == XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING) {
            exitRequested_ = true;
        }
        event = XrEventDataBuffer{};
        event.type = XR_TYPE_EVENT_DATA_BUFFER;
    }
}

void QuestRuntime::renderFrame() {
    if (!sessionRunning_) return;
    XrFrameState frameState{};
    frameState.type = XR_TYPE_FRAME_STATE;
    XrFrameWaitInfo waitInfo{};
    waitInfo.type = XR_TYPE_FRAME_WAIT_INFO;
    if (XR_FAILED(xrWaitFrame(session_, &waitInfo, &frameState))) return;
    XrFrameBeginInfo beginInfo{};
    beginInfo.type = XR_TYPE_FRAME_BEGIN_INFO;
    if (XR_FAILED(xrBeginFrame(session_, &beginInfo))) return;

    XrFrameEndInfo endInfo{};
    endInfo.type = XR_TYPE_FRAME_END_INFO;
    endInfo.displayTime = frameState.predictedDisplayTime;
    endInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    endInfo.layerCount = 0;
    endInfo.layers = nullptr;
    xrEndFrame(session_, &endInfo);
}

void QuestRuntime::destroyEglContext() {
    if (eglDisplay_ == EGL_NO_DISPLAY) return;
    eglMakeCurrent(eglDisplay_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (eglContext_ != EGL_NO_CONTEXT) eglDestroyContext(eglDisplay_, eglContext_);
    if (eglSurface_ != EGL_NO_SURFACE) eglDestroySurface(eglDisplay_, eglSurface_);
    eglTerminate(eglDisplay_);
    eglDisplay_ = EGL_NO_DISPLAY;
    eglConfig_ = nullptr;
    eglContext_ = EGL_NO_CONTEXT;
    eglSurface_ = EGL_NO_SURFACE;
}

void QuestRuntime::shutdown() {
    videoSession_.stop();
    if (sessionRunning_ && session_ != XR_NULL_HANDLE) xrEndSession(session_);
    sessionRunning_ = false;
    if (localSpace_ != XR_NULL_HANDLE) xrDestroySpace(localSpace_);
    if (session_ != XR_NULL_HANDLE) xrDestroySession(session_);
    localSpace_ = XR_NULL_HANDLE;
    session_ = XR_NULL_HANDLE;
    destroyEglContext();
    if (instance_ != XR_NULL_HANDLE) xrDestroyInstance(instance_);
    instance_ = XR_NULL_HANDLE;
    systemId_ = XR_NULL_SYSTEM_ID;
}

}  // namespace openstream::quest
