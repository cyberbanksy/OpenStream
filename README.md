# OpenStream

OpenStream is an independent, multi-platform media application maintained by [Cyberbanksy](https://github.com/cyberbanksy). It combines local, NAS, cloud, and add-on discovery with capability-aware playback, request/download-manager handoff, and immersive media routes.

Operational configuration, metadata credential rules, and playback behavior are documented in [`MASTER.md`](MASTER.md).

The account-free Netflix regional Top 10 cache, its daily ranking algorithm, fallback, and offline emulator are documented in [`docs/netflix-top10.md`](docs/netflix-top10.md).

## Content and source policy

Production builds ship with no demo titles, channels, YouTube subscriptions, Stremio add-ons, IPTV playlists, or sample server connections. Viewers add every content source through **Sources**, **Settings**, or **Web Management**, and those choices remain in private application storage. Stremio discovery accepts only movie and series catalogs; book and arbitrary `other` catalogs are ignored.

## Kids Mode

First launch asks who watches on the device. Choosing **Kids Mode** walks a guardian through a rating cap (Little kids G/TV-Y, Big kids PG/TV-PG, or Teens PG-13/TV-14), an optional blocklist of titles or words, and an explicit irreversible confirmation. Once enabled:

- Titles above the cap, blocklisted matches, and all unrated content never appear in Discover, Library, Live, or Search, and refuse to play.
- Connections, activity, request handoff, and add-on management are hidden.
- There is no PIN, setting, or hidden exit. The kids lock lives with the app data; deleting or editing the stored profile only fails closed to the strictest cap. The only reset is uninstalling OpenStream (removing its app data) and setting it up again.

## NAS folders & local transcoding

A **NAS folder** connection points the loopback gateway at a directory this machine already mounts (for example an SMB share under `/Volumes`). Files stream with HTTP Range support for instant seeking. When the device cannot direct-play a format, the gateway transcodes locally with ffmpeg — VideoToolbox hardware encoders when available — always capped at the device's probed resolution and never upscaling. Playback falls back to the transcode route automatically when a direct route fails, and the player offers a manual Transcode control. Stremio add-on streams proxied by the gateway can be transcoded the same way.

## Logging

The gateway writes redacted JSONL logs (one file per day, `logs/` by default, `OPENSTREAM_LOG_DIR` to override) covering every request, connection test, transcode lifecycle, and errors the browser ships to `POST /api/logs`. Uncaught client errors are captured automatically, so a test session can be replayed end to end from one log file.

## Screens

- `?screen=discover`
- `?screen=library`
- `?screen=details`
- `?screen=live`
- `?screen=connections`
- `?screen=search`
- `?screen=activity`
- `?screen=settings`
- `?screen=player`
- `/xr/viewer`

## Local development

```bash
pnpm install
pnpm dev
pnpm qa
pnpm test
pnpm build
pnpm netflix:top10:emulate
node scripts/live-smoke.mjs "<secure launch url>" "<nas folder>"   # live E2E: onboarding, NAS, direct play, transcode
```

On this workstation the Vite cache must live off the project volume: run dev with `OPENSTREAM_VITE_CACHE_DIR="$HOME/Library/Application Support/OpenStream/vite-cache"`. `pnpm qa` audits every screen plus the onboarding/Kids Mode flow (rating filter, irreversibility, tamper fail-closed), writes temporary captures outside the repository by default, and needs `CHROME_FOR_TESTING_PATH`.

`pnpm dev` starts both Vite and the loopback gateway. Open the one-time secure launch URL printed in the terminal; it establishes a per-origin browser session, removes the launch capability from the address bar, and never creates a host-wide loopback cookie. Standalone gateway launches are intended for controlled integrations and require an explicit high-entropy `OPENSTREAM_GATEWAY_TOKEN` shared only with their trusted proxy.

The responsive application supports remote/keyboard focus, pointer input, and compact mobile/XR browser layouts. The loopback gateway keeps connection credentials and LAN service access out of browser state. Signed XR media locators use one-time, owner-bound handoffs; viewer navigation contains only an opaque fragment identifier.
## Native targets

- `native/android-xr`: native Android tablet/mobile app with Media3, add-ons, metadata, recommendations, device-fit offline downloads, resume, captions, HDR/Dolby indicators, and XR routing.
- `native/android-xr-scene`: independent Android XR full-space app using SceneCore `SurfaceEntity` and Media3, with incoming-URI autoplay, packed stereo/MV-HEVC selection, and explicit HDR surface metadata.
- `native/google-tv`: independent Google TV application ID with Leanback launcher, remote focus, Media3 session support, and the shared Android core.
- `native/apple`: native SwiftUI foundations for iPhone, iPad, Apple TV, and Apple Vision Pro with an empty source-driven production shell and shared capability/offline policy code.
- `native/smart-tv`: vendor-native packaged web applications for LG webOS, Samsung Tizen, and Hisense VIDAA.
- `native/roku`: native Roku SceneGraph/BrightScript channel.
- `native/openxr`: C++20 inward-facing flat, wide-FOV, fisheye 180°, equirectangular 180°/360°, cubemap, SBS, top-bottom, and multiview projection plus capability/session core for Android XR and Meta Quest renderers.
- `native/meta-quest`: arm64 NativeActivity using the Khronos OpenXR Android loader, Media3 URI autoplay, OpenGL ES decoded-texture copies into OpenXR swapchains, flat/wide/equirectangular compositor layers, GPU fisheye and 3x2 EAC/cubemap conversion, packed-stereo eye crops, lifecycle pause/resume, runtime capability probes, and the shared immersive projection core.
- `brand/openstream-mark.svg` and `brand/openstream-app-icon.svg`: the canonical three-bar identity used across current platform targets.

Platform staging and core checks:

```sh
pnpm test:tv
pnpm build:tv
pnpm test:openxr
sh scripts/quest-layer-test.sh
OPENSTREAM_ANDROID_XR_BUILD_DIR=/tmp/openstream-android-xr-scene-build gradle --no-watch-fs -p native/android-xr-scene testDebugUnitTest lintDebug assembleDebug
OPENSTREAM_QUEST_BUILD_DIR=/tmp/openstream-meta-quest-build gradle --no-watch-fs -p native/meta-quest lintDebug assembleDebug
```

Store signing, vendor certificates, and account-bound submissions remain outside source control.

## Security boundary

OpenStream does not bypass DRM, extract protected media or keys, or claim playback support from metadata alone. Private-network connections require explicit consent. The development server uses a one-time launch capability and HttpOnly browser session, its proxy authenticates to the loopback-only gateway with a separate launch secret, and gateway records and media capabilities are owner-bound. Credentials remain server-side.
