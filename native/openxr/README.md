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

Run both native suites with `scripts/openxr-core-test.sh`.

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
| Flat / wide mesh | SceneCore panel or surface entity | OpenXR quad layer |
| 180° projection | SceneCore hemisphere surface | Inward OpenXR mesh |
| 360° projection | SceneCore sphere surface | Inward OpenXR mesh |
| Packed SBS / top-bottom | Media3 surface plus per-eye UV crop | MediaCodec surface plus per-eye UV crop |
| MV-HEVC / multiview | Two decoded views when supported | Two texture-array layers |
| HDR and spatial audio | Android decoder/display/audio capability probe | Quest decoder/display/audio capability probe |

The Android XR adapter will use Jetpack XR SceneCore full-space surfaces and
Media3. The Meta Quest adapter will use the Khronos Android OpenXR loader,
OpenXR swapchains, and Android MediaCodec surface decoding. Neither adapter
may infer support from a filename alone or bypass DRM.

## Remaining platform prerequisites

The runtime adapters are intentionally not declared buildable until the
official Android NDK/CMake, Jetpack XR SceneCore dependency, and Khronos
OpenXR Android loader are installed. A real Android XR or Meta Quest runtime
is also required for end-to-end frame, controller, decoder, HDR, audio, and
comfort validation.

- Android XR OpenXR setup: <https://developer.android.com/develop/xr/openxr/get-started>
- Android XR spatial video: <https://developer.android.com/develop/xr/jetpack-xr-sdk/add-spatial-video>
- Khronos OpenXR loader: <https://central.sonatype.com/artifact/org.khronos.openxr/openxr_loader_for_android>
