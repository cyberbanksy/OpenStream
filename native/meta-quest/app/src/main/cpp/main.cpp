#include "quest_runtime.hpp"

#include <android/log.h>
#include <android_native_app_glue.h>

extern "C" void android_main(android_app* app) {
    if (app == nullptr || app->activity == nullptr) return;

    JNIEnv* environment = nullptr;
    JavaVM* virtualMachine = app->activity->vm;
    if (virtualMachine->AttachCurrentThread(&environment, nullptr) != JNI_OK) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            "OpenStreamQuest",
            "%s",
            "Could not attach the native OpenXR thread to Android."
        );
        return;
    }

    openstream::quest::QuestRuntime runtime;
    if (!runtime.initialize(app, environment)) {
        ANativeActivity_finish(app->activity);
        virtualMachine->DetachCurrentThread();
        return;
    }

    while (app->destroyRequested == 0 && !runtime.exitRequested()) {
        android_poll_source* source = nullptr;
        int events = 0;
        const int timeoutMillis = runtime.sessionRunning() ? 0 : 16;
        while (ALooper_pollOnce(
                timeoutMillis,
                nullptr,
                &events,
                reinterpret_cast<void**>(&source)) >= 0) {
            if (source != nullptr) source->process(app, source);
            if (app->destroyRequested != 0) break;
            if (runtime.sessionRunning()) break;
        }
        runtime.pollEvents();
        runtime.renderFrame();
    }

    runtime.shutdown();
    virtualMachine->DetachCurrentThread();
}
