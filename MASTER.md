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

For local projects and LLM tooling, never put an OMDb value in a prompt, command argument, tracked file, URL log, or generated artifact. On this Mac the private reusable copy is stored in Login Keychain under service `com.cyberbanksy.openstream.omdb`; the wrapper checks `OMDB_API_KEY` first and then reads that Keychain item itself. Other trusted hosts can provide the same `OMDB_API_KEY` environment contract; `.env.local` is ignored by this repository when a local environment file is required. A caller supplies only an IMDb id:

```text
pnpm metadata:omdb -- tt3896198
```

The wrapper in `scripts/omdb-lookup.mjs` calls OMDb over HTTPS, keeps the credential inside the process, validates the IMDb id, and returns sanitized title/rating metadata without returning or logging the request URL. An LLM should receive this wrapper as a narrow `lookup_omdb(imdb_id)` tool; the model never needs access to the raw key or direct Keychain access.

When an add-on supplies an IMDb ID, OpenStream resolves the canonical TMDB record through `/find/{imdb_id}` before falling back to a title/year search. Detail responses fill missing overview, year, certification, runtime, poster, backdrop, genres, cast, director, and tagline. Enrichment is merged back into every matching discovery category and the app-private catalog cache; later six-hour catalog refreshes retain those richer cached fields instead of replacing them with a sparse provider record. A bounded background warmer enriches up to 24 sparse, likely-next titles four at a time, and cached series records retain their episode metadata. IMDb, Rotten Tomatoes, and Metacritic fields are merged independently so an IMDb-only catalog rating cannot discard later OMDb critic scores.

Credentials pasted into a development chat are not treated as installed configuration or copied automatically. Provisioning requires the user's explicit request, as it did for this installation. Enter credentials in Settings on each device, store a workstation credential in an operating-system secret store, or provision a metadata server through its process environment. The long JWT and 32-character key supplied during development are TMDB credentials, not IMDb credentials. Rotten Tomatoes/IMDb/Metacritic ratings require a separate OMDb key.

The metadata server reads credentials from its process environment:

