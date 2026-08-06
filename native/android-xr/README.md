# OpenStream Android

The native Android app: Kotlin + Jetpack Compose UI over the Media3 playback
engine. This is the product on Android — no web runtime.

## What it does

- First-run onboarding with **irreversible Kids Mode**: a rating cap
  (Little kids / Big kids / Teens), a guardian blocklist, and an explicit
  cannot-be-reverted confirmation. The lock lives in app data; deleting or
  editing the stored profile fails closed to the strictest cap, and the only
  reset is uninstalling the app. Unrated content never plays in Kids Mode.
- Discover / Library / Search / Details with rating chips everywhere.
- **Direct SMB NAS playback** (jcifs-ng SMB2/3 → custom Media3 DataSource with
  seeking). ExoPlayer's extractors and the device's hardware decoders handle
  MKV/MP4/TS, HEVC and AV1 — no gateway, no transcoding for anything the
  device can decode.
- **Stremio add-ons**: validate a manifest, resolve direct HTTPS streams per
  title, play them natively.
- Settings surface real decoder/display probes (HDR10/HDR10+/HLG/Dolby
  Vision, HEVC/AV1 Main10) from the Media3 probe module.

The Media3 capability probe/demo remains available at
`.MainActivity` (explicit intent only); the launcher is `.NativeAppActivity`.

## Build and test

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test assembleDebug
pnpm test:android-device
```

The attached-device smoke verifier is non-destructive: it checks the installed
API level, launches the Compose activity, validates the visible app shell,
navigates Discover → Library → Discover, checks the process log for a crash or
ANR, and preserves the device's prior sleep state. Set
`OPENSTREAM_SMOKE_NAVIGATE=0` to perform the launch and shell checks without
changing the current screen.

On this workstation Gradle cannot run on the project volume — rsync the
module to a local directory first (see the repo docs). The debug APK is
written to `app/build/outputs/apk/debug/app-debug.apk` and a copy is kept at
`dist/openstream-android-xr-debug.apk`.

## Architecture notes

- AGP 9 built-in Kotlin (no kotlin-android plugin) + Compose compiler plugin.
- Dependencies are deliberately minimal: Compose BOM, activity-compose,
  Media3, jcifs-ng. HTTP is HttpURLConnection + org.json; icons are the core
  set plus two hand-drawn vectors.
- Domain logic (`domain/`) mirrors the shared product rules (ratings,
  content policy, profile lock) and is what the Swift/macOS target ports next.
