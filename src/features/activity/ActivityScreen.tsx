import { CheckCircle2, Clock3, Download, Server, TriangleAlert } from "lucide-react";

const activity = [
  { title: "The Last Signal", service: "Radarr", status: "Searching", progress: 34, icon: Server, tone: "active" },
  { title: "Echoes of Mars · Season 1", service: "Sonarr", status: "Monitoring", progress: 100, icon: CheckCircle2, tone: "complete" },
  { title: "Beyond the Rift", service: "SABnzbd", status: "Waiting for connection", progress: 0, icon: TriangleAlert, tone: "warning" },
  { title: "Northstar", service: "Atlas NAS", status: "Indexed 8m ago", progress: 100, icon: Download, tone: "complete" },
];

export function ActivityScreen() {
  return (
    <main className="screen workspace-screen activity-screen"><section className="workspace-content full-width"><header className="section-header"><div><h1>Activity</h1><p>Remote requests, imports, and source health</p></div><span className="live-state"><Clock3 size={17} />Updated now</span></header><div className="activity-list">{activity.map(({ title, service, status, progress, icon: Icon, tone }, index) => <button key={title} className="activity-row" data-focusable data-autofocus={index === 0 ? true : undefined} autoFocus={index === 0}><span className={`activity-icon ${tone}`}><Icon size={21} /></span><span className="activity-copy"><strong>{title}</strong><small>{service} · {status}</small></span><span className="activity-progress"><span><i style={{ width: `${progress}%` }} /></span><small>{progress === 100 ? "Complete" : progress === 0 ? "Paused" : `${progress}%`}</small></span></button>)}</div></section></main>
  );
}
