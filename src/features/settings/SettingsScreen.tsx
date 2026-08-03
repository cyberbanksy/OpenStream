import { Baby, CheckCircle2, CircleHelp, Cpu, MonitorUp, ShieldCheck, XCircle } from "lucide-react";
import { useApp } from "../../app/AppContext";
import type { SupportLevel } from "../../domain/capabilities";
import { kidsRatingCap } from "../../domain/ratings";

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
  const { capabilities, policy, kidsMode, transcoder } = useApp();
  const dynamicRanges = Object.entries(capabilities.dynamicRanges) as Array<[string, SupportLevel]>;
  const projections = Object.entries(capabilities.projections) as Array<[string, SupportLevel]>;
  const kidsCap = policy?.mode === "kids" ? kidsRatingCap(policy.capId) : undefined;
  return (
    <main className="screen workspace-screen settings-screen">
      <aside className="workspace-sidebar"><div className="sidebar-heading"><MonitorUp size={18} /><span>Playback</span><small>Current device</small></div><button className="sidebar-button selected" data-focusable data-autofocus autoFocus><MonitorUp size={20} />Capabilities</button><button className="sidebar-button" data-focusable><ShieldCheck size={20} />Privacy & security</button><div className="settings-note"><ShieldCheck size={18} /><span>Format support is probed at runtime. Labels never override a failed decoder check.</span></div></aside>
      <section className="workspace-content"><header className="section-header"><div><h1>Playback capabilities</h1><p>{capabilities.platform} · {capabilities.probedAt ? "runtime probe complete" : "using conservative platform hints"}</p></div><span className="platform-chip">{capabilities.secureNativePlayback ? "Native adapter" : "Web adapter"}</span></header><div className="capability-panels">
        <section className="settings-kids-panel"><h2>Family & content</h2><p>{kidsMode ? "Kids Mode is permanently on for this installation." : "This installation shows the full library."}</p><div className="support-grid">
          {kidsMode && kidsCap && policy?.mode === "kids" ? (
            <>
              <span className="support-row supported"><Baby size={17} /><strong>{kidsCap.label}</strong><small>{kidsCap.examples}</small></span>
              <span className="support-row supported"><ShieldCheck size={17} /><strong>Unrated content</strong><small>Always blocked</small></span>
              <span className="support-row supported"><ShieldCheck size={17} /><strong>Blocked terms</strong><small>{policy.blockedTerms.length === 0 ? "None" : `${policy.blockedTerms.length} hidden`}</small></span>
            </>
          ) : (
            <span className="support-row runtime-check"><Baby size={17} /><strong>Kids Mode</strong><small>Off — chosen at setup</small></span>
          )}
        </div>{kidsMode ? <p className="settings-inline-note"><ShieldCheck size={15} />Turning Kids Mode off requires uninstalling OpenStream and setting it up again.</p> : null}</section>
        <section><h2>Local transcoder</h2><p>Unsupported formats convert on this machine, capped at what the device can decode.</p><div className="support-grid">
          {transcoder?.available ? (
            <>
              <span className="support-row supported"><Cpu size={17} /><strong>ffmpeg {transcoder.version ?? ""}</strong><small>{transcoder.hardwareAccelerated ? "Hardware accelerated" : "Software encoding"}</small></span>
              <span className="support-row supported"><CheckCircle2 size={17} /><strong>H.264</strong><small>{transcoder.encoders?.h264 ?? "Unavailable"}</small></span>
              <span className={transcoder.encoders?.hevc ? "support-row supported" : "support-row unsupported"}><SupportIcon level={transcoder.encoders?.hevc ? "supported" : "unsupported"} /><strong>HEVC</strong><small>{transcoder.encoders?.hevc ?? "Unavailable"}</small></span>
            </>
          ) : (
            <span className="support-row unsupported"><XCircle size={17} /><strong>Unavailable</strong><small>{transcoder ? "ffmpeg not found on this machine" : "Requires the loopback gateway"}</small></span>
          )}
        </div></section>
        <section><h2>Dynamic range</h2><p>Profile support is verified again before playback.</p><div className="support-grid">{dynamicRanges.map(([name, level]) => <span className={`support-row ${level}`} key={name}><SupportIcon level={level} /><strong>{name.replace("dolby-vision", "Dolby Vision").replace("hdr10-plus", "HDR10+").toUpperCase()}</strong><small>{level.replace("runtime-check", "Check on device")}</small></span>)}</div></section>
        <section><h2>Immersive media</h2><p>visionOS 26 uses public APMP and Apple Immersive Video playback APIs.</p><div className="support-grid">{projections.map(([name, level]) => <span className={`support-row ${level}`} key={name}><SupportIcon level={level} /><strong>{formatProjection(name)}</strong><small>{level.replace("runtime-check", "Check on device")}</small></span>)}</div></section>
        <section className="wide-panel"><h2>Decoded limits</h2><div className="metrics-strip"><span><small>Maximum frame</small><strong>{capabilities.maxWidth} × {capabilities.maxHeight}</strong></span><span><small>Frame rate</small><strong>Up to {capabilities.maxFrameRate} fps</strong></span><span><small>Native protected path</small><strong>{capabilities.secureNativePlayback ? "Available" : "Unavailable in browser"}</strong></span></div></section>
      </div></section>
    </main>
  );
}
