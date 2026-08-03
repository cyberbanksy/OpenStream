# Design audit - 2026-07-16

Audited at 1715x965 desktop and 390x844 mobile/XR browser sizes. Evidence is in `design/audit-2026-07-16/`.

## Coverage

- Discover
- Library
- Details
- Live
- Connections
- connection setup dialog
- Search
- Activity
- Settings
- Player route picker and playing state
- Arr request dialog
- WebXR 180 viewer

## Automated checks

- element bounds and viewport clipping;
- interactive-control overlap detection;
- WCAG 2 A/AA and WCAG 2.1 A/AA Axe checks;
- WebXR canvas nonblank pixel validation;
- desktop and mobile screenshots using the existing Chrome for Testing installation.

Final automated result: zero Axe violations on the audited routes and no incoherent interactive overlaps.

## Findings fixed

1. The eighth Search result clipped at the desktop viewport edge. The search layout now uses seven stable columns at the audited width.
2. The Live guide used a list role with invalid children. The incorrect role was removed while preserving the visual table layout.
3. Connection status indicators exposed an invalid `aria-label` on a generic element. They now use `role="img"` with a state-specific accessible name.
4. Projection labels exposed implementation-style casing. Settings now presents human-readable title case and preserves FOV as an abbreviation.
5. Stremio private-network permission was invisible and preselected. It is now visible, off by default, and contained within the dialog at desktop and mobile sizes.

## Visual system assessment

- Artwork is the primary content signal; operational data remains compact.
- Focus/hover treatments preserve card dimensions and surrounding rail geometry.
- Cyan is used as an action/focus accent, while the interface retains neutral, green, amber, and red semantic states rather than becoming a one-hue palette.
- Cards use restrained radii and are reserved for repeated media or operational items.
- Source provenance, format badges, and route state are readable without competing with title and artwork.
- The OpenStream mark, generated key art, typography, spacing, and component treatments are original and distinguish the product from Apple TV and Infuse.

## Residual device checks

Remote focus movement, overscan, HDR output, text scaling, and stereo composition must be visually checked on physical Apple TV, Vision Pro, Samsung XR, and Meta Quest hardware. Browser and simulator screenshots cannot close those device-specific checks.

