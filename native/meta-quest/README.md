# OpenStream Meta Quest

This package is the arm64 native Meta Quest/OpenXR application. It links the
official Khronos Android loader 1.1.62 through Prefab, initializes the active
Quest runtime, creates an OpenGL ES OpenXR session, and negotiates MediaCodec
video/audio support on the headset before accepting an immersive video.

The adapter reuses `native/openxr` for flat, wide-FOV, equirectangular 180/360,
fisheye/VR180, cubemap/EAC, mono, side-by-side, top-bottom, and multiview
geometry. Native compositor equirectangular and cube layers are selected when
the runtime exposes them; otherwise the tested inward projection mesh is used.

Build with:

```text
OPENSTREAM_QUEST_BUILD_DIR=/tmp/openstream-quest-build \
  gradle --no-watch-fs -p native/meta-quest lintDebug assembleDebug
```

A physical Quest runtime is required to validate presentation frames,
controller input, comfort, headset decoder limits, spatial audio, and store
signing. HDR is rejected until the runtime and display are both proven to
support the requested transfer function; it is never inferred from a filename.
