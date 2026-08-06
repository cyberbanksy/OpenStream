# OpenStream smart-TV targets

The shared television app is remote-first HTML5 with direct `<video>` playback, saved resume state, system captions/audio controls, brief HDR/Atmos labels, network detection, cached catalog metadata, and recommendation ranking. Roku uses a separate native SceneGraph/BrightScript client rather than a browser wrapper.

Platform packages:

- `webos`: LG packaged web app (`appinfo.json`)
- `tizen`: Samsung TV web app (`config.xml`)
- `vidaa`: Hisense/VIDAA hosted HTML5 app metadata
- `roku`: Roku SceneGraph channel (`manifest`, `source`, and `components`)

Run `node scripts/build-tv-packages.mjs` from the repository root to create deterministic staging folders under `dist/tv`. On macOS the build also normalizes Roku artwork to required FHD dimensions. Vendor signing and store submission still require each vendor's developer account, signing profile, and certification hardware.
