# OpenStream Meta Quest

This package is the arm64 native Meta Quest/OpenXR application. It links the
official Khronos Android loader 1.1.62 through Prefab, initializes the active
Quest runtime, creates an OpenGL ES OpenXR session, and probes MediaCodec
video/audio support on the headset before accepting an immersive video.

Version 0.2.0 accepts `content:`, `file:`, `http:`, and `https:` video VIEW
intents. Media3 decodes the source into an external OpenGL texture, the native
renderer copies each frame into an OpenXR swapchain, and the runtime submits a
quad or `XR_KHR_composition_layer_equirect2` layer. Playback starts when the
immersive session becomes ready and pauses/resumes with both the Android and
OpenXR lifecycle. Mono, side-by-side, and top-bottom sources have independent
eye visibility and pixel crops.

The optional VIEW-intent extras are `projection`, `stereo`, `codec`,
`dynamicRange`, `audio`, `width`, `height`, `durationSeconds`, and `live`.
Projection and packed-stereo layout may also be classified from conventional
source names; HDR is deliberately accepted only from explicit metadata. Codec
defaults to H.264, while dimensions default to a projection-appropriate output
size. A launch example is:

```text
adb shell am start -a android.intent.action.VIEW \
  -d 'https://media.example/video_360_sbs.mp4' -t 'video/mp4' \
  --es projection 360 --es stereo sbs --es codec hevc \
  --ei width 4096 --ei height 2048
```

The adapter reuses `native/openxr` policy and geometry for flat, wide-FOV,
equirectangular 180/360, fisheye/VR180, cubemap/EAC, mono, packed stereo, and
multiview classification. The current decoded-frame compositor supports flat
and equirectangular 180/360 mono/SBS/top-bottom. Wide-FOV, fisheye, cubemap,
multiview, and equirectangular fallback on a runtime without the KHR extension
are blocked until their projection-mesh or texture-array renderer exists; the
app does not display them with incorrect UV mapping.

Build with:

```text
OPENSTREAM_QUEST_BUILD_DIR=/tmp/openstream-quest-build \
  gradle --no-watch-fs -p native/meta-quest lintDebug assembleDebug
```

A physical Quest runtime is required to validate decoded presentation frames,
eye orientation/crops, controller input, comfort, headset decoder limits,
spatial audio, and store signing. HDR is rejected until the runtime and display
are both proven to support the requested transfer function; it is never
inferred from a filename.
