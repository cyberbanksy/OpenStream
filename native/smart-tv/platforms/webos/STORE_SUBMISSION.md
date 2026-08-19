# OpenStream for LG webOS TV

## Deliverables

- Packaged app source: `dist/tv/webos`
- Deterministic source archive: `dist/tv/artifacts/openstream-webos-1.0.0.source.zip`
- Seller Lounge icon: `native/smart-tv/platforms/webos/store/icon-400.png`
- Packaged icons: `icon-80.png` and `icon-130.png`
- FHD splash: `splash-1920x1080.png`

Run `node scripts/build-tv-packages.mjs` from the repository root. When the official webOS TV CLI is installed and `ares-package` is on `PATH`, the build also writes an `.ipk` into `dist/tv/artifacts`. You can then install and launch it on a registered Developer Mode TV:

```sh
ares-install --device <device-name> dist/tv/artifacts/<package>.ipk
ares-launch --device <device-name> com.cyberbanksy.app.openstream
```

Official references: [CLI introduction](https://webostv.developer.lge.com/develop/tools/cli-introduction), [CLI development guide](https://webostv.developer.lge.com/develop/tools/webos-tv-cli-dev-guide), [appinfo.json](https://webostv.developer.lge.com/develop/references/appinfo-json).

## 4K behavior

LG packaged web apps declare either a 1920×1080 or 1280×720 graphics surface, so `appinfo.json` correctly uses FHD. OpenStream does not transcode or resize playback URLs in JavaScript: the URL is passed to the television's native video pipeline. A compatible UHD model can therefore play 3840×2160 content while the application UI remains FHD.

Certification should cover HTTP and HLS streams at 2160p, seek/resume, codec failure messaging, HDR labels, subtitles, and audio-track selection on the oldest supported TV and at least one current UHD model. Support depends on the model's codecs, container, DRM, and firmware. See LG's [streaming protocol and DRM guide](https://webostv.developer.lge.com/develop/specifications/streaming-protocol-drm).

The local UHD smoke asset is `test-media/Glacier Run (2026).mkv` (3840×2160 HEVC Main 10). It is a test input only and is not bundled in the application or store archive.

## Store and remote checklist

- Test pointer selection plus five-way navigation (Left, Right, Up, Down, OK).
- Test Back (key code 461) from details, playback, a secondary section, and the app root.
- Test Play, Pause, and Stop remote keys during playback.
- Confirm the empty Sources state on a clean installation; no catalog or video ships in the package.
- Wire the production host bridge to `window.OpenStreamTV.configure({ catalog, sources })`, then verify only user-configured video sources appear.
- Verify app launch, relaunch, offline behavior, screensaver behavior, and memory use on physical LG hardware.
- Capture store screenshots at the required Seller Lounge dimensions; do not substitute the packaged icon for the separate 400×400 seller icon.
- Complete privacy, content-rights, regional, age-rating, support, and data-safety declarations in Seller Lounge.

Remote behavior follows LG's [Magic Remote guidance](https://webostv.developer.lge.com/develop/guides/magic-remote) and [Back button guidance](https://webostv.developer.lge.com/develop/guides/back-button). Before submission, use LG's [self-checklist](https://webostv.developer.lge.com/distribute/app-self-checklist) and [approval-process guide](https://webostv.developer.lge.com/distribute/app-approval-process).
