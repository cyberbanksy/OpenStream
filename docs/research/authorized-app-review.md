# Authorized app review

Review date: 2026-07-15 and 2026-07-16

## Scope and boundaries

This is a clean-room product and interoperability review of the locally installed Apple TV and Infuse applications. The supplied authorization permits bundle/resource inspection, static analysis, runtime observation, and UI capture for internal research. This review does not access user data, extract DRM keys or protected media, bypass access controls, or redistribute proprietary code or assets.

The output is intentionally architectural and behavioral. OpenStream uses original code, generated artwork, public platform APIs, and its own visual system.

## Apple TV app

Reviewed target: `/System/Applications/TV.app`

- Version observed: 1.5.6 on macOS 15.7.4.
- Publicly observable framework surface includes AVFoundation, AVKit, WebKit, MediaRemote, Picture in Picture, store/library services, and system media controls.
- Resource inventory includes compiled interface resources, localized strings, asset catalogs, and app extensions. Private Apple entitlements and frameworks are not available to third-party software and are not design targets for OpenStream.
- The visible product hierarchy is content-first: high-impact artwork, short metadata rows, focus-driven rails, immediate play affordances, lightweight navigation, and playback status that appears only when useful.
- Focus treatment enlarges the selected item, adds a clear outline/elevation cue, and reveals an action overlay without moving the surrounding rail.
- Format badges remain subordinate to title, artwork, and primary action. Download and playback state are represented compactly rather than as separate dashboard cards.

What OpenStream borrows as a general pattern:

- predictable remote-focus geometry;
- stable card dimensions while focus and hover states change;
- progressive disclosure of play, route, and format information;
- content artwork as the first viewport signal;
- short, scannable labels and platform-native playback controls.

What OpenStream does differently:

- source provenance and route quality are visible before playback;
- local, cloud, add-on, request, and download-manager workflows share one connection model;
- capability claims distinguish supported, runtime-check, fallback, and unsupported states;
- the brand, logo, artwork, colors, spacing, and component shapes are original.

## Infuse

Reviewed target: `/Applications/Infuse.app`

- Version observed: 8.4.8 build 5690, universal macOS binary, built with the macOS 26.4 SDK.
- The signed app uses the App Sandbox. Observable entitlements include network client/server access, multicast and Wi-Fi information, user-selected files, Movies-folder access, and iCloud/CloudKit capabilities.
- Observable framework and resource inventory shows a broad media stack: AVFoundation/AVKit, VideoToolbox, Metal, FFmpeg-family libraries, Core Data/CloudKit, Google authentication and REST support, dependency injection, reactive UI infrastructure, and network-file protocol libraries.
- Visible/localized product areas include Home, Library, Favorites, Up Next, collections, pre-playback detail, audio/subtitle/video controls, download queues, metadata/artwork, Trakt, speed testing, AirPlay/Cast, and source management.
- Observable source categories include SMB, NFS, WebDAV, FTP, UPnP, Plex, Emby, Jellyfin, Google Drive, Dropbox, OneDrive, Box, and other cloud providers.
- The product pattern is source-depth rather than storefront minimalism: connection health, protocol breadth, metadata repair, playback controls, and library maintenance are first-class.

What OpenStream borrows as a general pattern:

- source setup grouped by user intent rather than protocol jargon;
- rich pre-playback route and track information;
- separate queue/activity visibility for long-running operations;
- explicit local-network and cloud authorization states;
- one library surface across heterogeneous providers.

What OpenStream does differently:

- Stremio discovery, Sonarr/Radarr requests, and SABnzbd/NZBGet handoff are modeled as separate trust boundaries;
- a Download action sends a request to the configured manager and never implies a direct device download;
- immersive media is negotiated through native visionOS or standards-based WebXR routes;
- proprietary playback behavior is not copied or represented as guaranteed support.

## Product synthesis

Apple TV contributes focus clarity and content hierarchy. Infuse contributes source and playback workflow depth. OpenStream combines those principles in an original interface centered on three questions:

1. What is this title?
2. Where will it play from?
3. Can this device faithfully play this exact format?

That structure is reflected in Discover, Library, Details, Live, Connections, Search, Activity, Settings, Player route selection, and the immersive viewer.

## Environment and forward compatibility

Verified locally with Xcode 26.3 and the tvOS/visionOS 26.2 SDKs. Public beta documentation for tvOS 27, visionOS 27, macOS 27, and Xcode 27 was reviewed for forward compatibility. OpenStream does not compile in unverified 27-only APIs. It uses an explicit SwiftUI tint for tvOS focus/accent behavior and keeps simulator-only WebXR limitations out of capability claims.

Primary references:

- Apple AVFoundation HDR and Dolby Vision guidance: https://developer.apple.com/news/?id=rwbholxw
- Apple immersive media overview: https://developer.apple.com/documentation/technologyoverviews/immersive-media/
- Apple tvOS 27 release notes: https://developer.apple.com/documentation/tvos-release-notes/tvos-27-release-notes
- Apple visionOS 27 release notes: https://developer.apple.com/documentation/visionos-release-notes/visionos-27-release-notes
- Apple Xcode 27 release notes: https://developer.apple.com/documentation/xcode-release-notes/xcode-27-release-notes
- Dolby Vision profile overview: https://professionalsupport.dolby.com/s/article/What-is-Dolby-Vision-Profile?language=en_US

