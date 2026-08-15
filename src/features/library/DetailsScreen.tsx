import { CheckCircle2, Download, Play, ShieldCheck, Subtitles, Volume2 } from "lucide-react";
import { useMemo, useState } from "react";
import { useApp } from "../../app/AppContext";
import { FormatBadges } from "../../components/FormatBadge";
import { MediaRail } from "../../components/MediaRail";
import { rankCandidates } from "../../domain/capabilities";
import { ArrRequestDialog } from "./ArrRequestDialog";

export function DetailsScreen() {
  const { selectedItem: item, catalog, capabilities, beginPlayback, kidsMode } = useApp();
  const [requestOpen, setRequestOpen] = useState(false);
  const routes = useMemo(() => rankCandidates(item?.candidates ?? [], capabilities), [capabilities, item]);
  const best = routes[0];
  const related = catalog.filter((candidate) => candidate.id !== item?.id).slice(0, 6);

  if (!item) {
    return <main className="screen details-screen"><section className="details-copy"><h1>No title selected</h1><p>Add content through Sources, then choose a title.</p></section></main>;
  }

  return (
    <main className="screen details-screen">
      <div className="details-backdrop" />
      <div className="details-edge-fade" />
      <section className="details-copy">
        <h1>{item.title}</h1>
        <div className="title-meta"><span>{item.year}</span><i>·</i><span>{item.rating}</span><i>·</i><span>{Math.floor(item.runtimeMinutes / 60)}h {String(item.runtimeMinutes % 60).padStart(2, "0")}m</span>{best ? <FormatBadges format={best.candidate.format} /> : null}</div>
        <p>{item.synopsis}</p>
        <div className="hero-actions">
          <button className="action-button primary" data-focusable data-autofocus autoFocus onClick={() => beginPlayback(item)}><Play size={22} fill="currentColor" />Stream</button>
          {kidsMode ? null : <button className="action-button secondary download-action" data-focusable onClick={() => setRequestOpen(true)}><Download size={22} />Download</button>}
        </div>
        <div className="media-facts">
          <span><Volume2 size={17} />English · Atmos <b>Spanish · 5.1</b></span>
          <span><Subtitles size={17} />English SDH <b>Spanish</b><b>French</b></span>
        </div>
        {best ? (
          <div className="best-route"><ShieldCheck size={19} /><span><strong>{best.candidate.sourceName}</strong><small>{best.candidate.directPlay ? "Direct play" : "Server transcode"} · {best.requiresProbe ? "Format check required on device" : "Format verified"}</small></span><CheckCircle2 size={19} /></div>
        ) : null}
      </section>
      <aside className="route-summary" aria-label="Available routes">
        <span className="side-label">Available routes</span>
        {routes.map((route, index) => (
          <button key={route.candidate.id} className={index === 0 ? "route-row preferred" : "route-row"} data-focusable onClick={() => beginPlayback(item)}>
            <span><strong>{route.candidate.sourceName}</strong><small>{route.candidate.directPlay ? "Direct" : "Transcode"} · {route.candidate.bitrateMbps} Mbps</small></span>
            <FormatBadges format={route.candidate.format} />
          </button>
        ))}
      </aside>
      <div className="details-related"><MediaRail title="Up Next" items={related} /></div>
      {requestOpen && !kidsMode ? <ArrRequestDialog item={item} onClose={() => setRequestOpen(false)} /> : null}
    </main>
  );
}
