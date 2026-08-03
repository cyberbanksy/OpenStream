# OpenStream Apple playback core

This Swift package is the public-framework playback and capability layer for macOS, tvOS, and visionOS.

- `AppleAssetInspector` reads codec, transfer function, Dolby Vision configuration atoms, stereo flags, and public projection metadata from an actual asset.
- `DolbyVisionConfiguration` parses profile, level, RPU/EL/BL flags, and base-layer compatibility identifiers. Profile 8 distinguishes P8.1, P8.2, and P8.4 when signaled, with transfer-function fallback for incomplete records.
- `AppleCapabilityProbe` uses `AVPlayer.eligibleForHDRPlayback` only as a broad eligibility signal.
- `OpenStreamVisionPlayerController` uses the visionOS 26 `AVExperienceController` immersive experience for APMP/AIV assets. It does not invent a custom projection for Apple Immersive Video.
- HDR10+ per-frame metadata remains an asset/runtime check. A container label alone never claims decoder support.

Run the host-compatible tests with `swift test`. The current source is compile-verified against the installed tvOS 26.2 and visionOS 26.2 SDKs with explicit Swift target triples. Build a signed app target and validate representative media on physical hardware before making device/profile support claims.
