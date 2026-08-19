#pragma once

#include <GLES3/gl3.h>
#include <jni.h>

#include <array>
#include <cstdint>
#include <string>

namespace openstream::quest {

class QuestMediaPlayer {
public:
    QuestMediaPlayer() = default;
    QuestMediaPlayer(const QuestMediaPlayer&) = delete;
    QuestMediaPlayer& operator=(const QuestMediaPlayer&) = delete;
    ~QuestMediaPlayer();

    [[nodiscard]] bool initialize(
        JNIEnv* environment,
        jobject activity,
        const std::string& uri,
        std::uint32_t width,
        std::uint32_t height
    );
    [[nodiscard]] bool updateTexture(std::array<float, 16>& transform);
    void play();
    void pause();
    void shutdown();

    [[nodiscard]] bool ready() const { return bridge_ != nullptr; }
    [[nodiscard]] GLuint texture() const { return externalTexture_; }

private:
    [[nodiscard]] bool callBridge(const char* methodName);
    [[nodiscard]] bool clearException(const char* operation) const;

    JNIEnv* environment_ = nullptr;
    jobject bridge_ = nullptr;
    jobject surfaceTexture_ = nullptr;
    jmethodID updateTextureMethod_ = nullptr;
    jmethodID getTransformMethod_ = nullptr;
    GLuint externalTexture_ = 0;
};

}  // namespace openstream::quest