```text
TMDB_READ_TOKEN=<TMDB v4 read token>
# or
TMDB_API_KEY=<TMDB v3 API key>
OMDB_API_KEY=<OMDb key, for the local lookup wrapper>
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

- Selecting a larger title tile opens its details, where one primary **Play** action begins playback automatically. The route policy prefers a completed device download, then a local/NAS candidate, then the highest-quality add-on stream compatible with the current display, decoder, HDR, and audio capabilities.
- Featured **Play** opens Details for a series so the episode list remains available. The series Play decision is local and deterministic: an unwatched show starts at Season 1 Episode 1 (specials are not treated as the default); the most recently unfinished episode resumes from its stored position; a completed episode advances to the next numbered episode; and a completed finale remains replayable. The Season dropdown and episode cards always allow an explicit override.
- Automatic preflight measurement is cellular-only. It uses bounded ramped Cloudflare transfers (100 KB, 500 KB, 2 MB, and 5 MB), discards the connection-warmup sample, and uses the median sustained result. If Cloudflare rejects an app-style probe with an access/rate/server response, the same official endpoint is retried once with same-origin browser headers and a cache-busting query. Unmetered Wi-Fi and Ethernet begin with the best device-compatible route instead of being downgraded by a handshake-dominated 1 MB sample. If on-demand startup remains buffered for five seconds, OpenStream measures that connection once and caches the result for later playback. The app does not request location or read an SSID for this policy.
- The player uses one slim centered preparation panel. On-demand remote buffering may say **Optimizing for your network**; Live TV uses the neutral **Loading** label and does not run the on-demand slow-start optimization path for a fixed IPTV feed. Tap/remote chrome shows only the title and a compact route such as **4K Stream**, **HD Local**, or **HD Offline**; provider names, seeds, file sizes, route counts, measured Mbps, and safety-buffer calculations remain out of the playback UI.
- Dolby Vision is selected only when the display path and decoder are both confirmed. Platform codec constants are mapped to bitstream profiles; the Lenovo TB132FU advertises P4/P5/P8/P9, so explicit P8 encodes are preferred while unsupported P7 remuxes are rejected. An unprofiled non-remux can be inspected at runtime, while an unprofiled remux is deferred behind a reliable HDR10/SDR route.
- Display resolution and decoder input limits are separate: the Lenovo's 2560×1480 panel can accept and scale a decoder-supported 3840×2160 stream. Track selection uses the 4K decoder cap and the physical panel as its viewport.
- Remote playback keeps up to 12 distinct direct routes. A preferred route receives two short retries for transient 408/429/5xx provider failures before the player advances automatically, retaining the current resume position. Alternate-route status never carries forward an unverified Dolby Vision claim. Private route URLs and error causes are never logged.
- Positions are saved every five seconds under a hash of the stable media identity (with a URI-hash migration fallback); raw NAS paths and signed stream URLs are not stored in playback preferences.
- Resume is offered after 15 seconds and is cleared in the last 30 seconds or after 95% completion.
- Player title, compact quality/route label, and back control are transient and follow touch/remote controller visibility.
- The status bar is hidden and the navigation bar is transparent. Swipe-up Home remains available; an upward player gesture from the lower portion also exits the activity.
- All system-back gestures use one navigation rule: player/details → last tab, tab → Discover, Discover → exit.

## Recommendations

Recommendations run locally and deterministically. The ranking gives highest priority to resumable titles, learns item-to-item genre affinity from recent and repeated playback, moves completed titles behind unwatched choices, and then considers release recency and metadata completeness. Viewing identifiers and NAS paths are hashed in app-private preferences; no viewing history is uploaded.

The design adapts the useful, small-device pieces of collaborative filtering without pretending a single viewer has a community-sized model:

- `rcz7795/Movie-Recommendation-System`: item similarity, rating prediction, and measurable evaluation informed the separation between interaction signals and ranking.
- GroupLens MovieLens: MovieLens 32M is the stable external benchmark to use for offline evaluation after reviewing its current license/terms. It is not bundled, redistributed, or fetched by the app.
- Recent r/vibecoding media projects: preserve existing behavior with small testable changes, ground recommendations in a known catalog, weight watch history by recency and repeat visits, and keep reusable state-free UI building blocks.

## Configured discovery sources

Production packages contain no Stremio manifests, demo titles, YouTube subscriptions, IPTV playlists, channels, or sample server connections. A request to add content means configuring it through **Sources**, **Settings**, or **Web Management** on the intended device; it never means compiling that content or provider URL into the repository. Fresh installs therefore open with an honest empty state.

User-configured catalog responses refresh in the background every six hours and fall back only to an app-private cache whose add-on identity and catalog still exist in current settings. Removing or disabling an add-on immediately removes its cached rails; cache data from the former compiled defaults is discarded during startup. Stremio discovery accepts only `movie` and `series` types, so book and arbitrary `other` catalogs cannot appear as video rails. Stream, metadata, subtitle, and recommendation resources remain independently usable when advertised by a configured add-on.

Global Search is not limited to those cached discovery rails. After a 350 ms debounce, it queries every enabled catalog route whose Stremio manifest declares the `search` extra, in parallel, then title-first ranks and merges those provider results with the local/offline library by IMDb identity. The current catalog is normalized once into a reusable search index instead of normalizing every title and synopsis on each keystroke; punctuation and case are still normalized for ranking, while provider-returned canonical aliases remain visible (for example, a `Se7en` query whose catalog title is `Seven`). Search requests are cancelled as the query changes and are skipped while offline. Run `node scripts/title-search-audit.mjs` for the repeatable 50-movie/50-series public catalog audit; the command prints only totals and failures and never reads private add-on configuration.

Netflix's signed-in home page is not a public catalog API and is personalized per profile, so OpenStream does not depend on account cookies or reverse-engineered member endpoints. The implemented account-free adapter reads the public regional **Trending Now** order, accepts the first ten unique validated public title links, polls every four hours, and publishes the cached result through authenticated `GET /api/catalog/netflix/top10`. Netflix describes the country list as updating daily; polling more frequently reduces discovery delay without pretending the ranking itself changes every four hours. If the regional surface changes or returns an error, the adapter falls back to Netflix's official latest weekly country TSV, then retains the last known-good snapshot if both sources fail. Cache writes are atomic and owner-only; a result becomes stale after 30 hours. This produces a public regional overall list, not a viewer's personalized homepage or a guaranteed copy of the signed-in Movies/TV rows. Configuration, response fields, sources, and deterministic offline emulation are documented in `docs/netflix-top10.md`. The GitHub project `thitirat-mnc/Netflix-top10-dataset` remains useful for analyzing official weekly country history, but OpenStream copies neither its code nor its data and does not use it for the daily feed.

Discover preserves the catalog structure supplied by each configured add-on instead of flattening providers together. Each enabled add-on is shown as its own group with separate category rails, and the cache retains only that current provider/category provenance across restarts.

For series, the Stremio `meta.videos` list is parsed into season-ordered episodes with stable episode stream IDs, thumbnails, titles, descriptions, runtimes, and the parent IMDb identity. Series details open on Season 1 when it exists, keep specials under Season 0, and the primary Play action resolves Season 1 Episode 1 through the same device/bandwidth-aware route policy as movies. Selecting any episode card resolves that episode's add-on stream ID directly.

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

## Android offline storage

- **Make available offline** always means a completed copy stored on the Android device; it is not an ARR/server availability status.
- The downloader considers direct NAS routes and configured direct-stream add-ons, then chooses the best resolution, codec, dynamic range, audio capability, and size for the device.
- **Settings → Offline downloads** selects built-in device storage or app-specific storage on a mounted SD card without broad file permissions.
- Storage location changes are disabled while any download exists, preventing a completed or partial download from being orphaned. The built-in location retains the legacy app-specific cache path so upgrades keep existing downloads playable.
- Version 2 offline records require the provider filename to match the requested title before any bytes are queued. A provider filename for another movie is rejected. Legacy downloads have no identity proof, are quarantined from offline playback/catalogs, and appear as **Replace unverified offline copy** on the online title screen instead of being silently trusted or deleted.
- Media3/provider content lengths that overflow a signed 32-bit field for files larger than 2 GB are repaired before progress is calculated. Completed records always show 100%; an active transfer with no trustworthy total shows the saved byte count instead of a false 0%. Details and Settings also show remaining device storage and the total bytes currently available offline.
- Series keep one Season dropdown directly beside Play. The dropdown contains only season names; after selection, one icon-only download action appears beside the selector for that season. Every episode card keeps its own icon-only action. An arrow means available to queue, a compact progress indicator means preparing/transferring, and a check means the verified device copy is complete. A season action queues only its missing episodes through the same identity and device-capability checks as a movie download.
- **Delete After Watching** is an optional Offline downloads setting, off by default. Its `(i)` explanation states that OpenStream removes only the completed device download after playback is marked watched; unfinished playback and online streams are retained.
- The built-in/SD-card storage choices sit directly above **Delete After Watching** as two compact bounded controls. They no longer expand across an entire Settings row.
- The **Captions** panel uses the concise **Auto-On** setting name for automatically enabling available subtitles.
- **Delete all downloads** is available in the same Settings panel whenever device downloads exist. The first tap shows the exact download count and requires an explicit Delete all or Cancel choice; confirmation removes completed, partial, queued, and failed downloads through Media3 while retaining libraries, metadata credentials, settings, and playback history.
- **Settings → Storage** measures temporary cache and catalog/title metadata separately with B/KB/MB/GB precision. **Clear cache** removes Coil artwork plus other app-owned temporary files. **Clear metadata** confirms first, then removes only the local library index and add-on catalog/title-detail cache. Neither action can touch device downloads, playback history, connection settings, or credentials; active libraries and catalogs may rebuild their caches immediately after clearing.

## Radarr and Sonarr

- **Settings → Server downloads** accepts independent Radarr and Sonarr base addresses and API keys. Credentials are password-masked, stored only in backup-disabled app-private preferences, omitted from logs and object descriptions, and never compiled into source or an APK.
- The compact **Test** action calls the current v3 status route, `GET /api/v3/system/status`, using the `X-Api-Key` header. HTTPS is accepted normally. Cleartext HTTP is restricted to literal private/loopback IP addresses and handled by a bounded client.
- **Settings → Server downloads → Download button default** stores one app-private preference: **Ask** (the compatibility default), **This device**, or **Server**. Ask opens the two-destination chooser; the other choices immediately take their respective action. If the matching ARR is not configured, the action safely falls back to the device flow.
- **Download to this Device** keeps the optimized device-storage flow. **Send to Radarr/Sonarr** selects the strongest available quality profile by its target/cutoff resolution and source (preferring 2160p/remux-style targets over broad `Any` or HD profiles), applies it to an existing ARR title when needed, then starts the exactly scoped `MoviesSearch`, `SeasonSearch`, `EpisodeSearch`, or `SeriesSearch` command. A retryable offline/network/server failure is saved in an app-private queue without an API key; OpenStream retries it with the currently configured connection after network recovery. Invalid/authentication requests are not silently retried.
- A missing ARR title is first added with the server's first accessible root folder and selected strongest quality profile, then searched. The server submission shows progress only while the request is being made and never creates a false on-device check mark.
- ARR indicates server management/availability only; it is not presented as an on-device download and does not replace a local library or a configured direct stream source.

## Device presentation, Live TV, LAN setup, and Trakt

- Android phones are portrait-first with normal user rotation. Android tablets request user-landscape while keeping the UI adaptive; Android 17/API 37 may override orientation requests on 600 dp+ displays by platform design. Google TV remains landscape. iPhone supports portrait plus landscape rotation, while iPad declares landscape orientations. Playback and adaptive/offline track selection use the active display mode, hardware decoders, HDR display paths, and current audio output. Settings shows the exact detected viewport and capability report.
- Launch uses the five-second native OpenStream signal/confluence/lockup sequence: local, NAS, and add-on streams form the three-bar mark, the OpenStream wordmark resolves, and the stage hands off to the real Discover screen. The exact mark is also used by the adaptive/round/monochrome launcher icon, Android TV banner, and tappable app-bar brand. The launcher foreground alone receives a dedicated 20 dp inset on the 108 dp adaptive canvas, keeping the heavy bars compact inside round, squircle, and themed masks without shrinking the app-bar or launch-animation artwork. Disabled system animations receive a 220 ms static-mark handoff. The canonical cross-platform vectors are `brand/openstream-mark.svg` and `brand/openstream-app-icon.svg`; obsolete identity assets are removed.
- **Live** loads only the M3U/M3U8 playlist or Xtream-style account configured under **Sources → Live sources**, plus individual channels explicitly saved in Channel Manager. It organizes that source into **Local**, **Entertainment**, **Premier**, **Sports Pack**, **League Sports**, and **Pay Per View**, caps normal guide rows while retaining the provider catalog for search, and stores package/favorite/manual-channel preferences privately. No public playlist, regional lineup, account, or channel is compiled into the app. Usernames and passwords are masked, stored only in backup-disabled app-private preferences, redacted from object descriptions, and never written here, to source, logs, screenshots, or APK constants.
- **Live** uses a fixed channel rail, a two-hour half-hour timeline, current-time marker, program progress, favorites, vertical scrolling, time paging/swipes, and touch/keyboard/remote focus. The old selected-channel/Watch summary below Search is removed, as are Choice and Ultimate chips from the main guide. Search uses a prebuilt normalized channel/program index, ranks direct channel-name matches before schedule text, and waits 110 ms after typing before filtering; the 5,000+ channel manager uses the same indexed approach. The current lineup and guide live in a private in-memory 15-minute session, so returning from playback paints immediately without refetching playlists or rebuilding search data.
- Live preparation is stale-while-refresh: route merging, package classification, program grouping, and both normalized search indexes run off the Compose UI thread, while the last usable prepared guide remains visible and available to player channel controls. Inside playback, a deliberate swipe anywhere across the picture selects a channel: right is next and left is previous. The gesture uses a natural tablet-width threshold and can recover the adjacent channel by normalized name if a guide refresh replaced the original route key. The old **CH −**/**CH +** buttons and side-edge gesture exclusions are removed; transient chrome places the compact **Live** action beside the timeline controls. **Live** uses Media3's measured live offset or the current window end to seek forward only, keeps a two-second playback cushion, resumes without a seek when already live, and reconnects routes that do not expose a live timeline. A user pause never shows the preparation overlay or auto-resumes through a retry. Initial startup and rebuffering both use the compact **Loading** surface; rebuffering still gets two bounded reconnect attempts for transient network/408/429/5xx failures, while an initial dead channel fails over immediately. Terminal HTTP 401/403 failures explain that the provider denied the channel and may require an active package or refreshed link, without exposing the route URL.
- Provider-advertised XMLTV is the only automatic schedule source. XMLTV is parsed as a bounded stream. The parser removes only the conventional inert `<!DOCTYPE tv SYSTEM "xmltv.dtd">` prefix while continuing to reject internal entity subsets and disabling external general entities, parameter entities, and external DTD loading. Empty or failed guides retain a one-hour retry backoff; channels without a match remain playable and show **Schedule unavailable**.
- On Android XR/Quest-compatible hardware, the catalog remains the launch surface. Runtime probes identify Android XR/OpenXR/Quest devices; titles marked 180°, 360°, fisheye, cubemap, SBS, top-bottom, or multiview route to the immersive backend, while flat titles remain in the normal player.
- **Sources → Web Management** starts an explicit temporary HTTP page on the device's private `192.168.x.x`/`10.x.x.x` address. While that page is active, the device advertises the browser-friendly `openstream.local:<port>` hostname over mDNS and serves the portal at its root so there is no path or access code to type. The Sources panel presents that short name as selectable plain text with a Copy action and keeps `<device-ip>:<port>` directly underneath as the reliable fallback for guest Wi-Fi, client-isolated, VPN, or multicast-filtered networks; the optional QR continues to carry the complete numeric URL. The responder acquires Android's Wi-Fi multicast lock only for the setup session and releases it on success, Stop, screen close, failure, or the ten-minute expiry, following [Android's multicast-lock lifecycle](https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock). The responsive portal follows OpenStream's monochrome brand system (black canvas, near-black surface, white wordmark/action, gray supporting text), uses the real three-stream mark, and includes a compact HTTPS `manifest.json` form, in-progress feedback, a styled success confirmation, and an editable error state. Browser-side and server-side parsing explain missing schemes, HTTP/Stremio/other unsupported schemes, missing hosts, embedded credentials, query/fragment components, and non-manifest JSON paths before an add-on is stored. The page accepts one HTTPS Stremio manifest at `/` or `/openstream`, rejects non-local clients and every other path, expires after ten minutes, and stops after a successful add, an explicit Stop, or when the Sources screen closes. Closing its listening or multicast socket is treated as normal shutdown rather than an uncaught background exception. Use only on a trusted LAN; metadata and account credentials are never accepted by this page.
- **Sources → YouTube** accepts an exact `@handle`, canonical `UC…` channel ID, or YouTube channel URL and verifies it to the channel's canonical ID before saving it in app-private preferences. It does not perform ambiguous general search and requires no YouTube API key. Every enabled channel contributes a channel-named 16:9 rail inside one shared **YouTube** Discover group from YouTube's public Atom feed; the newest upload from each channel is also eligible for **Up Next** after resumable titles. Feed identity, video IDs, XML declarations, response sizes, and artwork hosts are validated before display. Selecting a video opens a full-screen restricted WebView using the official [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference); OpenStream does not extract, proxy, download, or expose a direct YouTube media URL. Channels can be individually disabled or removed from Sources.
- **Sources → Discover feed** provides persistent visibility and Up/Down ordering for **Up Next**, **Library**, **Add-Ons**, and **YouTube** without deleting or disabling the underlying sources. Up Next is a bounded, duplicate-free local ranking: resumable titles first, then one newest upload per enabled YouTube channel, then the existing metadata/history-ranked likely unwatched titles. The former **Discovery & playback add-ons** heading is now **Add-Ons**. Every installed Stremio add-on has an icon-only diagnostic that revalidates its manifest identity and, when the manifest exposes a non-input catalog, probes one real catalog route; it shows a neutral refresh icon before testing, a compact progress indicator while working, then only a green check or red X. Detailed success/failure copy remains available as the control's accessibility description without occupying the row or revealing private manifest paths. **Add NAS share**, **Add YouTube channel**, and **Add add-on** live beside their matching section headings instead of in the page header. Libraries, NAS shares, YouTube channels, Stremio add-ons, and the custom Live source use an explicit section-level delete mode; destructive red X controls remain hidden until **Delete…** is selected and disappear again with **Done**. IPTV/Xtream/M3U configuration and Channel Manager live under **Sources → Live sources** rather than Settings or the Live guide, and all Live empty/error actions route to Sources.
- Optional Trakt support uses the TV-friendly OAuth device-code flow, refreshes seven-day access tokens with the single-use refresh token, imports a remote resume point when no local point exists, and scrobbles start/pause/stop for IMDb-identified movies and numbered episodes. Until a publisher-owned OpenStream Trakt application is registered, users can opt in with their own Trakt application client ID/secret under Settings; credentials and tokens stay in backup-disabled app-private storage.
- Discover uses large portrait poster rails with lazy vertical composition, title-by-title horizontal snap scrolling, and a restrained focus scale/border for remote navigation. Tapping the **OpenStream** wordmark returns directly to Discover. A short, thin, translucent progress line is centered near the bottom of the featured stage with clear separation from the system gesture indicator; it fills during the 12-second dwell and resets when the next title arrives, with no countdown text. Featured and Details use a true full-width 16:9 media stage with no legacy 500 dp height cap. Cinematic backdrops use a centered full-bleed crop so a narrower source cannot leave a hard internal right/bottom edge on wide tablets; title/actions remain inside the safe overlay area, and the softly blurred loading/fallback layer is clipped to the stage boundary. Details keeps one prominent Play action plus mutually exclusive thumbs-up and thumbs-down preferences. The supplied third-party APK was not decompiled or copied; only high-level viewing patterns were reimplemented independently from the user's written guide. No proprietary Netflix code, layouts, assets, or resources are bundled.
- Provider names on title cards are normalized to **Stream**; device-backed routes use **Local** or **Offline**. Like/dislike are accessible icon-only thumb controls, and the top navigation no longer causes artwork to be cropped. When TMDB supplies a valid official YouTube trailer ID, Details may transition from the still artwork to the official YouTube IFrame player, muted, only while online on an unmetered connection and while system animations are enabled. OpenStream does not extract or proxy YouTube media URLs; unavailable previews immediately fall back to the complete still artwork, and no app overlay obscures the standard YouTube player controls.

## Native platform matrix

| Platform | Native technology | Current runnable scope |
| --- | --- | --- |
| Android mobile/tablet | Kotlin, Compose, Media3 | Full catalog, metadata, add-ons, playback, resume, offline, captions, HDR/audio labels |
| Google TV | Kotlin, Compose for TV, Media3 | Independent Leanback app sharing the Android core |
| iPhone/iPad | SwiftUI | Source-driven native shell and shared source/content policy foundation |
| Apple TV | SwiftUI | Focusable source-driven native shell; playback integration remains future work |
| Apple Vision Pro | SwiftUI | Native source-driven shell; immersive playback integration remains future work |
| LG webOS | Packaged HTML5 app | Configured-catalog shell, adaptive performance profile, remote focus, playback, resume, format labels |
| Samsung Tizen | Packaged TV web app | Configured-catalog shell, registered media keys, remote focus, playback, resume |
| Hisense VIDAA | Hosted HTML5 app package | Configured-catalog shell, remote focus, playback, resume |
| Roku | SceneGraph/BrightScript | Source-driven empty-state channel foundation; catalog pairing and playback remain future work |
| Android XR | Kotlin, SceneCore, Media3 | Independent full-space APK, incoming-URI autoplay, flat/equirectangular surfaces, mono/SBS/top-bottom/MV-HEVC, HDR surface metadata, pause/resume lifecycle |
| Meta Quest | Android NativeActivity, Media3, OpenXR C++, OpenGL ES | Independent arm64 APK, VIEW-intent autoplay, decoded external texture to OpenXR swapchain, flat/wide/equirectangular compositor layers, GPU fisheye and 3x2 EAC/cubemap conversion, mono/SBS/top-bottom crops, lifecycle pause/resume, headset capability probes |

The immersive projection contract recognizes flat, wide-FOV, fisheye 180°, equirectangular 180°/360°, equi-angular cubemap, mono, side-by-side, top-bottom, and MV-HEVC/multiview media. The native session core rejects unsupported resolution, codec, projection, stereo, dynamic-range, and spatial-audio combinations before allocating runtime resources, then provides deterministic ready/play/pause/seek/end/stop behavior and per-eye texture bindings.

Android XR is pinned to `androidx.xr.scenecore:scenecore:1.0.0-beta01`. Its direct `SurfaceEntity` path covers flat, wide-FOV, equirectangular 180°/360°, packed stereo, and MV-HEVC. It blocks fisheye and EAC/cubemap sources with an explicit native-mesh requirement instead of showing incorrect UV mapping. The Quest package is pinned to Media3 1.10.1, the official Khronos `openxr_loader_for_android:1.1.62`, NDK `29.0.14206865`, and SDK CMake `4.1.2`. Quest 0.3.0 decodes incoming content/file/HTTP(S) VIEW intents to a `SurfaceTexture`, copies the external texture into an OpenXR swapchain, and submits flat, wide-FOV, or equirectangular KHR layers with per-eye mono/SBS/top-bottom crops. Its GPU copy shader converts fisheye/VR180 and 3x2 EAC/cubemap sources to projection-correct equirectangular swapchains. Multiview and runtimes without the equirectangular extension remain explicitly blocked until the required texture-array or projection-mesh renderer is implemented.

## Verification commands

```text
pnpm test
pnpm build
pnpm test:android-device
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


## Current verification

The former chronological device log was removed because it described compiled demo catalogs, public IPTV defaults, and visual assets that no longer exist.

On August 14, 2026, the source/brand cleanup passed 314 Android and 314 shared Google TV unit tests with zero failures, both lint pipelines, and both debug APK assemblies. The web passed 29 Vitest tests, 52 server/script tests, TypeScript checking, and a production Vite build; the native Apple package passed all 10 tests; and the webOS/Tizen/VIDAA/Roku package builder completed. The final Android APK was installed as an upgrade on the attached Lenovo TB132FU and passed a cold-launch Discover → Library → Discover smoke test without a crash or ANR. The upgrade retained all three settings-added Stremio add-ons, found no compiled-default preference, discarded the stale compiled discovery cache, and removed ten obsolete public Live cache files. Discover rendered the source-driven empty state with no book row or bundled title. Android APK SHA-256: `3d1ebc187a69f7c56a2f763165b093576ca8d0abc2883d888f417771d60d83ce`; Google TV APK SHA-256: `61b927f39403496968e8f466d0cebac15d533f444eb7dc69e113688ee442a0fc`.
