# OpenStream Android XR

This module is the universal Android media target for Android TV, standard Android,
Android XR headsets, Samsung Android XR devices, and Meta Quest compatibility.

## Implemented

- Media3 1.10.1 playback for local, HLS, and DASH media.
- Android 17 API 37.1 compilation with Android Gradle Plugin 9.3.0.
- Runtime display and decoder probing for HDR10, HDR10+, HLG, Dolby Vision,
  HEVC Main10, and AV1 Main10.
- Dolby Vision codec-string profile and level inspection without claiming a
  device supports a profile that its decoder does not advertise.
- Optional OpenXR 1.1 and 6DoF feature declarations so the universal APK remains
  installable in 2D compatibility mode.
- Standards-based WebXR handoff for immersive 180/360 playback on compatible
  headset browsers.
- HTTPS-only release networking and no Android backup of connection data.

The native OpenXR renderer is intentionally a separate product flavor: a 2D
Media3 surface cannot become a conforming stereoscopic OpenXR compositor layer.
Until that flavor is linked against a headset runtime and validated on hardware,
the app reports immersive playback as unavailable instead of silently showing a
flat panel.

## Build and test

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle -p native/android-xr test assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
