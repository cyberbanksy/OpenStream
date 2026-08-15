import { Cloud, Database, Download, HardDrive, Puzzle, Server, X } from "lucide-react";
import { type FormEvent, useMemo, useRef, useState } from "react";
import { useApp } from "../../app/AppContext";
import { useDialogFocusTrap } from "../../app/useDialogFocusTrap";
import type { ConnectionKind, ConnectionSummary } from "../../integrations/connection";
import { connectThroughGateway } from "../../integrations/gateway";

const kinds: Array<{ id: ConnectionKind; label: string; icon: typeof Server }> = [
  { id: "nas-folder", label: "NAS folder", icon: HardDrive },
  { id: "google-drive", label: "Google Drive", icon: Cloud },
  { id: "stremio", label: "Stremio", icon: Puzzle },
  { id: "sonarr", label: "Sonarr", icon: Server },
  { id: "radarr", label: "Radarr", icon: Server },
  { id: "sabnzbd", label: "SABnzbd", icon: Download },
  { id: "nzbget", label: "NZBGet", icon: Download },
];

export function ConnectionDialog() {
  const { closeConnectionDialog, notify, upsertConnection } = useApp();
  const dialogRef = useRef<HTMLElement>(null);
  const [kind, setKind] = useState<ConnectionKind>("nas-folder");
  const [baseUrl, setBaseUrl] = useState("");
  const [credential, setCredential] = useState("");
  const [username, setUsername] = useState("");
  const [allowPrivateNetwork, setAllowPrivateNetwork] = useState(false);
  const [state, setState] = useState<"idle" | "checking">("idle");
  const selected = useMemo(() => kinds.find((item) => item.id === kind) ?? kinds[0], [kind]);
  useDialogFocusTrap(dialogRef, closeConnectionDialog);

  const chooseKind = (nextKind: ConnectionKind) => {
    setKind(nextKind);
    setBaseUrl("");
    setCredential("");
    setUsername("");
    setAllowPrivateNetwork(false);
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setState("checking");
    // Open the authorization window synchronously, inside the submit gesture,
    // so the browser does not popup-block it after the gateway call resolves.
    const authWindow = kind === "google-drive" ? window.open("", "_blank") : null;
    if (authWindow) authWindow.opener = null;
    try {
      const result = await connectThroughGateway({ kind, baseUrl, credential, username, allowPrivateNetwork });
      const connection: ConnectionSummary = result.connection;
      if (result.authorizationUrl) {
        if (authWindow) authWindow.location.href = result.authorizationUrl;
        else window.open(result.authorizationUrl, "_blank", "noopener,noreferrer");
      } else {
        authWindow?.close();
      }
      upsertConnection(connection);
      notify({
        tone: connection.state === "connected" ? "success" : "neutral",
        title: connection.state === "connected" ? `${selected.label} connected` : `${selected.label} authorization opened`,
        detail: connection.detail,
      });
      setCredential("");
      closeConnectionDialog();
    } catch (error) {
      authWindow?.close();
      notify({ tone: "warning", title: `Could not connect ${selected.label}`, detail: error instanceof Error ? error.message : "Connection failed." });
    } finally {
      setState("idle");
    }
  };

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => event.currentTarget === event.target && closeConnectionDialog()}>
      <section ref={dialogRef} className="dialog connection-dialog" role="dialog" aria-modal="true" aria-labelledby="connection-title">
        <header><span className="dialog-icon"><Database size={23} /></span><div><h2 id="connection-title">Add connection</h2><p>Credentials stay in memory unless a native secure store is available.</p></div><button data-focusable aria-label="Close" onClick={closeConnectionDialog}><X size={20} /></button></header>
        <form onSubmit={save}>
          <div className="connection-kind-grid" role="radiogroup" aria-label="Connection type">
            {kinds.map(({ id, label, icon: Icon }) => (
              <button data-focusable data-dialog-autofocus={id === "nas-folder" ? true : undefined} type="button" role="radio" aria-checked={kind === id} className={kind === id ? "kind-button selected" : "kind-button"} key={id} onClick={() => chooseKind(id)}>
                <Icon size={20} /><span>{label}</span>
              </button>
            ))}
          </div>
          <div className="form-stack">
            {kind === "nas-folder" ? (
              <label>Mounted folder path<input data-focusable type="text" value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} placeholder="/Volumes/Media" required /></label>
            ) : kind === "google-drive" ? null : (
              <label>Server or manifest URL<input data-focusable type="url" value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} placeholder={kind === "stremio" ? "https://addon.example/manifest.json" : "https://server.example"} required /></label>
            )}
            {kind === "nzbget" ? <label>Username<input data-focusable value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required /></label> : null}
            {kind === "stremio" || kind === "nas-folder" ? null : <label>{kind === "google-drive" ? "OAuth client ID" : kind === "nzbget" ? "Password" : "API key"}<input data-focusable type={kind === "google-drive" ? "text" : "password"} value={credential} onChange={(event) => setCredential(event.target.value)} autoComplete="off" required /></label>}
            {kind !== "google-drive" && kind !== "nas-folder" ? <label className="check-row"><input data-focusable type="checkbox" checked={allowPrivateNetwork} onChange={(event) => setAllowPrivateNetwork(event.target.checked)} /><span>Allow this connection to reach my local network</span></label> : null}
            {kind === "nas-folder" ? <p className="dialog-hint">Point OpenStream at a folder this computer can already read — a mounted SMB/NFS share or a local library. Files stream through the loopback gateway with range support and on-demand local transcoding.</p> : null}
          </div>
          <footer><button data-focusable type="button" className="text-button" onClick={closeConnectionDialog}>Cancel</button><button data-focusable className="action-button primary" type="submit" disabled={state === "checking"}>{state === "checking" ? "Checking…" : kind === "google-drive" ? "Authorize" : "Test & connect"}</button></footer>
        </form>
      </section>
    </div>
  );
}
