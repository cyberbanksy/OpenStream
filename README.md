# OpenStream.orgista.com

OpenStream is an independent, tvOS-first media application under the Orgista development umbrella. It combines local, NAS, cloud, and add-on discovery with capability-aware playback, request/download-manager handoff, and immersive media routes.

## Kids Mode

First launch asks who watches on the device. Choosing **Kids Mode** walks a guardian through a rating cap (Little kids G/TV-Y, Big kids PG/TV-PG, or Teens PG-13/TV-14), an optional blocklist of titles or words, and an explicit irreversible confirmation. Once enabled:

- Titles above the cap, blocklisted matches, and all unrated content never appear in Discover, Library, Live, or Search, and refuse to play.
- Connections, activity, request handoff, and add-on management are hidden.
- There is no PIN, setting, or hidden exit. The kids lock lives with the app data; deleting or editing the stored profile only fails closed to the strictest cap. The only reset is uninstalling OpenStream (removing its app data) and setting it up again.

## NAS folders & local transcoding

A **NAS folder** connection points the loopback gateway at a directory this machine already mounts (for example an SMB share under `/Volumes`). Files stream with HTTP Range support for instant seeking. When the device cannot direct-play a format, the gateway transcodes locally with ffmpeg — VideoToolbox hardware encoders when available — always capped at the device's probed resolution and never upscaling. Playback falls back to the transcode route automatically when a direct route fails, and the player offers a manual Transcode control. Stremio add-on streams proxied by the gateway can be transcoded the same way.

## Logging

The gateway writes redacted JSONL logs (one file per day, `logs/` by default, `OPENSTREAM_LOG_DIR` to override) covering every request, connection test, transcode lifecycle, and errors the browser ships to `POST /api/logs`. Uncaught client errors are captured automatically, so a test session can be replayed end to end from one log file.

## Design handoff

- Editable Figma file: https://www.figma.com/design/A8dE5NRzjGjwAMVSfdaeBW
- Figma canvas: `00 Design Foundations`, `01 Discover`, `02 Library & NAS`, `03 Title Details`, and `04 Sources & Add-ons`
- Local concept board and verified 1920x1080 renders: `design/`
- Product and interoperability research: `docs/research/`
- Capability matrix and audit reports: `docs/architecture/` and `docs/audits/`
- OSS design editor: OpenPencil 0.13.2, installed at `/Applications/OpenPencil.app`
- OpenPencil Make-style test: `design/openpencil-lab/`
- Conversational design agent: `design/make-agent/`

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
pnpm openpencil:smoke
pnpm make:new -- "Design a tvOS media dashboard"
pnpm make -- "Make the focused card more obvious"
node scripts/live-smoke.mjs "<secure launch url>" "<nas folder>"   # live E2E: onboarding, NAS, direct play, transcode
```

On this workstation the Vite cache must live off the project volume: run dev with `OPENSTREAM_VITE_CACHE_DIR="$HOME/Library/Application Support/Orgista/OpenStream/vite-cache"`. `pnpm qa` audits every screen plus the onboarding/Kids Mode flow (rating filter, irreversibility, tamper fail-closed) and needs `CHROME_FOR_TESTING_PATH`.

`pnpm dev` starts both Vite and the loopback gateway. Open the one-time secure launch URL printed in the terminal; it establishes a per-origin browser session, removes the launch capability from the address bar, and never creates a host-wide loopback cookie. Standalone gateway launches are intended for controlled integrations and require an explicit high-entropy `OPENSTREAM_GATEWAY_TOKEN` shared only with their trusted proxy.

The responsive application supports remote/keyboard focus, pointer input, and compact mobile/XR browser layouts. The loopback gateway keeps connection credentials and LAN service access out of browser state. Signed XR media locators use one-time, owner-bound handoffs; viewer navigation contains only an opaque fragment identifier.
The OpenPencil smoke test requires the desktop app to be open. It produces an editable `.fig`,
a verified PNG, round-trip JSX, and a machine-readable report.
Both OpenPencil workflows verify the listener's operating-system owner and bind its process to a
publisher-verified, pinned application parent chain plus the expected MCP package version and
entrypoint digest before sending MCP traffic. A verified OpenPencil upgrade therefore requires
reviewing and updating the app and MCP pins in `scripts/openpencil-security.mjs`.

## Native targets

- `native/apple`: Swift package for macOS, tvOS, and visionOS using public AVFoundation/AVKit APIs.
- `native/android-xr`: Android/Android XR compatibility target using Media3, runtime HDR/decoder probes, and standards-based immersive handoff.
- Debug APK: `native/android-xr/dist/openstream-android-xr-debug.apk`

## Security boundary

OpenStream does not bypass DRM, extract protected media or keys, or claim playback support from metadata alone. Private-network connections require explicit consent. The development server uses a one-time launch capability and HttpOnly browser session, its proxy authenticates to the loopback-only gateway with a separate launch secret, and gateway records and media capabilities are owner-bound. Credentials remain server-side.
