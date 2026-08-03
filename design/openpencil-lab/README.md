# OpenPencil Make-style test

## Recommendation

OpenPencil is the best fit for this project because it combines a Figma-like canvas, built-in AI,
106 MCP tools, and native `.fig` read/write. Onlook is closer to Figma Make when the desired output
is a running Next.js app, while Dyad and bolt.diy are broader code-first app builders. They do not
offer OpenPencil's direct editable-design round trip.

Official projects:

- OpenPencil: https://github.com/open-pencil/open-pencil
- Onlook: https://github.com/onlook-dev/onlook
- Dyad: https://github.com/dyad-sh/dyad
- bolt.diy: https://github.com/stackblitz-labs/bolt.diy

## Installed setup

- OpenPencil desktop and CLI: `0.13.2`
- OpenPencil MCP: `0.13.2`
- Codex ACP adapter: `@agentclientprotocol/codex-acp@1.1.2`
- Provider-free test path: Codex generates JSX and drives OpenPencil's local MCP tools.
- Built-in AI path: choose Codex in OpenPencil, or use OpenRouter with a design-capable model.

OpenPencil currently labels Claude Sonnet as its preferred design model. Its provider list also
supports GPT Codex, Gemini, Kimi, Qwen, and GPT-OSS models through OpenRouter or direct APIs.

## Result

The brief in `prompt.md` generated a 1920x1080 tvOS source console with one editable root frame,
108 nodes, auto-layout, text, focus treatment, and source-status components.

- `openstream-source-console.fig`: native editable OpenPencil/Figma document
- `openstream-source-console.png`: verified 1920x1080 render
- `openstream-source-console.jsx`: 10.9 KB round-trip design source
- `smoke-test-report.json`: MCP and CLI verification data

Verification completed:

- Native `.fig` parsed as 1 page and 108 nodes.
- Offline plugin-API edit was written and read back successfully.
- Accessibility lint preset returned zero findings.
- PNG export is exactly 1920x1080.

Run it with OpenPencil open:

```bash
pnpm openpencil:smoke
```

## Version 0.13.2 notes

- `new_document` creates a tab but times out over MCP, so the harness uses the initial blank canvas.
- MCP `export_image` reports no visible nodes for the rendered frame; the verified PNG comes from the
  native CLI renderer instead.
- The packaged CLI uses Bun-only APIs during export despite a Node launcher; the harness invokes it
  through the installed Bun runtime.
- Saving directly to `/Volumes/Files` triggers a macOS network-volume prompt. The harness stages in
  `/tmp` and copies only completed artifacts into the workspace.
