cmake_minimum_required(VERSION 3.22.1)

project(OpenStreamXRCore VERSION 0.1.0 LANGUAGES CXX)

include(CTest)

add_library(openstream_xr_core STATIC
    shared/immersive_video_geometry.cpp
    shared/immersive_video_session.cpp
)

target_include_directories(openstream_xr_core
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/shared
)

target_compile_features(openstream_xr_core PUBLIC cxx_std_20)
set_target_properties(openstream_xr_core PROPERTIES
    CXX_EXTENSIONS OFF
    POSITION_INDEPENDENT_CODE ON
)

if(MSVC)
    target_compile_options(openstream_xr_core PRIVATE /W4 /WX)
else()
    target_compile_options(openstream_xr_core PRIVATE -Wall -Wextra -Werror)
endif()

if(BUILD_TESTING)
    add_executable(openstream_xr_geometry_test
        tests/immersive_video_geometry_test.cpp
    )
    target_link_libraries(openstream_xr_geometry_test PRIVATE openstream_xr_core)
    add_test(NAME openstream_xr_geometry COMMAND openstream_xr_geometry_test)

    add_executable(openstream_xr_session_test
        tests/immersive_video_session_test.cpp
    )
    target_link_libraries(openstream_xr_session_test PRIVATE openstream_xr_core)
    add_test(NAME openstream_xr_session COMMAND openstream_xr_session_test)
endif()
