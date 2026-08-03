# Functional audit - 2026-07-16

## Implemented workflows

| Area | Result | Verification |
| --- | --- | --- |
| Discover, Library, Details, Live, Search | Implemented | Browser interaction and responsive capture |
| Connections | Implemented | Dialog validation, explicit LAN consent, gateway tests |
| Google Drive | Implemented gateway path | PKCE OAuth, `drive.file`, listing and byte-range media proxy; live Google credentials required |
| Stremio | Implemented gateway path | Manifest validation, sanitized/bounded streams, opaque range proxy |
| Sonarr/Radarr | Implemented | Root-folder/profile lookup and server-side request handoff |
| SABnzbd/NZBGet | Implemented | URL handoff to manager; no direct device download |
| Player route selection | Implemented | Capability-ranked routes, blocked/probe/ready states |
| WebXR 180/360 | Implemented | Mono/SBS/top-bottom, local file, source validation, canvas checks |
| tvOS/visionOS core | Implemented package | Host tests and explicit SDK cross-compilation |
| Android XR core | Implemented module | Unit tests, lint, debug APK |

## Connector behavior verified with local mocks

- private-network access is denied until the user explicitly enables it;
- Stremio manifests and stream payloads are bounded and sanitized;
- unsafe URL schemes are dropped;
- upstream media URLs are replaced by expiring opaque gateway routes;
- HTTP range requests receive the expected partial response;
- Sonarr and Radarr receive the correct API-key-authenticated payload;
- SABnzbd and NZBGet receive server-side authenticated handoffs;
- connection credentials never appear in public connection JSON.

## Findings fixed

1. Stremio had implicitly authorized private-network access. It now requires visible, off-by-default consent.
2. Connection-type switching retained sensitive form fields. URL, credentials, username, and LAN consent now reset.
3. Stremio displayed an irrelevant API-key field. The dialog now requires only a manifest URL and applicable consent.
4. Stremio returned raw upstream URLs. Media routes are now opaque, bounded, expiring, and range-proxied.
5. NZBGet JSON-RPC errors were not checked. Failed handoffs now surface as errors.
6. The gateway could be configured to bind all interfaces. Only loopback bind hosts are accepted.
7. Browser mutations lacked an application request marker. Origin-initiated POST requests now require `X-OpenStream-Request: 1`.
8. Plain HTTP could be accepted for a hostname with mixed public/private DNS answers. Every resolved address must now be private.
9. Malformed byte ranges could reach media proxy routes. Shared range validation rejects unsupported and suffix-only forms.

## External validation still required

Real credentials and production services were intentionally not used in automated tests. Before release, run controlled integration tests with a dedicated Google OAuth client, non-production Arr/download-manager instances, representative Stremio add-ons, and physical headsets/displays.

