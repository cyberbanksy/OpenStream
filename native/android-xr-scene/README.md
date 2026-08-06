# OpenStream Android XR SceneCore

This is the native managed-full-space Android XR application. It uses
SceneCore 1.0.0-beta01 `SurfaceEntity` objects and Media3 to autoplay an
incoming local or network video URI.

The intent extras `projection`, `stereo`, and `dynamicRange` override filename
classification. Direct SceneCore playback covers flat, wide-FOV,
equirectangular 180/360, mono, side-by-side, top-bottom, MV-HEVC, HDR10,
HDR10+, HLG, and Dolby Vision decoder output. Fisheye and EAC/cubemap sources
are blocked with an explicit native-custom-mesh requirement instead of being
displayed with incorrect UV mapping. The Meta Quest package provides that
native mesh policy.

Build with:

```text
gradle --no-watch-fs -p native/android-xr-scene testDebugUnitTest lintDebug assembleDebug
```
