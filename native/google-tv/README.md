# OpenStream for Google TV

Native Android TV target that shares OpenStream's Kotlin/Compose, Media3, metadata, recommendation, and offline-download core while using a TV-only manifest and package ID.

- Remote-first launcher (`LEANBACK_LAUNCHER`), no touchscreen requirement
- Compose for TV Material 1.1 and TV Foundation 1.0
- Media3 playback, sessions, HLS/DASH, captions, resume, and app-storage downloads
- HDR capability declarations and the new static cube launcher artwork

Build locally:

```sh
OPENSTREAM_GOOGLE_TV_BUILD_DIR=/tmp/openstream-google-tv-build \
  gradle --no-watch-fs --project-cache-dir /tmp/openstream-google-tv-gradle \
  -p native/google-tv testDebugUnitTest lintDebug assembleDebug
```
