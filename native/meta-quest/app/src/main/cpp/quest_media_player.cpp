#include "quest_media_player.hpp"

#include <GLES2/gl2ext.h>
#include <android/log.h>

namespace openstream::quest {
namespace {

constexpr const char* kLogTag = "OpenStreamQuest";

void deleteLocalReference(JNIEnv* environment, jobject reference) {
    if (reference != nullptr) environment->DeleteLocalRef(reference);
}

jclass loadApplicationClass(JNIEnv* environment, jobject activity, const char* className) {
    jclass activityClass = environment->GetObjectClass(activity);
    if (activityClass == nullptr) return nullptr;
    const jmethodID getClassLoader = environment->GetMethodID(
        activityClass,
        "getClassLoader",
        "()Ljava/lang/ClassLoader;"
    );
    jobject classLoader = getClassLoader == nullptr
        ? nullptr
        : environment->CallObjectMethod(activity, getClassLoader);
    jclass loaderClass = classLoader == nullptr ? nullptr : environment->GetObjectClass(classLoader);
    const jmethodID loadClass = loaderClass == nullptr
        ? nullptr
        : environment->GetMethodID(
            loaderClass,
            "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;"
        );
    jstring requestedClass = environment->NewStringUTF(className);
    auto result = loadClass == nullptr || requestedClass == nullptr
        ? nullptr
        : static_cast<jclass>(
            environment->CallObjectMethod(classLoader, loadClass, requestedClass)
        );
    deleteLocalReference(environment, requestedClass);
    deleteLocalReference(environment, loaderClass);
    deleteLocalReference(environment, classLoader);
    deleteLocalReference(environment, activityClass);
    return result;
}

}  // namespace

QuestMediaPlayer::~QuestMediaPlayer() {
    shutdown();
}

bool QuestMediaPlayer::initialize(
    JNIEnv* environment,
    jobject activity,
    const std::string& uri,
    std::uint32_t width,
    std::uint32_t height
) {
    shutdown();
    if (environment == nullptr || activity == nullptr || uri.empty() || width == 0 || height == 0) {
        return false;
    }
    environment_ = environment;

    glGenTextures(1, &externalTexture_);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalTexture_);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    if (externalTexture_ == 0 || glGetError() != GL_NO_ERROR) {
        shutdown();
        return false;
    }

    jclass textureClass = environment_->FindClass("android/graphics/SurfaceTexture");
    jclass surfaceClass = environment_->FindClass("android/view/Surface");
    jclass bridgeClass = loadApplicationClass(
        environment_,
        activity,
        "com.cyberbanksy.openstream.quest.QuestMedia3Bridge"
    );
    if (clearException("looking up Android media classes") || textureClass == nullptr ||
        surfaceClass == nullptr || bridgeClass == nullptr) {
        deleteLocalReference(environment_, bridgeClass);
        deleteLocalReference(environment_, surfaceClass);
        deleteLocalReference(environment_, textureClass);
        shutdown();
        return false;
    }

    const jmethodID textureConstructor = environment_->GetMethodID(textureClass, "<init>", "(I)V");
    updateTextureMethod_ = environment_->GetMethodID(textureClass, "updateTexImage", "()V");
    getTransformMethod_ = environment_->GetMethodID(textureClass, "getTransformMatrix", "([F)V");
    const jmethodID setSizeMethod = environment_->GetMethodID(
        textureClass,
        "setDefaultBufferSize",
        "(II)V"
    );
    const jmethodID surfaceConstructor = environment_->GetMethodID(
        surfaceClass,
        "<init>",
        "(Landroid/graphics/SurfaceTexture;)V"
    );
    const jmethodID bridgeConstructor = environment_->GetMethodID(
        bridgeClass,
        "<init>",
        "(Landroid/app/Activity;Landroid/view/Surface;Ljava/lang/String;)V"
    );
    const jmethodID readyMethod = environment_->GetMethodID(bridgeClass, "isReady", "()Z");
    if (clearException("looking up Android media methods") || textureConstructor == nullptr ||
        updateTextureMethod_ == nullptr || getTransformMethod_ == nullptr || setSizeMethod == nullptr ||
        surfaceConstructor == nullptr || bridgeConstructor == nullptr || readyMethod == nullptr) {
        deleteLocalReference(environment_, bridgeClass);
        deleteLocalReference(environment_, surfaceClass);
        deleteLocalReference(environment_, textureClass);
        shutdown();
        return false;
    }

