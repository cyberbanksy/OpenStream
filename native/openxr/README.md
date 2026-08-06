# OpenStream native XR core

This directory contains the runtime-independent C++20 core shared by the
Android XR SceneCore and Meta Quest OpenXR video adapters.

## Implemented and host-tested

- Inward flat, wide-field, equirectangular 180°/360°, fisheye/VR180, and
  equi-angular cubemap presentation geometry.
- Mono, side-by-side, top-bottom, and multiview/MV-HEVC eye binding.
- Per-eye UV crops for packed stereo and texture-array layers for multiview.
- Capability negotiation for decoder resolution, H.264/HEVC/AV1/VP9, SDR,
  HDR10, HDR10+, HLG, Dolby Vision, standard/spatial/Atmos audio, projection,
  and stereo support.
- Deterministic ready/play/pause/seek/end/stop session transitions, including
  live-stream behavior.

Run both native suites with `sh scripts/openxr-core-test.sh` and
`sh scripts/quest-layer-test.sh`.

The directory is also a standalone CMake project. Configure it with
`cmake -S native/openxr -B /tmp/openstream-xr-cmake -DBUILD_TESTING=ON`,
then run `cmake --build /tmp/openstream-xr-cmake` and
`ctest --test-dir /tmp/openstream-xr-cmake --output-on-failure`. The
`openstream_xr_core` static library is position-independent so both Android
NDK shared libraries and native OpenXR executables can link it unchanged.

## Runtime adapter boundary

Both headset adapters must probe the real runtime and decoder, fill
`RuntimeCapabilities`, call `ImmersiveVideoSession::prepare`, and create
runtime resources only after the result is accepted.

| Shared plan | Android XR adapter | Meta Quest adapter |
| --- | --- | --- |
| Flat / wide mesh | SceneCore panel or surface entity | Flat OpenXR quad; wide mesh pending |
| 180° projection | SceneCore hemisphere surface | KHR equirectangular layer; mesh fallback pending |
| 360° projection | SceneCore sphere surface | KHR equirectangular layer; mesh fallback pending |
| Packed SBS / top-bottom | Media3 surface plus per-eye UV crop | Media3 surface, swapchain copy, per-eye image rect |
| MV-HEVC / multiview | Two decoded views when supported | Texture-array renderer pending |
| HDR and spatial audio | Android decoder/display/audio capability probe | Quest decoder/display/audio capability probe |

The Android XR adapter uses Jetpack XR SceneCore full-space surfaces and
Media3. The Meta Quest adapter uses Media3, the Khronos Android OpenXR loader,
a real OpenGL ES OpenXR session, runtime MediaCodec queries, an external-texture
copy shader, and video-layer swapchains. Its current compositor presents flat
and equirectangular mono/SBS/top-bottom sources; other planned geometry remains
blocked until its runtime renderer exists. Neither adapter infers decoder/HDR
support from a filename or bypasses DRM.

## Runtime packages and remaining hardware prerequisites

Both debug packages are buildable with NDK 29.0.14206865 and CMake 4.1.2.
Android XR uses SceneCore 1.0.0-beta01; Quest uses Khronos loader 1.1.62. A
real Android XR or Meta Quest runtime is still required for end-to-end frame,
controller, decoder, HDR, audio, and comfort validation.

- Android XR OpenXR setup: <https://developer.android.com/develop/xr/openxr/get-started>
- Android XR spatial video: <https://developer.android.com/develop/xr/jetpack-xr-sdk/add-spatial-video>
- Khronos OpenXR loader: <https://central.sonatype.com/artifact/org.khronos.openxr/openxr_loader_for_android>
