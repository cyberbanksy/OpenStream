# OpenStream Master Guide

This file is the operational source of truth for OpenStream's metadata, playback, recommendation, and release configuration.

## Ownership and branding

- Product name: **OpenStream**
- Maintainer profile: <https://github.com/cyberbanksy>
- Canonical publication target: the private [`cyberbanksy/OpenStream`](https://github.com/cyberbanksy/OpenStream) repository. The legacy `orgista/openstream` remote is retained locally only to preserve history until migration is reviewed.
- The Android application ID remains `com.orgista.openstream` only as a compatibility identifier. Changing it would install a second app and lose the existing app data, libraries, and resume history. It must not be presented as visible product branding.

## Metadata credentials

Never place a real API credential in source code, Gradle files, documentation, launch arguments, logs, screenshots, or an APK.

Android viewers opt in under **Settings → Artwork & descriptions (TMDB) → Use my metadata API credentials**. The stored credential is reusable by library scans and title details only while that switch is on. Both of these TMDB credential formats are supported:

- TMDB v4 API Read Access Token (preferred)
- TMDB v3 API key

The optional OMDb key uses the same opt-in switch. Turning the switch off makes all stored metadata credentials dormant without deleting the user's library.

The metadata server reads credentials from its process environment:

```text
TMDB_READ_TOKEN=<TMDB v4 read token>
# or
TMDB_API_KEY=<TMDB v3 API key>
```

`TMDB_READ_TOKEN` takes precedence when both variables are present. The key values supplied during development are intentionally not copied into this repository.

## Periodic recent-movie metadata

The loopback gateway refreshes metadata only; it does not download movie or television files.

```text
OPENSTREAM_METADATA_REFRESH_MS=21600000
OPENSTREAM_METADATA_CACHE_FILE=logs/metadata-cache.json
```

- Default interval: 6 hours
- Minimum interval: 15 minutes
- Sources: TMDB now playing, popular movies, and weekly movie trends
- Authenticated endpoint: `GET /api/metadata/recent`
- Cache files are private (`0600`) and live in the ignored `logs/` directory by default.

## Playback behavior

- Selecting **Play** routes directly to the first available media candidate and begins playback automatically.
- Remote playback keeps up to 12 distinct direct routes and advances automatically when a signed URL expires or another route fails, retaining the current resume position. Private route URLs and error causes are never logged.
- Positions are saved every five seconds under a hash of the stable media identity (with a URI-hash migration fallback); raw NAS paths and signed stream URLs are not stored in playback preferences.
- Resume is offered after 15 seconds and is cleared in the last 30 seconds or after 95% completion.
- Player title, source, and back control are transient and follow touch/remote controller visibility.
- The status bar is hidden and the navigation bar is transparent. Swipe-up Home remains available; an upward player gesture from the lower portion also exits the activity.
- All system-back gestures use one navigation rule: player/details → last tab, tab → Discover, Discover → exit.

## Recommendations

Recommendations run locally and deterministically. The ranking gives highest priority to resumable titles, learns item-to-item genre affinity from recent and repeated playback, moves completed titles behind unwatched choices, and then considers release recency and metadata completeness. Viewing identifiers and NAS paths are hashed in app-private preferences; no viewing history is uploaded.

The design adapts the useful, small-device pieces of collaborative filtering without pretending a single viewer has a community-sized model:

- `rcz7795/Movie-Recommendation-System`: item similarity, rating prediction, and measurable evaluation informed the separation between interaction signals and ranking.
- GroupLens MovieLens: MovieLens 32M is the stable external benchmark to use for offline evaluation after reviewing its current license/terms. It is not bundled, redistributed, or fetched by the app.
- Recent r/vibecoding media projects: preserve existing behavior with small testable changes, ground recommendations in a known catalog, weight watch history by recency and repeat visits, and keep reusable state-free UI building blocks.

## Built-in discovery add-ons

The following requested public Stremio manifests are compiled as toggleable discovery providers under **Sources**:

- Cinemeta — catalog and title metadata
- Streaming Catalogs — Netflix and related catalog rails (catalog metadata, not a video subscription bypass)
- Rotten Tomatoes — Certified Fresh catalog metadata
- The Movie Database Addon — latest/trending catalog and title metadata using the supplied reusable public configuration

Catalog responses refresh in the background every six hours and fall back to an app-private offline cache. A failure in one provider does not discard successful results from another provider.

Configured stream providers such as Torrentio/debrid are deliberately opt-in. Use **Sources → Add-on**, paste the personal manifest, and leave its switch enabled. OpenStream stores the full configured path only in app-private, non-backed-up preferences; the UI shows only the host and “private configuration.” It is never logged, placed in source, copied into this guide, or included in validation output. Only direct HTTP(S) stream URLs are playable; raw torrent locators are ignored.

Run the repeatable, credential-safe live check with:

```text
node scripts/stremio-addons.mjs
OPENSTREAM_STREAM_ADDON_MANIFEST_URL='<personal manifest>' node scripts/stremio-addons.mjs
```

The check retries transient 408/429/5xx responses with bounded exponential backoff and reports only manifest identity, capabilities, catalog counts, and direct-stream counts.

## Android and player baseline

- Compile/target baseline: Android 17 / API 37.
- Player baseline: Media3 1.10.1 paired with the ABI-compatible NextLib FFmpeg extension 1.10.1-0.13.0.
- Playback starts only from a viewer action, requests media audio focus, saves resume state continuously, and pauses on activity stop to comply with current background-playback hardening.
- The current Media3 patch includes decoder-recovery, Dolby Vision, DASH/HLS, and extractor crash fixes while preserving the existing direct-play/FFmpeg fallback behavior.

## Native platform matrix

| Platform | Native technology | Current runnable scope |
| --- | --- | --- |
| Android mobile/tablet | Kotlin, Compose, Media3 | Full catalog, metadata, add-ons, playback, resume, offline, captions, HDR/audio labels |
| Google TV | Kotlin, Compose for TV, Media3 | Independent Leanback app sharing the Android core |
| iPhone/iPad | SwiftUI, AVKit, AVFoundation | Adaptive native UI, playback, resume policy, device-storage offline store, network status |
| Apple TV | SwiftUI, AVKit | Focusable native UI and the shared Apple playback/offline core |
| Apple Vision Pro | SwiftUI, AVKit | Native visionOS target plus projection/capability negotiation |
| LG webOS | Packaged HTML5 app | Remote focus, native video controls, resume, offline catalog, format labels |
| Samsung Tizen | Packaged TV web app | Remote focus, registered media keys, native video controls, resume |
| Hisense VIDAA | Hosted HTML5 app package | Remote focus, HLS/DASH/MSS declaration, resume and offline catalog |
| Roku | SceneGraph/BrightScript | Native channel, remote navigation, Video node, Registry resume, format label |
| Android XR | Kotlin, SceneCore, Media3 | Independent full-space APK, incoming-URI autoplay, flat/equirectangular surfaces, mono/SBS/top-bottom/MV-HEVC, HDR surface metadata, pause/resume lifecycle |
| Meta Quest | Android NativeActivity, Media3, OpenXR C++, OpenGL ES | Independent arm64 APK, VIEW-intent autoplay, decoded external texture to OpenXR swapchain, flat/equirectangular compositor layers, mono/SBS/top-bottom crops, lifecycle pause/resume, headset capability probes |

The immersive projection contract recognizes flat, wide-FOV, fisheye 180°, equirectangular 180°/360°, equi-angular cubemap, mono, side-by-side, top-bottom, and MV-HEVC/multiview media. The native session core rejects unsupported resolution, codec, projection, stereo, dynamic-range, and spatial-audio combinations before allocating runtime resources, then provides deterministic ready/play/pause/seek/end/stop behavior and per-eye texture bindings.

Android XR is pinned to `androidx.xr.scenecore:scenecore:1.0.0-beta01`. Its direct `SurfaceEntity` path covers flat, wide-FOV, equirectangular 180°/360°, packed stereo, and MV-HEVC. It blocks fisheye and EAC/cubemap sources with an explicit native-mesh requirement instead of showing incorrect UV mapping. The Quest package is pinned to Media3 1.10.1, the official Khronos `openxr_loader_for_android:1.1.62`, NDK `29.0.14206865`, and SDK CMake `4.1.2`. Quest 0.2.0 decodes incoming content/file/HTTP(S) VIEW intents to a `SurfaceTexture`, copies the external texture into an OpenXR swapchain, and submits flat or equirectangular KHR layers with per-eye mono/SBS/top-bottom crops. Wide-FOV, fisheye, cubemap, multiview, and runtimes without the equirectangular extension are explicitly blocked until the required mesh/texture-array renderer is implemented.

## Verification commands

```text
pnpm test
pnpm build
node scripts/stremio-addons.mjs
node --test scripts/tv-core.node-test.mjs
node scripts/build-tv-packages.mjs
sh scripts/openxr-core-test.sh
sh scripts/quest-layer-test.sh
swift test --package-path native/apple --scratch-path /tmp/openstream-apple-build --jobs 1
gradle --no-watch-fs --project-cache-dir /tmp/openstream-gradle-project-cache -p native/android-xr testDebugUnitTest
gradle --no-watch-fs --project-cache-dir /tmp/openstream-gradle-project-cache -p native/android-xr lintDebug
gradle --no-watch-fs --project-cache-dir /tmp/openstream-gradle-project-cache -p native/android-xr assembleDebug
adb install -r native/android-xr/app/build/outputs/apk/debug/app-debug.apk
OPENSTREAM_ANDROID_XR_BUILD_DIR=/tmp/openstream-android-xr-scene-build gradle --no-watch-fs --project-cache-dir /tmp/openstream-xr-scene-gradle -p native/android-xr-scene testDebugUnitTest lintDebug assembleDebug
OPENSTREAM_QUEST_BUILD_DIR=/tmp/openstream-meta-quest-build gradle --no-watch-fs --project-cache-dir /tmp/openstream-meta-quest-gradle -p native/meta-quest lintDebug assembleDebug
```

If Android packaging on an external macOS volume reports that it cannot delete AGP's generated `packageDebug/tmp` folder, route generated output to local temporary storage:

```text
OPENSTREAM_ANDROID_BUILD_DIR=/tmp/openstream-android-build gradle --no-watch-fs --project-cache-dir /tmp/openstream-gradle-project-cache -p native/android-xr assembleDebug
adb install -r /tmp/openstream-android-build/outputs/apk/debug/app-debug.apk
```

## Last verified build

Verified on a Lenovo TB132FU on August 5, 2026:

- 43 Android unit tests, 57 shared-core Google TV unit tests, both Android lint runs, and both debug APK builds passed.
- 29 web tests, 35 server/script tests, TypeScript checking, and the production web build passed.
- All four built-in discovery add-ons returned valid live catalogs; the configured private stream source returned direct routes without exposing its configuration.
- Live remote playback recovered from an expired signed route by selecting an alternate, then resumed the same title from its stable saved position.
- Back from player/details and swipe-up from the player completed without a crash or ANR; swipe-up returned to OpenPanel.
- A live HTTPS add-on stream selected Dolby Vision video and E-AC-3 audio, auto-enabled English captions, and passed pause/resume. The provider's Atmos label is accepted only when the selected audio carrier is Atmos-capable.
- Native Apple package tests passed, and iOS/iPadOS, tvOS, and visionOS simulator-SDK application bundles compiled successfully. Simulator runtimes were not installed on this Mac.
- LG/Samsung/Hisense browser execution passed remote navigation, details, play, pause/resume, brief Dolby Vision/Atmos signaling, and automatic offline-only filtering. Roku passed SceneGraph package validation; vendor emulators and store signing tools were not installed.
- The host-built C++20 OpenXR suites passed geometry, inward sphere, fisheye, cubemap, per-eye stereo UV, multiview texture layers, runtime capability negotiation, and playback-session transition tests.
- On August 6, 2026, the Android XR SceneCore unit tests, lint, and APK build passed against SceneCore 1.0.0-beta01. Manifest inspection confirmed the spatial XR API declaration and managed full-space launch mode.
- On August 6, 2026, Meta Quest 0.2.0 compiled with NDK 29/CMake 4.1.2 and Media3 1.10.1, linked Khronos loader 1.1.62, passed lint plus launch/layer/stereo-crop policy tests, and produced an APK containing DEX, the loader, and the native engine. Manifest inspection confirmed content/file/HTTP(S) video VIEW intents, the immersive HMD category, head-tracking requirement, loader permissions, and runtime-broker queries. The native engine now owns the Media3 surface, external-texture copy shader, OpenXR video swapchain, flat/equirectangular composition layers, and Android/OpenXR pause-resume bridge.
- No Android XR or Quest headset was attached to this Mac. End-to-end headset frames, controllers, decoder limits, HDR/display output, spatial audio, comfort, store signing, and vendor submission remain physical-device checks; they are not represented as passed.
