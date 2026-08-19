# OpenStream smart-TV targets

The shared television app is remote-first HTML5 with direct `<video>` playback, saved resume state, system captions/audio controls, brief 4K/HDR/Atmos labels, network detection, cached catalog metadata, and recommendation ranking. It contains no demo catalog or bundled video: only media supplied by a configured OpenStream source is shown. Roku uses a separate native SceneGraph/BrightScript client rather than a browser wrapper.

Platform packages:

- `webos`: LG packaged web app (`appinfo.json`)
- `tizen`: Samsung TV web app (`config.xml`)
- `vidaa`: Hisense/VIDAA hosted HTML5 app metadata
- `roku`: Roku SceneGraph channel (`manifest`, `source`, and `components`)

Run `node scripts/build-tv-packages.mjs` from the repository root to create deterministic staging folders and source archives under `dist/tv`. If LG's `ares-package` is available, the same build also emits a webOS `.ipk`. On macOS the build normalizes Roku artwork to required FHD dimensions. Vendor signing and store submission still require each vendor's developer account, signing profile, and certification hardware.

The LG package declares a 1920×1080 application surface because webOS packaged web apps support FHD or HD UI resolutions. Video URLs are assigned directly to the native `<video>` element, so compatible UHD LG televisions can decode and display a 3840×2160 stream without the application downscaling it. Codec, container, DRM, HDR, and audio support still vary by television generation.

The host integration surface is `window.OpenStreamTV.configure({ catalog, sources })`. Playback URLs are deliberately not retained in local storage; the paired host supplies fresh routes on launch. See `platforms/webos/STORE_SUBMISSION.md` for LG packaging and certification notes.
