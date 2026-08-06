cmake_minimum_required(VERSION 3.22.1)

project(OpenStreamQuest VERSION 0.1.0 LANGUAGES C CXX)

find_package(OpenXR REQUIRED CONFIG)

set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../openxr
    ${CMAKE_CURRENT_BINARY_DIR}/openstream-xr-core
)

add_library(native_app_glue STATIC
    ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c
)
target_include_directories(native_app_glue PUBLIC
    ${ANDROID_NDK}/sources/android/native_app_glue
)

add_library(openstream_quest SHARED
    main.cpp
    quest_compositor_video.cpp
    quest_launch_media.cpp
    quest_media_player.cpp
    quest_runtime.cpp
    quest_video_layer_plan.cpp
)

target_compile_features(openstream_quest PRIVATE cxx_std_20)
set_target_properties(openstream_quest PROPERTIES CXX_EXTENSIONS OFF)
target_compile_definitions(openstream_quest PRIVATE
    XR_USE_PLATFORM_ANDROID
    XR_USE_GRAPHICS_API_OPENGL_ES
)
target_compile_options(openstream_quest PRIVATE
    -Wall
    -Wextra
    -Werror
)
target_link_options(openstream_quest PRIVATE "-uANativeActivity_onCreate")
target_link_libraries(openstream_quest PRIVATE
    native_app_glue
    openstream_xr_core
    OpenXR::openxr_loader
    android
    log
    EGL
    GLESv3
)
