# OpenStream Make

OpenStream Make is the conversational layer over OpenPencil. OpenPencil provides the editable canvas
and design tools; Codex interprets each natural-language request and operates those tools through
the local authenticated MCP server.

## Use it

Create a new design conversation:

```bash
pnpm make:new -- "Design a tvOS home screen for a local and streaming media library"
```

Iterate on that design and preserve context:

```bash
pnpm make -- "Make the focused title more obvious and add an offline NAS state"
```

Use a visual reference:

```bash
pnpm make -- --image /absolute/reference.png "Use this typography and density"
```

Each new conversation receives its own OpenPencil page. The current Codex thread and page are
stored in `session.json`; `latest.fig`, `latest.png`, and `last-run.json` are refreshed after every
successful prompt.

OpenPencil also exposes the same interaction in its own UI: press `Cmd+J`, choose **Codex**, and
type directly beside the canvas. The `@agentclientprotocol/codex-acp` adapter is already installed.
