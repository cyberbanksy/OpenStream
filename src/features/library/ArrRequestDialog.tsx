import { Download, Server, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useApp } from "../../app/AppContext";
import { useDialogFocusTrap } from "../../app/useDialogFocusTrap";
import type { MediaItem } from "../../domain/media";
import { getArrOptions, sendToArrGateway, type ArrOptions } from "../../integrations/gateway";
import { isCurrentArrSelection } from "./arrSelection";

export function ArrRequestDialog({ item, onClose }: { item: MediaItem; onClose: () => void }) {
  const { connections, notify } = useApp();
  const dialogRef = useRef<HTMLElement>(null);
  const service = item.kind === "series" || item.kind === "episode" ? "Sonarr" : "Radarr";
  const serviceKind = service.toLowerCase();
  const servers = useMemo(
    () => connections.filter((connection) => connection.kind === serviceKind && connection.managed && connection.state === "connected"),
    [connections, serviceKind],
  );
  const [connectionId, setConnectionId] = useState(servers[0]?.id ?? "");
  const [options, setOptions] = useState<ArrOptions>({ profiles: [], roots: [] });
  const [loadedConnectionId, setLoadedConnectionId] = useState("");
  const [qualityProfileId, setQualityProfileId] = useState(0);
  const [rootFolderPath, setRootFolderPath] = useState("");
  const [monitor, setMonitor] = useState(true);
  const [state, setState] = useState<"idle" | "loading" | "submitting">("idle");
  useDialogFocusTrap(dialogRef, onClose);

  useEffect(() => {
    if (!connectionId && servers[0]) setConnectionId(servers[0].id);
  }, [connectionId, servers]);

  useEffect(() => {
    setOptions({ profiles: [], roots: [] });
    setLoadedConnectionId("");
    setQualityProfileId(0);
    setRootFolderPath("");
    if (!connectionId) {
      setState("idle");
      return;
    }
    let cancelled = false;
    setState("loading");
    void getArrOptions(connectionId).then((result) => {
      if (cancelled) return;
      setOptions(result);
      setLoadedConnectionId(connectionId);
      setQualityProfileId(result.profiles[0]?.id ?? 0);
      setRootFolderPath(result.roots[0]?.path ?? "");
      setState("idle");
    }).catch((error) => {
      if (cancelled) return;
      setState("idle");
      notify({ tone: "warning", title: `${service} options unavailable`, detail: error instanceof Error ? error.message : "Could not load ARR options." });
    });
    return () => { cancelled = true; };
  }, [connectionId, notify, service]);

  const canSubmit = isCurrentArrSelection(
    connectionId,
    loadedConnectionId,
    options,
    qualityProfileId,
    rootFolderPath,
  );

  const submit = async () => {
    if (!canSubmit) return;
    setState("submitting");
    try {
      await sendToArrGateway(connectionId, item, qualityProfileId, rootFolderPath, monitor);
      notify({
        tone: "success",
        title: `Sent to ${service}`,
        detail: `${item.title} is monitored and the search was queued on your server.`,
      });
      onClose();
    } catch (error) {
      notify({ tone: "warning", title: `Could not send to ${service}`, detail: error instanceof Error ? error.message : "ARR request failed." });
      setState("idle");
    }
  };

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section ref={dialogRef} className="dialog arr-dialog" role="dialog" aria-modal="true" aria-labelledby="arr-title">
        <header><span className="dialog-icon"><Server size={23} /></span><div><h2 id="arr-title">Send to {service}</h2><p>The download runs on your ARR server, not this device.</p></div><button data-focusable aria-label="Close" onClick={onClose}><X size={20} /></button></header>
        <div className="dialog-body">
          <dl className="request-summary"><dt>Title</dt><dd>{item.title}</dd><dt>Action</dt><dd>Monitor and search on {service}</dd></dl>
          {servers.length === 0 ? <p className="dialog-inline-note">Connect {service} through the secure gateway before sending this request.</p> : null}
          <label>Server<select data-focusable value={connectionId} onChange={(event) => setConnectionId(event.target.value)} disabled={servers.length === 0}>{servers.length === 0 ? <option value="">No configured server</option> : servers.map((server) => <option key={server.id} value={server.id}>{server.name} - {server.detail}</option>)}</select></label>
          <label>Quality profile<select data-focusable value={qualityProfileId} onChange={(event) => setQualityProfileId(Number(event.target.value))} disabled={loadedConnectionId !== connectionId || state === "loading" || options.profiles.length === 0}>{options.profiles.length === 0 ? <option value={0}>{state === "loading" ? "Loading profiles..." : "No profiles available"}</option> : options.profiles.map((profile) => <option key={profile.id} value={profile.id}>{profile.name}</option>)}</select></label>
          <label>Destination<select data-focusable value={rootFolderPath} onChange={(event) => setRootFolderPath(event.target.value)} disabled={loadedConnectionId !== connectionId || state === "loading" || options.roots.length === 0}>{options.roots.length === 0 ? <option value="">{state === "loading" ? "Loading folders..." : "No root folders available"}</option> : options.roots.map((root) => <option key={root.path} value={root.path}>{root.path}</option>)}</select></label>
          <label className="check-row"><input data-focusable type="checkbox" checked={monitor} onChange={(event) => setMonitor(event.target.checked)} /><span>Monitor for future upgrades</span></label>
        </div>
        <footer><button className="text-button" data-focusable onClick={onClose}>Cancel</button><button className="action-button primary" data-focusable data-dialog-autofocus onClick={submit} disabled={state !== "idle" || !canSubmit}><Download size={19} />{state === "submitting" ? "Sending..." : `Send to ${service}`}</button></footer>
      </section>
    </div>
  );
}
