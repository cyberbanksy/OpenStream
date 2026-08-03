import { CheckCircle2, CircleHelp, MonitorUp, ShieldCheck, XCircle } from "lucide-react";
import { useApp } from "../../app/AppContext";
import type { SupportLevel } from "../../domain/capabilities";

function SupportIcon({ level }: { level: SupportLevel }) {
  if (level === "supported") return <CheckCircle2 size={17} />;
  if (level === "unsupported") return <XCircle size={17} />;
  return <CircleHelp size={17} />;
}

function formatProjection(name: string) {
  const abbreviations: Record<string, string> = { fov: "FOV" };
  return name
    .split("-")
    .map((part) => abbreviations[part] ?? `${part.charAt(0).toUpperCase()}${part.slice(1)}`)
    .join(" ");
}

export function SettingsScreen() {
  const { capabilities } = useApp();
  const dynamicRanges = Object.entries(capabilities.dynamicRanges) as Array<[string, SupportLevel]>;
  const projections = Object.entries(capabilities.projections) as Array<[string, SupportLevel]>;
  return (
    <main className="screen workspace-screen settings-screen">
      <aside className="workspace-sidebar"><div className="sidebar-heading"><MonitorUp size={18} /><span>Playback</span><small>Current device</small></div><button className="sidebar-button selected" data-focusable data-autofocus autoFocus><MonitorUp size={20} />Capabilities</button><button className="sidebar-button" data-focusable><ShieldCheck size={20} />Privacy & security</button><div className="settings-note"><ShieldCheck size={18} /><span>Format support is probed at runtime. Labels never override a failed decoder check.</span></div></aside>
      <section className="workspace-content"><header className="section-header"><div><h1>Playback capabilities</h1><p>{capabilities.platform} · {capabilities.probedAt ? "runtime probe complete" : "using conservative platform hints"}</p></div><span className="platform-chip">{capabilities.secureNativePlayback ? "Native adapter" : "Web adapter"}</span></header><div className="capability-panels"><section><h2>Dynamic range</h2><p>Profile support is verified again before playback.</p><div className="support-grid">{dynamicRanges.map(([name, level]) => <span className={`support-row ${level}`} key={name}><SupportIcon level={level} /><strong>{name.replace("dolby-vision", "Dolby Vision").replace("hdr10-plus", "HDR10+").toUpperCase()}</strong><small>{level.replace("runtime-check", "Check on device")}</small></span>)}</div></section><section><h2>Immersive media</h2><p>visionOS 26 uses public APMP and Apple Immersive Video playback APIs.</p><div className="support-grid">{projections.map(([name, level]) => <span className={`support-row ${level}`} key={name}><SupportIcon level={level} /><strong>{formatProjection(name)}</strong><small>{level.replace("runtime-check", "Check on device")}</small></span>)}</div></section><section className="wide-panel"><h2>Decoded limits</h2><div className="metrics-strip"><span><small>Maximum frame</small><strong>{capabilities.maxWidth} × {capabilities.maxHeight}</strong></span><span><small>Frame rate</small><strong>Up to {capabilities.maxFrameRate} fps</strong></span><span><small>Native protected path</small><strong>{capabilities.secureNativePlayback ? "Available" : "Unavailable in browser"}</strong></span></div></section></div></section>
    </main>
  );
}
