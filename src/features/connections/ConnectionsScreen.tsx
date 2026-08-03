import { CheckCircle2, ChevronRight, Cloud, Database, Download, HardDrive, Plus, Puzzle, Server, ShieldCheck } from "lucide-react";
import { useMemo, useState } from "react";
import { useApp } from "../../app/AppContext";
import { StatusDot } from "../../components/StatusDot";
import type { ConnectionKind, ConnectionSummary } from "../../integrations/connection";
import { NasBrowser } from "./NasBrowser";

const iconByKind: Partial<Record<ConnectionKind, typeof Server>> = {
  "google-drive": Cloud,
  stremio: Puzzle,
  sonarr: Server,
  radarr: Server,
  sabnzbd: Download,
  nzbget: Download,
  "nas-folder": HardDrive,
  smb: HardDrive,
  jellyfin: Database,
  plex: Database,
};

function ConnectionDetail({ connection }: { connection: ConnectionSummary }) {
  const Icon = iconByKind[connection.kind] ?? Server;
  return (
    <section className="connection-detail" aria-labelledby="connection-detail-title">
      <div className="connection-hero-icon"><Icon size={58} /><StatusDot state={connection.state} /></div>
      <div className="connection-main-copy"><span className="side-label">Selected connection</span><h1 id="connection-detail-title">{connection.name}</h1><p>{connection.detail}</p><div className="connection-health"><ShieldCheck size={18} /><span>{connection.state === "connected" ? "Verified connection" : "Configuration required"}</span></div></div>
      <dl className="connection-meta"><dt>Status</dt><dd><StatusDot state={connection.state} />{connection.state}</dd><dt>Last checked</dt><dd>{connection.lastChecked ?? "Never"}</dd><dt>Credential storage</dt><dd>Session / native vault</dd></dl>
      <div className="capability-list"><span className="side-label">Capabilities</span>{connection.capabilities.map((capability) => <span key={capability}><CheckCircle2 size={16} />{capability}</span>)}</div>
    </section>
  );
}

export function ConnectionsScreen() {
  const { connections, openConnectionDialog } = useApp();
  const [selectedId, setSelectedId] = useState(connections[0]?.id);
  const selected = useMemo(() => connections.find((item) => item.id === selectedId) ?? connections[0], [connections, selectedId]);
  const groups = [
    { title: "Libraries", kinds: new Set<ConnectionKind>(["nas-folder", "smb", "jellyfin", "plex", "google-drive"]) },
    { title: "Discovery & requests", kinds: new Set<ConnectionKind>(["stremio", "sonarr", "radarr"]) },
    { title: "Download managers", kinds: new Set<ConnectionKind>(["sabnzbd", "nzbget"]) },
  ];

  return (
    <main className="screen workspace-screen connections-screen">
      <aside className="connections-list" aria-label="Connections">
        <header><div><h1>Connections</h1><p>Sources and server handoffs</p></div><button data-focusable data-autofocus autoFocus aria-label="Add connection" className="icon-action" onClick={openConnectionDialog}><Plus size={22} /></button></header>
        {groups.map((group) => (
          <section key={group.title}><span className="side-label">{group.title}</span>{connections.filter((item) => group.kinds.has(item.kind)).map((connection) => {
            const Icon = iconByKind[connection.kind] ?? Server;
            return <button key={connection.id} data-focusable className={connection.id === selected?.id ? "connection-row selected" : "connection-row"} onClick={() => setSelectedId(connection.id)}><Icon size={21} /><span><strong>{connection.name}</strong><small>{connection.detail}</small></span><StatusDot state={connection.state} /><ChevronRight size={16} /></button>;
          })}</section>
        ))}
      </aside>
      <section className="connections-workspace">
        {selected ? <ConnectionDetail connection={selected} /> : <button onClick={openConnectionDialog}>Add your first connection</button>}
        {selected?.kind === "nas-folder" && selected.managed ? <NasBrowser connection={selected} /> : null}
      </section>
    </main>
  );
}