    jobject localTexture = environment_->NewObject(
        textureClass,
        textureConstructor,
        static_cast<jint>(externalTexture_)
    );
    if (localTexture != nullptr) {
        environment_->CallVoidMethod(
            localTexture,
            setSizeMethod,
            static_cast<jint>(width),
            static_cast<jint>(height)
        );
    }
    jobject localSurface = localTexture == nullptr
        ? nullptr
        : environment_->NewObject(surfaceClass, surfaceConstructor, localTexture);
    jstring localUri = environment_->NewStringUTF(uri.c_str());
    jobject localBridge = localSurface == nullptr || localUri == nullptr
        ? nullptr
        : environment_->NewObject(bridgeClass, bridgeConstructor, activity, localSurface, localUri);
    const bool constructionFailed = clearException("creating the Media3 video surface") ||
        localTexture == nullptr || localSurface == nullptr || localBridge == nullptr;
    const bool bridgeReady = !constructionFailed &&
        environment_->CallBooleanMethod(localBridge, readyMethod) == JNI_TRUE &&
        !clearException("checking Media3 readiness");

    if (bridgeReady) {
        surfaceTexture_ = environment_->NewGlobalRef(localTexture);
        bridge_ = environment_->NewGlobalRef(localBridge);
    }
    const bool globalReferencesReady = bridgeReady && surfaceTexture_ != nullptr && bridge_ != nullptr;

    if (!globalReferencesReady && localBridge != nullptr) {
        const jmethodID releaseMethod = environment_->GetMethodID(bridgeClass, "release", "()V");
        if (releaseMethod != nullptr) environment_->CallVoidMethod(localBridge, releaseMethod);
        (void) clearException("releasing an incomplete Media3 bridge");
    }

    deleteLocalReference(environment_, localBridge);
    deleteLocalReference(environment_, localUri);
    deleteLocalReference(environment_, localSurface);
    deleteLocalReference(environment_, localTexture);
    deleteLocalReference(environment_, bridgeClass);
    deleteLocalReference(environment_, surfaceClass);
    deleteLocalReference(environment_, textureClass);

    if (!globalReferencesReady) {
        shutdown();
        return false;
    }
    return true;
}

bool QuestMediaPlayer::updateTexture(std::array<float, 16>& transform) {
    if (environment_ == nullptr || surfaceTexture_ == nullptr || updateTextureMethod_ == nullptr ||
        getTransformMethod_ == nullptr) {
        return false;
    }
    environment_->CallVoidMethod(surfaceTexture_, updateTextureMethod_);
    if (clearException("updating the decoded video texture")) return false;

    jfloatArray matrix = environment_->NewFloatArray(static_cast<jsize>(transform.size()));
    if (matrix == nullptr) return false;
    environment_->CallVoidMethod(surfaceTexture_, getTransformMethod_, matrix);
    const bool transformFailed = clearException("reading the decoded video transform");
    if (!transformFailed) {
        environment_->GetFloatArrayRegion(
            matrix,
            0,
            static_cast<jsize>(transform.size()),
            transform.data()
        );
    }
    deleteLocalReference(environment_, matrix);
    return !transformFailed && !clearException("copying the decoded video transform");
}

bool QuestMediaPlayer::callBridge(const char* methodName) {
    if (environment_ == nullptr || bridge_ == nullptr) return false;
    jclass bridgeClass = environment_->GetObjectClass(bridge_);
    if (bridgeClass == nullptr || clearException("looking up the Media3 bridge")) {
        deleteLocalReference(environment_, bridgeClass);
        return false;
    }
    const jmethodID method = environment_->GetMethodID(bridgeClass, methodName, "()V");
    if (method != nullptr) environment_->CallVoidMethod(bridge_, method);
    const bool failed = method == nullptr || clearException("controlling Media3 playback");
    deleteLocalReference(environment_, bridgeClass);
    return !failed;
}

void QuestMediaPlayer::play() {
    (void) callBridge("play");
}

void QuestMediaPlayer::pause() {
    (void) callBridge("pause");
}

void QuestMediaPlayer::shutdown() {
    if (environment_ != nullptr && bridge_ != nullptr) {
        (void) callBridge("release");
        environment_->DeleteGlobalRef(bridge_);
    }
    if (environment_ != nullptr && surfaceTexture_ != nullptr) {
        jclass textureClass = environment_->GetObjectClass(surfaceTexture_);
        if (textureClass != nullptr) {
            const jmethodID releaseMethod = environment_->GetMethodID(textureClass, "release", "()V");
            if (releaseMethod != nullptr) environment_->CallVoidMethod(surfaceTexture_, releaseMethod);
            (void) clearException("releasing the decoded video texture");
        }
        deleteLocalReference(environment_, textureClass);
        environment_->DeleteGlobalRef(surfaceTexture_);
    }
    bridge_ = nullptr;
    surfaceTexture_ = nullptr;
    updateTextureMethod_ = nullptr;
    getTransformMethod_ = nullptr;
    if (externalTexture_ != 0) glDeleteTextures(1, &externalTexture_);
    externalTexture_ = 0;
    environment_ = nullptr;
}

bool QuestMediaPlayer::clearException(const char* operation) const {
    if (environment_ == nullptr || !environment_->ExceptionCheck()) return false;
    environment_->ExceptionClear();
    __android_log_print(
        ANDROID_LOG_ERROR,
        kLogTag,
        "Android exception while %s.",
        operation
    );
    return true;
}

}  // namespace openstream::quest
