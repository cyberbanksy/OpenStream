# Media capability matrix

OpenStream separates metadata identification from playback proof. A file label, codec string, or HDR-eligible display is never treated as a decoder guarantee.

Support states:

- **Supported**: a public platform path is implemented and the current capability report accepts it.
- **Runtime check**: asset, decoder, display, or headset validation is still required.
- **Fallback**: the route may use a compatible base layer or tone-mapped presentation.
- **Unsupported**: OpenStream blocks the route instead of silently presenting the wrong format.

## Dynamic range

| Format | Web | tvOS | visionOS | Android XR / Samsung XR | Meta Quest |
| --- | --- | --- | --- | --- | --- |
| SDR | Supported | Supported | Supported | Supported | Supported |
| HDR10 | Runtime check | Supported platform path | Supported platform path | Runtime display + decoder check | Runtime check |
| HDR10+ | Runtime check | Asset/runtime check | Asset/runtime check | Runtime display + decoder check | Runtime check |
| HLG | Runtime check | Supported platform path | Supported platform path | Runtime display + decoder check | Runtime check |
| Dolby Vision | Runtime check | Asset + decoder check | Asset + decoder check | Runtime display + decoder check | Runtime check |

The Apple inspector parses `dvcC` and `dvvC` records, including profile, level, RPU, enhancement/base-layer flags, and base-layer compatibility identifier. Profile 8 is represented as P8.1 (HDR10-compatible), P8.2 (SDR-compatible), or P8.4 (HLG-compatible) when explicitly signaled. Transfer function is only a fallback when the compatibility identifier is absent or unknown.

P5, P7, P8.1, P8.2, P8.4, and unknown profiles remain runtime checks. Unsupported or future profile numbers remain unknown rather than being misreported.

Android probes `Display.HdrCapabilities`, advertised HEVC/AV1 Main10 profiles, and Dolby Vision decoder profiles. Codec-string inspection reports Dolby profile and level but does not infer base-layer compatibility that the string does not contain.

## Immersive projection

| Projection | WebXR | tvOS | visionOS | Android XR / Samsung XR | Meta Quest |
| --- | --- | --- | --- | --- | --- |
| Flat | Supported | Supported | Supported | Supported | Supported |
| Mono/stereo 180 | Runtime WebXR | Unsupported | Public AVKit path | Runtime WebXR/OpenXR path | Runtime WebXR/OpenXR path |
| Mono/stereo 360 | Runtime WebXR | Unsupported | Public AVKit path | Runtime WebXR/OpenXR path | Runtime WebXR/OpenXR path |
| Wide FOV / APMP | Runtime WebXR | Unsupported | Public AVKit experience path | Runtime check | Runtime check |
| Apple Immersive Video | Unsupported | Unsupported | Public AVKit experience path | Unsupported | Unsupported |

The browser viewer implements 180, 360, and wide projections with mono, side-by-side, and top-bottom stereo layout. It validates source URLs and supports local file selection. Browser HDR preservation depends on the browser, compositor, headset, and media pipeline; OpenStream therefore does not promise Dolby Vision or HDR10+ output through WebXR.

visionOS uses public projection metadata and `AVExperienceController` rather than a custom projection for Apple Immersive Video. Hardware validation with representative licensed/test clips is still required before release claims.

## Route negotiation

Every candidate is scored using:

- container, video codec, audio codec, dynamic range, projection, resolution, and frame rate;
- direct-play versus transcode status;
- source verification, bitrate, latency, and availability;
- current platform probe rather than a global compatibility table alone.

The player displays the selected route and whether it is ready, requires a probe, or is blocked. Protected playback remains in the native platform adapter; browser routes do not claim a protected native path.

## Verification boundary

Implemented and compile-tested:

- TypeScript capability negotiation and tests;
- Apple metadata inspection and profile parsing tests;
- tvOS 26.2 and visionOS 26.2 package compilation;
- Android runtime capability probe, unit tests, lint, and debug build;
- WebXR projection math and source-policy tests.

Still requires physical-device and licensed-media verification:

- output-mode switching and display-chain behavior;
- Dolby Vision/HDR10+ decoding for each model and profile;
- Apple Immersive Video and APMP presentation on Vision Pro;
- OpenXR compositor integration and stereo validation on Samsung XR and Meta Quest;
- browser HDR behavior on each headset/browser release.

